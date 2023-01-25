
import 'espblufi_platform_interface.dart';

class Espblufi {
  Future<String?> getPlatformVersion() {
    return EspblufiPlatform.instance.getPlatformVersion();
  }

  Future<void> startScan() {
    return EspblufiPlatform.instance.startScan();
  }
}
