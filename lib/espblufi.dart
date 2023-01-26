import 'espblufi_platform_interface.dart';

class Espblufi {
  Future<String?> getPlatformVersion() => EspblufiPlatform.instance.getPlatformVersion();

  Future<void> startScan() => EspblufiPlatform.instance.startScan();

  Stream<String> scanResults() => EspblufiPlatform.instance.scanResults;

  Future<void> connect(String macAddress) => EspblufiPlatform.instance.connect(macAddress);

  Future<void> disconnect() => EspblufiPlatform.instance.disconnect();
}
