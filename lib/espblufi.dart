library espblufi;

import 'dart:typed_data';

import 'espblufi_platform_interface.dart';
import 'src/models.dart';

export 'src/models.dart';

const Duration defaultScanDuration = Duration(seconds: 5);

class Espblufi {
  Stream<BLEScanEvent> scanResults() => EspblufiPlatform.instance.scanResults;

  Stream<BlufiEvent> events() => EspblufiPlatform.instance.events;

  Future<void> startScan({String? filter, Duration timeout = defaultScanDuration}) =>
      EspblufiPlatform.instance.startScan(filter: filter, timeout: timeout);

  Future<void> connect(String macAddress) => EspblufiPlatform.instance.connect(macAddress);

  Future<void> postCustomData(Uint8List data) => EspblufiPlatform.instance.postCustomData(data);

  Future<void> disconnect() => EspblufiPlatform.instance.disconnect();

  Future<String> requestDeviceVersion() => EspblufiPlatform.instance.requestDeviceVersion();

  Future<DeviceStatus> requestDeviceStatus() => EspblufiPlatform.instance.requestDeviceStatus();
}
