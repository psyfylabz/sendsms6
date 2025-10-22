import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/sms_item.dart';
import '../services/sms_provider.dart';

class SmsItemWidget extends StatefulWidget {
  final SmsItem item;
  final int index;

  const SmsItemWidget({super.key, required this.item, required this.index});

  @override
  State<SmsItemWidget> createState() => _SmsItemWidgetState();
}

class _SmsItemWidgetState extends State<SmsItemWidget> {
  bool _expanded = false;
  late TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.item.message);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Color _getBackgroundColor(SmsStatus status) {
    switch (status) {
      case SmsStatus.sent:
        return Colors.green.withOpacity(0.2);
      case SmsStatus.failed:
        return Colors.red.withOpacity(0.2);
      default:
        return Theme.of(context).colorScheme.surface;
    }
  }

  Color _getBorderColor(SmsStatus status) {
    switch (status) {
      case SmsStatus.sent:
        return Colors.green;
      case SmsStatus.failed:
        return Colors.red;
      default:
        return Colors.grey.shade600;
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<SmsProvider>();
    final item = widget.item;

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6),
      color: _getBackgroundColor(item.status),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: _getBorderColor(item.status), width: 1),
      ),
      child: InkWell(
        onTap: () {
          setState(() {
            _expanded = !_expanded;
          });
        },
        child: Column(
          children: [
            ListTile(
              title: Text(item.phone, style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: Text(
                item.message,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: Icon(_expanded ? Icons.expand_less : Icons.expand_more),
            ),
            if (_expanded)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                child: Column(
                  children: [
                    const Divider(),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _controller,
                      maxLines: 4,
                      decoration: const InputDecoration(
                        labelText: "Poruka",
                        border: OutlineInputBorder(),
                      ),
                      onChanged: (value) {
                        provider.updateMessage(widget.index, value);
                      },
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
