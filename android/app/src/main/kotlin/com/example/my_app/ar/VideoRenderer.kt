package com.example.my_app.ar

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class VideoRenderer(private val context: Context) {

    companion object { private const val TAG = "VideoRenderer" }

    private var program = 0
    private var posAttrib = 0
    private var texAttrib = 0
    private var mvpUniform = 0
    private var stMatrixUniform = 0
    private var texUniform = 0

    private var videoTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var frameAvailable = false
    private val stMatrix = FloatArray(16)

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private val QUAD_VERTICES = floatArrayOf(
        -0.5f, 0f, -0.5f,
        +0.5f, 0f, -0.5f,
        -0.5f, 0f, +0.5f,
        +0.5f, 0f, +0.5f
    )
    private val QUAD_TEXCOORDS = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )
    private val QUAD_INDICES = shortArrayOf(0, 1, 2, 1, 3, 2)

    fun createOnGlThread() {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        videoTextureId = tex[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, videoTextureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(videoTextureId).apply {
            setOnFrameAvailableListener { frameAvailable = true }
        }

        val vb = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4).order(ByteOrder.nativeOrder())
        vertexBuffer = vb.asFloatBuffer().apply { put(QUAD_VERTICES); position(0) }
        val tb = ByteBuffer.allocateDirect(QUAD_TEXCOORDS.size * 4).order(ByteOrder.nativeOrder())
        texBuffer = tb.asFloatBuffer().apply { put(QUAD_TEXCOORDS); position(0) }
        val ib = ByteBuffer.allocateDirect(QUAD_INDICES.size * 2).order(ByteOrder.nativeOrder())
        indexBuffer = ib.asShortBuffer().apply { put(QUAD_INDICES); position(0) }

        val vs = """
            uniform mat4 u_MVP;
            uniform mat4 u_StMatrix;
            attribute vec4 a_Position;
            attribute vec4 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
              gl_Position = u_MVP * a_Position;
              v_TexCoord = (u_StMatrix * a_TexCoord).xy;
            }
        """.trimIndent()
        val fs = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, v_TexCoord); }
        """.trimIndent()
        program = ShaderUtil.buildProgram(vs, fs)
        posAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_MVP")
        stMatrixUniform = GLES20.glGetUniformLocation(program, "u_StMatrix")
        texUniform = GLES20.glGetUniformLocation(program, "sTexture")

        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        try {
            val afd = context.assets.openFd("flutter_assets/assets/videos/sample.mp4")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setSurface(Surface(surfaceTexture))
                setOnPreparedListener {
                    prepared = true
                    Log.i(TAG, "Video prepared")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra"); true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sample.mp4 from assets", e)
        }
    }

    fun ensurePlaying() {
        val mp = mediaPlayer ?: return
        if (prepared && !mp.isPlaying) {
            try { mp.start() } catch (e: Exception) { Log.e(TAG, "start failed", e) }
        }
    }

    fun pause() {
        val mp = mediaPlayer ?: return
        if (prepared && mp.isPlaying) {
            try { mp.pause() } catch (_: Exception) {}
        }
    }

    fun release() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        try { surfaceTexture?.release() } catch (_: Exception) {}
        surfaceTexture = null
    }

    fun draw(projection: FloatArray, viewM: FloatArray, pose: Pose, extentX: Float, extentZ: Float) {
        if (!prepared) return

        if (frameAvailable) {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(stMatrix)
            frameAvailable = false
        } else {
            Matrix.setIdentityM(stMatrix, 0)
        }

        val model = FloatArray(16)
        pose.toMatrix(model, 0)
        val scaled = FloatArray(16)
        Matrix.setIdentityM(scaled, 0)
        Matrix.scaleM(scaled, 0, extentX, 1f, extentZ)
        val worldModel = FloatArray(16)
        Matrix.multiplyMM(worldModel, 0, model, 0, scaled, 0)

        val mv = FloatArray(16)
        Matrix.multiplyMM(mv, 0, viewM, 0, worldModel, 0)
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

        GLES20.glUseProgram(program)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glUniform1i(texUniform, 0)

        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(stMatrixUniform, 1, false, stMatrix, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posAttrib, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(posAttrib)

        texBuffer.position(0)
        GLES20.glVertexAttribPointer(texAttrib, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glEnableVertexAttribArray(texAttrib)

        indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, QUAD_INDICES.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(posAttrib)
        GLES20.glDisableVertexAttribArray(texAttrib)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
    }
}
