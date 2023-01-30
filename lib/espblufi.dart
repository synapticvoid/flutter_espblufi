import 'espblufi_platform_interface.dart';

class BLEDevice {
  final String macAddress;
  final String name;

  BLEDevice(this.macAddress, this.name);

  factory BLEDevice.fromMap(Map<String, dynamic> data) => BLEDevice(
        data["macAddress"],
        data["name"],
      );
}

class Espblufi {
  Future<String?> getPlatformVersion() => EspblufiPlatform.instance.getPlatformVersion();

  Future<void> startScan() => EspblufiPlatform.instance.startScan();

  Stream<List<BLEDevice>> scanResults() => EspblufiPlatform.instance.scanResults;

  Stream<String> state() => EspblufiPlatform.instance.state;

  Future<void> connect(String macAddress) => EspblufiPlatform.instance.connect(macAddress);

  Future<void> postCustomData(String data) => EspblufiPlatform.instance.postCustomData(data);

  Future<void> disconnect() => EspblufiPlatform.instance.disconnect();

  Future<String> requestDeviceVersion() => EspblufiPlatform.instance.requestDeviceVersion();
}
