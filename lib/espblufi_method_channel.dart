import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'espblufi_platform_interface.dart';

/// An implementation of [EspblufiPlatform] that uses method channels.
class MethodChannelEspblufi extends EspblufiPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('espblufi');
  final eventChannel = const EventChannel("espblufi/state");

  @override
  Stream<String> get scanResults =>
      eventChannel.receiveBroadcastStream().distinct().map((event) => event as String);

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<void> startScan() async {
    await methodChannel.invokeMethod('startScan');
  }

  @override
  Future<void> connect(String macAddress) async {
    await methodChannel.invokeMethod("connect", {
      "macAddress": macAddress,
    });
  }

  @override
  Future<void> disconnect() async {
    await methodChannel.invokeMethod('disconnect');
  }
}
