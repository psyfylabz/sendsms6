package com.example.sendsms6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val METHOD_CHANNEL = "sendsms6/methods"
    private val EVENT_CHANNEL = "sendsms6/events"
    private var eventSink: EventChannel.EventSink? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = mutableMapOf<String, Any>()
            intent.extras?.keySet()?.forEach { key ->
                event[key] = intent.extras?.get(key)!!
            }
            intent.action?.let { event["type"] = it.substring(it.lastIndexOf('.') + 1).lowercase() }
            sendEventToFlutter(event)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val filter = IntentFilter().apply {
            addAction(SmsSenderService.ACTION_EVENT)
            addAction(SmsSenderService.ACTION_REFRESH)
            addAction(SmsSenderService.ACTION_SERVER_STATUS_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
            }
            override fun onCancel(arguments: Any?) {
                eventSink = null
            }
        })

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            val action = when (call.method) {
                "startSending" -> SmsSenderService.ACTION_START_SENDING
                "cancelSending" -> SmsSenderService.ACTION_CANCEL_SENDING
                "startServer" -> SmsSenderService.ACTION_START_SERVER
                "stopServer" -> SmsSenderService.ACTION_STOP_SERVER
                "queryServerStatus" -> SmsSenderService.ACTION_QUERY_SERVER_STATUS
                else -> null
            }

            if (action != null) {
                controlSmsService(action)
                result.success(true)
            } else {
                result.notImplemented()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun controlSmsService(action: String) {
        val serviceIntent = Intent(this, SmsSenderService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun sendEventToFlutter(event: Map<String, Any>) {
        runOnUiThread { eventSink?.success(event) }
    }
}
