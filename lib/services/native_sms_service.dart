import 'dart:async';
import 'package:flutter/services.dart';

class NativeSmsService {
  static const MethodChannel _methodChannel = MethodChannel('sendsms6/methods');
  static const EventChannel _eventChannel = EventChannel('sendsms6/events');

  static Stream<Map<String, dynamic>>? _stream;

  static Stream<Map<String, dynamic>> get eventsStream {
    _stream ??= _eventChannel.receiveBroadcastStream().cast<Map<dynamic, dynamic>>().map((event) {
      return event.map((key, value) => MapEntry(key.toString(), value));
    });
    return _stream!;
  }

  static Future<void> startSending() async {
    try {
      await _methodChannel.invokeMethod('startSending');
    } catch (e) {
      print("Greška pri startSending: $e");
    }
  }

  static Future<void> cancelSending() async {
    try {
      await _methodChannel.invokeMethod('cancelSending');
    } catch (e) {
      print("Greška pri cancelSending: $e");
    }
  }

  static Future<void> startServer() async {
    try {
      await _methodChannel.invokeMethod('startServer');
    } catch (e) {
      print("Greška pri startServer: $e");
    }
  }

  static Future<void> stopServer() async {
    try {
      await _methodChannel.invokeMethod('stopServer');
    } catch (e) {
      print("Greška pri stopServer: $e");
    }
  }

  static Future<void> queryServerStatus() async {
    try {
      await _methodChannel.invokeMethod('queryServerStatus');
    } catch (e) {
      print("Greška pri queryServerStatus: $e");
    }
  }

}
