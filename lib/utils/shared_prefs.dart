import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/sms_item.dart';

class SharedPrefs {
  static const String _smsListKey = 'flutter.sms_items';

  static Future<void> saveSmsList(List<SmsItem> items) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final List<Map<String, dynamic>> itemList = items.map((item) => item.toJson()).toList();
      final String jsonString = jsonEncode(itemList);
      debugPrint("[SharedPrefs] Spremanje JSON-a: $jsonString");
      await prefs.setString(_smsListKey, jsonString);
      debugPrint("[SharedPrefs] Uspješno spremljeno.");
    } catch (e) {
      debugPrint("[SharedPrefs] Greška pri spremanju: $e");
    }
  }

  static Future<List<SmsItem>> loadSmsList() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.reload();
      final String? jsonString = prefs.getString(_smsListKey);
      debugPrint("[SharedPrefs] Pročitan JSON: $jsonString");
      if (jsonString == null || jsonString.isEmpty) {
        return [];
      }
      final List<dynamic> jsonList = jsonDecode(jsonString);
      final List<SmsItem> items = jsonList.map((jsonItem) => SmsItem.fromJson(jsonItem)).toList();
      debugPrint("[SharedPrefs] Uspješno parsirano ${items.length} stavki.");
      return items;
    } catch (e) {
      debugPrint("[SharedPrefs] Greška pri učitavanju: $e");
      return [];
    }
  }

  static Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_smsListKey);
    debugPrint("[SharedPrefs] Lista obrisana.");
  }
}
