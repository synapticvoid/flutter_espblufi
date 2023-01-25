
import 'espblufi_platform_interface.dart';

class Espblufi {
  Future<String?> getPlatformVersion() {
    return EspblufiPlatform.instance.getPlatformVersion();
  }
}
