package com.example.my_app

import com.example.my_app.ar.ArViewFactory
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val channelName = "ar_channel"
    private var methodChannel: MethodChannel? = null
    private var factory: ArViewFactory? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val messenger = flutterEngine.dartExecutor.binaryMessenger
        methodChannel = MethodChannel(messenger, channelName)
        
        val arFactory = ArViewFactory(messenger)
        this.factory = arFactory

        flutterEngine
            .platformViewsController
            .registry
            .registerViewFactory("ar_view", arFactory)

        methodChannel!!.setMethodCallHandler { call, result ->
            when (call.method) {
                "startAR" -> {
                    arFactory.startAR()
                    result.success(null)
                }
                "stopAR" -> {
                    arFactory.stopAR()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        factory?.startAR()
    }

    override fun onPause() {
        super.onPause()
        factory?.stopAR()
    }
}