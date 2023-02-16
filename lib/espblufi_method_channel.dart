import 'package:espblufi/espblufi.dart';
import 'package:espblufi/src/mappers.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'espblufi_platform_interface.dart';

/// An implementation of [EspblufiPlatform] that uses method channels.
class MethodChannelEspblufi extends EspblufiPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('espblufi');
  final scanResultsChannel = const EventChannel("espblufi/scanResults");
  final stateChannel = const EventChannel("espblufi/events");

  @override
  Stream<BLEScanEvent> get scanResults =>
      scanResultsChannel.receiveBroadcastStream().distinct().map((event) {
        final map = Map<String, dynamic>.from(event);
        print("Received event. data=$map");
        return mapToBLEScanEvent(map);
        // return List<dynamic>.from(event)
        //   .map((e) => BLEDevice.fromMap(Map<String, dynamic>.from(e)))
        //   .toList();
      });

  @override
  Stream<BlufiEvent> get events => stateChannel.receiveBroadcastStream().distinct().map((event) {
        final map = Map<String, dynamic>.from(event);
        print("Received event. data=$map");
        return mapToBlufiEvent(map);
      });

  @override
  Future<void> startScan({String? filter, Duration timeout = defaultScanDuration}) {
    return methodChannel.invokeMethod('startScan', {
      "filter": filter,
      "timeout": timeout.inMilliseconds,
    });
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
  Future<void> postCustomData(Uint8List data) {
    return methodChannel.invokeMethod('postCustomData', {
      "data": data,
    });
  }

  @override
  Future<DeviceStatus> requestDeviceStatus() async {
    final map = Map<String, dynamic>.from(await methodChannel.invokeMethod("requestDeviceStatus"));
    return mapToDeviceStatus(map);
  }
}
