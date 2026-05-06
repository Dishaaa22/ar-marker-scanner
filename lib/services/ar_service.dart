import 'package:flutter/services.dart';

/// Thin wrapper around the native ARCore MethodChannel.
class ArService {
  static const MethodChannel _channel = MethodChannel('ar_channel');

  void Function(String status)? onStatusChanged;

  ArService() {
    _channel.setMethodCallHandler(_handle);
  }

  Future<void> _handle(MethodCall call) async {
    if (call.method == 'onStatus') {
      onStatusChanged?.call(call.arguments as String);
    }
  }

  Future<void> startAR() async {
    try {
      await _channel.invokeMethod('startAR');
    } on PlatformException catch (e) {
      onStatusChanged?.call('AR start failed: ${e.message}');
    }
  }

  Future<void> stopAR() async {
    try {
      await _channel.invokeMethod('stopAR');
    } on PlatformException catch (_) {}
  }
}