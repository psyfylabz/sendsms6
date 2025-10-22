package com.example.sendsms6

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra("phone") ?: ""
        val index = intent.getIntExtra("index", -1)

        val status = when (resultCode) {
            Activity.RESULT_OK -> "sent"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "failed"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "no_service"
            SmsManager.RESULT_ERROR_NULL_PDU -> "null_pdu"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "radio_off"
            else -> "unknown_error"
        }

        Log.i(TAG, "Received SMS status for index $index, phone $phone: $status (Code: $resultCode)")

        if (index != -1) {
            // Prosledi rezultat nazad servisu
            SmsSenderService.onSmsResult(index, phone, status)
        }
    }
}
