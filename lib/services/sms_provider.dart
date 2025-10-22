import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:sendsms6/services/native_sms_service.dart';
import '../models/sms_item.dart';
import '../utils/shared_prefs.dart';

class SmsProvider extends ChangeNotifier {
  List<SmsItem> _items = [];
  bool _isSending = false;
  bool _isServerRunning = false;
  bool _isInitialized = false;

  StreamSubscription? _eventSubscription;

  List<SmsItem> get items => _items;
  bool get isSending => _isSending;
  bool get isServerRunning => _isServerRunning;
  bool get isInitialized => _isInitialized;

  SmsProvider() {
    debugPrint("[SmsProvider] Konstruktor pozvan. Inicijalizacija...");
    init();
  }

  Future<void> init() async {
    if (_isInitialized) return;
    await loadFromStorage(); // Učitaj staru listu pri startu
    _eventSubscription?.cancel();
    _eventSubscription = NativeSmsService.eventsStream.listen(_handleNativeEvent);
    NativeSmsService.queryServerStatus();
    _isInitialized = true;
    notifyListeners();
  }

  void _handleNativeEvent(Map<String, dynamic> event) {
    debugPrint("[SmsProvider] >>> Primljen nativni događaj: $event");
    final type = event['type'] as String?;
    switch (type) {
      case 'status':
        final index = event['index'] as int?;
        final status = event['status'] as String?;
        if (index != null && status != null) {
          updateStatus(index, status == 'sent' ? SmsStatus.sent : SmsStatus.failed);
        }
        break;
      case 'refresh_list':
        final jsonString = event['sms_data_json'] as String?;
        if (jsonString != null) {
          _updateListFromJson(jsonString);
        }
        break;
      case 'server_status_changed':
        final isRunning = event['isRunning'] as bool?;
        if (isRunning != null && _isServerRunning != isRunning) {
          _isServerRunning = isRunning;
          notifyListeners();
        }
        break;
    }
  }

  void _updateListFromJson(String jsonString) {
    try {
      final List<dynamic> jsonList = jsonDecode(jsonString);
      _items = jsonList.map((json) => SmsItem.fromJson(json)).toList();
      SharedPrefs.saveSmsList(_items); // Spremi novu listu kao backup
      notifyListeners();
    } catch (e) {
      debugPrint("[SmsProvider] Greška pri parsiranju JSON-a za refresh: $e");
    }
  }

  Future<void> loadFromStorage() async {
    _items = await SharedPrefs.loadSmsList();
    notifyListeners();
  }

  void updateStatus(int index, SmsStatus status) {
    if (index >= 0 && index < _items.length && _items[index].status != status) {
      _items[index].status = status;
      SharedPrefs.saveSmsList(_items);
      notifyListeners();
    }
  }

  void updateMessage(int index, String text) {
    if (index >= 0 && index < _items.length) {
      _items[index].message = text;
      SharedPrefs.saveSmsList(_items);
    }
  }

  void setSending(bool value) {
    if (_isSending != value) {
      _isSending = value;
      notifyListeners();
    }
  }

  void clearAll() {
    _items.clear();
    SharedPrefs.clear();
    notifyListeners();
  }

  void toggleServer() {
    _isServerRunning ? NativeSmsService.stopServer() : NativeSmsService.startServer();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }
}
