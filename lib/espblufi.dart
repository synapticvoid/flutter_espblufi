import 'espblufi_platform_interface.dart';

class Espblufi {
  Future<String?> getPlatformVersion() => EspblufiPlatform.instance.getPlatformVersion();

  Future<void> startScan() => EspblufiPlatform.instance.startScan();

  Stream<String> scanResults() => EspblufiPlatform.instance.scanResults;

  Stream<String> state() => EspblufiPlatform.instance.state;

  Future<void> connect(String macAddress) => EspblufiPlatform.instance.connect(macAddress);

  Future<void> postCustomData(String data) =>
      EspblufiPlatform.instance.postCustomData(data);

  Future<void> disconnect() => EspblufiPlatform.instance.disconnect();

  Future<String> requestDeviceVersion() => EspblufiPlatform.instance.requestDeviceVersion();
}
