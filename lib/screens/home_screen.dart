import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/sms_item.dart';
import '../services/sms_provider.dart';
import '../services/native_sms_service.dart';
import '../widgets/sms_item_widget.dart';
import '../utils/permissions.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  Future<void> _handleRefresh(BuildContext context) async {
    context.read<SmsProvider>().loadFromStorage();
    NativeSmsService.queryServerStatus();
  }

  void _startSending(BuildContext context) async {
    if (!await SmsPermissions.hasPermissions()) {
      final granted = await SmsPermissions.requestAll();
      if (!context.mounted || !granted) return;
    }
    context.read<SmsProvider>().setSending(true);
    NativeSmsService.startSending();
  }

  void _cancelSending(BuildContext context) {
    context.read<SmsProvider>().setSending(false);
    NativeSmsService.cancelSending();
  }

  void _clearList(BuildContext context) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text("Potvrda brisanja"),
        content: const Text("Da li ste sigurni da želite obrisati celu listu?"),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text("Ne")),
          TextButton(onPressed: () => Navigator.of(ctx).pop(true), child: const Text("Da, obriši")),
        ],
      ),
    );
    if (confirm == true) {
      context.read<SmsProvider>().clearAll();
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<SmsProvider>();
    final items = provider.items;

    return Scaffold(
      appBar: AppBar(
        title: const Text("Send SMS 6"),
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(provider.isServerRunning ? Icons.stop_circle_outlined : Icons.play_circle_outline),
            color: provider.isServerRunning ? Colors.redAccent : Colors.greenAccent,
            iconSize: 30,
            tooltip: provider.isServerRunning ? "Zaustavi Server" : "Pokreni Server",
            onPressed: () => provider.toggleServer(),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: RefreshIndicator(
              onRefresh: () => _handleRefresh(context),
              child: items.isEmpty
                  ? Stack(
                      children: [
                        ListView(),
                        const Center(
                          child: Text(
                            "Lista je prazna\n(Povuci za osveženje)",
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ],
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.all(12),
                      itemCount: items.length,
                      itemBuilder: (context, index) {
                        return SmsItemWidget(item: items[index], index: index);
                      },
                    ),
            ),
          ),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(border: Border(top: BorderSide(color: Colors.grey.shade800))),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton.icon(
                  icon: const Icon(Icons.delete_sweep),
                  label: const Text("Clear"),
                  onPressed: items.isEmpty ? null : () => _clearList(context),
                  style: ElevatedButton.styleFrom(backgroundColor: Colors.red.shade800),
                ),
                provider.isSending
                    ? ElevatedButton.icon(
                        icon: const Icon(Icons.cancel),
                        label: const Text("Cancel"),
                        onPressed: () => _cancelSending(context),
                        style: ElevatedButton.styleFrom(backgroundColor: Colors.orange.shade800),
                      )
                    : ElevatedButton.icon(
                        icon: const Icon(Icons.send),
                        label: const Text("Send"),
                        onPressed: items.isEmpty ? null : () => _startSending(context),
                      ),
              ],
            ),
          )
        ],
      ),
    );
  }
}
