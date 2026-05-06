package com.example.my_app.ar

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.flutter.plugin.common.StandardMessageCodec

class ArViewFactory(
    private val messenger: BinaryMessenger
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    private var arView: ArPlatformView? = null

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {

        val channel = MethodChannel(messenger, "ar_view_$viewId")

        val view = ArPlatformView(context, channel)
        arView = view

        channel.setMethodCallHandler { call, result ->
            when (call.method) {

                "startAR" -> {
                    view.startAR()
                    result.success(null)
                }

                "pauseAR" -> {
                    view.pauseAR()
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }

        return view
    }

    fun startAR() {
        arView?.startAR()
    }

    fun stopAR() {
        arView?.pauseAR()
    }
}