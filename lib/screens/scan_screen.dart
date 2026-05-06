import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/rendering.dart';
import '../services/ar_service.dart';

class ScanScreen extends StatefulWidget {
  const ScanScreen({super.key});

  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen> {
  final ArService _arService = ArService();
  String _status = 'Initializing AR...';

  @override
  void initState() {
    super.initState();

    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

    _arService.onStatusChanged = (s) {
      if (mounted) setState(() => _status = s);
    };
  }

  @override
  void dispose() {
    _arService.stopAR();
    SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [

          /// FORCE FULL SCREEN SIZE
          Positioned.fill(
            child: PlatformViewLink(
              viewType: 'ar_view',

              surfaceFactory: (context, controller) {
                return AndroidViewSurface(
                  controller: controller as AndroidViewController,
                  gestureRecognizers:
                  const <Factory<OneSequenceGestureRecognizer>>{},
                  hitTestBehavior: PlatformViewHitTestBehavior.opaque,
                );
              },

              onCreatePlatformView: (params) {
                final controller =
                PlatformViewsService.initSurfaceAndroidView(
                  id: params.id,
                  viewType: 'ar_view',
                  layoutDirection: TextDirection.ltr,
                  creationParams: {},
                  creationParamsCodec: const StandardMessageCodec(),
                );

                controller
                  ..addOnPlatformViewCreatedListener((id) {
                    params.onPlatformViewCreated(id);

                    ///  START AR ONLY AFTER VIEW IS READY
                    _arService.startAR();
                  })
                  ..create();

                return controller;
              },
            ),
          ),

          /// 🔹 TOP BAR
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    _circleBtn(
                      Icons.arrow_back,
                          () => Navigator.of(context).pop(),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 10),
                        decoration: BoxDecoration(
                          color: Colors.black.withOpacity(0.55),
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: Text(
                          _status,
                          style: const TextStyle(
                              color: Colors.white, fontSize: 14),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),

          /// 🔹 SCAN BOX
          Center(
            child: IgnorePointer(
              child: Container(
                width: 260,
                height: 260,
                decoration: BoxDecoration(
                  border: Border.all(
                      color: Colors.white.withOpacity(0.35), width: 2),
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _circleBtn(IconData icon, VoidCallback onTap) {
    return Material(
      color: Colors.black.withOpacity(0.55),
      shape: const CircleBorder(),
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Icon(icon, color: Colors.white),
        ),
      ),
    );
  }
}