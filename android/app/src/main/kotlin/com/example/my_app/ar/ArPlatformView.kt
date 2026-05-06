package com.example.my_app.ar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArPlatformView(
    private val context: Context,
    private val channel: MethodChannel
) : PlatformView, GLSurfaceView.Renderer {

    companion object { private const val TAG = "ArPlatformView" }

    private val glView = GLSurfaceView(context)

    private var session: Session? = null
    private var installRequested = false

    private var viewportChanged = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    private var hasSetTextureNames = false

    private val backgroundRenderer = BackgroundRenderer()
    private val videoRenderer = VideoRenderer(context)

    init {
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(this)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    override fun getView(): View = glView

    override fun dispose() {
        pauseAR()
        videoRenderer.release()
    }

    // ✅ FIX: SAFELY GET ACTIVITY (NO CRASH)
    private fun getActivity(): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // 🔥 CALLED FROM FLUTTER
    fun startAR() {

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            emitStatus("Camera permission not granted")
            return
        }

        val activity = getActivity()
        if (activity == null) {
            emitStatus("Activity not found")
            return
        }

        try {
            if (session == null) {

                // ✅ FIX: PROPER ARCORE INSTALL FLOW
                when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        emitStatus("Installing ARCore...")
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }

                session = Session(activity)
                configure(session!!)
                emitStatus("Point camera at marker")
            }

            session?.resume()
            glView.onResume()

        } catch (e: Exception) {
            Log.e(TAG, "Start AR failed", e)
            emitStatus("AR Error: ${e.message}")
        }
    }

    fun pauseAR() {
        glView.onPause()
        session?.pause()
        videoRenderer.pause()
    }

    private fun configure(session: Session) {
        val config = Config(session)

        config.focusMode = Config.FocusMode.AUTO
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        val db = AugmentedImageDatabase(session)

        try {
            val stream = context.assets.open("flutter_assets/assets/images/marker.jpg")
            val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
            stream.close()

            db.addImage("marker", bitmap, 0.15f)
            Log.i(TAG, "Marker loaded")

        } catch (e: Exception) {
            Log.e(TAG, "Marker load failed", e)
            emitStatus("Marker missing!")
        }

        config.augmentedImageDatabase = db
        session.configure(config)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            backgroundRenderer.createOnGlThread(context)
            videoRenderer.createOnGlThread()
        } catch (e: Exception) {
            Log.e(TAG, "GL init error", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = this.session ?: return

        try {
            if (!hasSetTextureNames) {
                session.setCameraTextureName(backgroundRenderer.textureId)
                hasSetTextureNames = true
            }

            if (viewportChanged) {
                val activity = getActivity() ?: return
                val rotation = activity.windowManager.defaultDisplay.rotation
                session.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
                viewportChanged = false
            }

            val frame = session.update()
            val camera = frame.camera

            // ✅ THIS DRAWS CAMERA
            backgroundRenderer.draw(frame)

            if (camera.trackingState != TrackingState.TRACKING) return

            val projection = FloatArray(16)
            val view = FloatArray(16)

            camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
            camera.getViewMatrix(view, 0)

            val images = frame.getUpdatedTrackables(AugmentedImage::class.java)

            var found = false

            for (img in images) {
                if (img.trackingState == TrackingState.TRACKING) {
                    found = true

                    videoRenderer.ensurePlaying()

                    videoRenderer.draw(
                        projection,
                        view,
                        img.centerPose,
                        img.extentX,
                        img.extentZ
                    )

                    emitStatus("Tracking: ${img.name}")
                }
            }

            if (!found) {
                videoRenderer.pause()
                emitStatus("Searching marker...")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Frame error", e)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun emitStatus(msg: String) {
        handler.post { channel.invokeMethod("onStatus", msg) }
    }
}