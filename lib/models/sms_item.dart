// lib/models/sms_item.dart
enum SmsStatus { pending, sent, failed }

class SmsItem {
  String phone;
  String message;
  SmsStatus status;

  SmsItem({
    required this.phone,
    required this.message,
    this.status = SmsStatus.pending,
  });

  factory SmsItem.fromJson(Map<String, dynamic> json) {
    return SmsItem(
      phone: json['phone'] ?? '',
      message: json['message'] ?? '',
      status: SmsStatus.values[json['status'] ?? 0],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'phone': phone,
      'message': message,
      'status': status.index,
    };
  }
}
