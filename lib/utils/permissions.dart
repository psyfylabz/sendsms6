import 'package:permission_handler/permission_handler.dart';

class SmsPermissions {
  static Future<bool> requestAll() async {
    // SMS & telefon permissions
    final sms = await Permission.sms.request();
    final phone = await Permission.phone.request();

    if (sms.isGranted && phone.isGranted) {
      return true;
    }
    return false;
  }

  static Future<bool> hasPermissions() async {
    return await Permission.sms.isGranted &&
           await Permission.phone.isGranted;
  }
}
