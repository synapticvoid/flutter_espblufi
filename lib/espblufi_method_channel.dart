import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'espblufi_platform_interface.dart';

/// An implementation of [EspblufiPlatform] that uses method channels.
class MethodChannelEspblufi extends EspblufiPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('espblufi');
  final scanResultsChannel = const EventChannel("espblufi/scanResults");
  final stateChannel = const EventChannel("espblufi/state");

  @override
  Stream<String> get scanResults =>
      scanResultsChannel.receiveBroadcastStream().distinct().map((event) => event as String);

  @override
  Stream<String> get state => stateChannel.receiveBroadcastStream().map((event) => event as String);

  @override
  Future<String?> getPlatformVersion() {
    return methodChannel.invokeMethod<String>('getPlatformVersion');
  }

  @override
  Future<void> startScan() {
    return methodChannel.invokeMethod('startScan');
  }

  @override
  Future<void> connect(String macAddress) {
    return methodChannel.invokeMethod("connect", {
      "macAddress": macAddress,
    });
  }

  @override
  Future<void> disconnect() {
    return methodChannel.invokeMethod('disconnect');
  }

  @override
  Future<String> requestDeviceVersion() async {
    return await methodChannel.invokeMethod<String>('requestDeviceVersion') ?? "";
  }

  @override
  Future<void> postCustomData(String data) {
    return methodChannel.invokeMethod('postCustomData', {
      "data": data,
    });
  }
}
