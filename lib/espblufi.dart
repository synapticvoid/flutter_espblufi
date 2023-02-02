library espblufi;

export 'src/models.dart';

import 'espblufi_platform_interface.dart';
import 'src/models.dart';

const Duration defaultScanDuration = Duration(seconds: 5);

class Espblufi {
  Stream<BLEScanEvent> scanResults() => EspblufiPlatform.instance.scanResults;

  Stream<BlufiEvent> events() => EspblufiPlatform.instance.events;

  Future<void> startScan({String? filter, Duration timeout = defaultScanDuration}) =>
      EspblufiPlatform.instance.startScan(filter: filter, timeout: timeout);

  Future<void> connect(String macAddress) => EspblufiPlatform.instance.connect(macAddress);

  Future<void> postCustomData(String data) => EspblufiPlatform.instance.postCustomData(data);

  Future<void> disconnect() => EspblufiPlatform.instance.disconnect();

  Future<String> requestDeviceVersion() => EspblufiPlatform.instance.requestDeviceVersion();
}
