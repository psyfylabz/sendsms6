package com.example.sendsms6

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ActivityCompat
import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SmsSenderService : Service() {

    companion object {
        private const val TAG = "SmsSenderService"
        private const val CHANNEL_ID = "sendsms6_channel"
        private const val NOTIF_ID = 1001

        const val ACTION_START_SENDING = "com.example.sendsms6.START_SENDING"
        const val ACTION_CANCEL_SENDING = "com.example.sendsms6.CANCEL_SENDING"
        const val ACTION_START_SERVER = "com.example.sendsms6.START_SERVER"
        const val ACTION_STOP_SERVER = "com.example.sendsms6.STOP_SERVER"
        const val ACTION_QUERY_SERVER_STATUS = "com.example.sendsms6.QUERY_SERVER_STATUS"
        const val ACTION_EVENT = "com.example.sendsms6.SMS_EVENT"
        const val ACTION_REFRESH = "com.example.sendsms6.REFRESH_LIST"
        const val ACTION_SERVER_STATUS_CHANGED = "com.example.sendsms6.SERVER_STATUS_CHANGED"

        private var lastResultLatch: CountDownLatch? = null
        private var lastResultStatus: String? = null
        private val cancelled = AtomicBoolean(false)

        fun onSmsResult(index: Int, phone: String, status: String) {
            lastResultStatus = status
            lastResultLatch?.countDown()
        }

        fun requestCancel() {
            cancelled.set(true)
            lastResultLatch?.countDown()
        }
    }

    private var httpServer: AppHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val shutdownHandler = Handler(Looper.getMainLooper())
    private val shutdownRunnable = Runnable { stopHttpServer() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundNotif("Spremno")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHttpServer()
        releaseWakeLock()
        cancelled.set(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startHttpServer()
            ACTION_STOP_SERVER -> stopHttpServer()
            ACTION_QUERY_SERVER_STATUS -> sendServerStatusBroadcast(httpServer?.isAlive == true)
            ACTION_START_SENDING -> {
                acquireWakeLock()
                cancelled.set(false)
                val items = loadItemsFromPrefs()
                if (items.isEmpty()) {
                    releaseWakeLock()
                } else {
                    Thread { 
                        try { runLoop(items) } finally { releaseWakeLock() }
                    }.start()
                    startForegroundNotif("Slanje u toku…")
                }
            }
            ACTION_CANCEL_SENDING -> {
                requestCancel()
                releaseWakeLock()
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsSenderService::WakeLock").apply {
                acquire(30 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun scheduleServerShutdown() {
        cancelServerShutdown()
        shutdownHandler.postDelayed(shutdownRunnable, 60_000L)
    }

    private fun cancelServerShutdown() {
        shutdownHandler.removeCallbacks(shutdownRunnable)
    }

    private fun startHttpServer() {
        if (httpServer?.isAlive == true) {
            scheduleServerShutdown()
            return
        }
        httpServer = AppHttpServer(8080, this)
        try {
            httpServer!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            startForegroundNotif("Server aktivan na :8080")
            sendServerStatusBroadcast(true)
            scheduleServerShutdown()
        } catch (e: IOException) {
            httpServer = null
            sendServerStatusBroadcast(false)
        }
    }

    private fun stopHttpServer() {
        cancelServerShutdown()
        httpServer?.stop()
        httpServer = null
        sendServerStatusBroadcast(false)
        startForegroundNotif("Server zaustavljen")
    }

    private fun sendServerStatusBroadcast(isRunning: Boolean) {
        val intent = Intent(ACTION_SERVER_STATUS_CHANGED).apply {
            putExtra("isRunning", isRunning)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private inner class AppHttpServer(port: Int, private val svc: SmsSenderService) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            svc.scheduleServerShutdown()
            if (session.method != Method.POST || session.uri != "/") return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
            return try {
                val body = HashMap<String, String>()
                session.parseBody(body)
                val postData = body["postData"] ?: ""
                val json = JSONObject(postData)
                val arr: JSONArray = json.getJSONArray("data")
                if (arr.length() > 0) {
                    val jsonString = arr.toString()
                    // Upisujemo u SharedPreferences KAO BACKUP
                    prefs().edit().putString(prefsKey(), jsonString).apply()
                    // Ali Flutteru šaljemo podatke DIREKTNO
                    val refreshIntent = Intent(ACTION_REFRESH).apply {
                        putExtra("sms_data_json", jsonString)
                        setPackage(packageName)
                    }
                    svc.sendBroadcast(refreshIntent)
                }
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error")
            }
        }
    }

    private fun runLoop(items: List<SmsItem>) {
        val smsManager = resolveSmsManagerOrNull() ?: return
        var sentCount = 0
        items.forEachIndexed { index, item ->
            if (cancelled.get() || item.status == 1) return@forEachIndexed
            val ok = sendOneSmsBlocking(smsManager, index, item.phone, item.message)
            if (ok) sentCount++
            sendStatusEvent(index, if (ok) "sent" else "failed")
            if (cancelled.get()) return@forEachIndexed
            sleepQuiet(3000L)
            if (sentCount > 0 && sentCount % 50 == 0) sleepQuiet(60_000L)
        }
    }

    private fun sendOneSmsBlocking(smsManager: SmsManager, index: Int, phone: String, message: String): Boolean {
        return try {
            val parts = smsManager.divideMessage(message)
            val sentIntent = Intent(this, SmsBroadcastReceiver::class.java).apply {
                action = "com.example.sendsms6.SMS_SENT"
                putExtra("phone", phone)
                putExtra("index", index)
            }
            val sentPI: PendingIntent = PendingIntent.getBroadcast(this, 10_000 + index, sentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val sentIntents = ArrayList<PendingIntent>().apply { repeat(parts.size) { add(sentPI) } }
            lastResultStatus = null
            lastResultLatch = CountDownLatch(1)
            @Suppress("DEPRECATION")
            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            lastResultLatch?.await(25, TimeUnit.SECONDS)
            lastResultStatus == "sent"
        } catch (t: Throwable) { false }
    }

    private fun resolveSmsManagerOrNull(): SmsManager? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return null
        val subMgr = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val list: List<SubscriptionInfo> = try { subMgr.activeSubscriptionInfoList ?: emptyList() } catch (_: SecurityException) { emptyList() }
        return if (list.isNotEmpty()) SmsManager.getSmsManagerForSubscriptionId(list[0].subscriptionId) else SmsManager.getDefault()
    }

    private fun startForegroundNotif(text: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("SendSms6").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).setOnlyAlertOnce(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SendSms6 Channel", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sleepQuiet(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    private fun prefs() = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private fun prefsKey() = "flutter.sms_items"

    private fun loadItemsFromPrefs(): List<SmsItem> {
        val str = prefs().getString(prefsKey(), null)
        if (str == null) {
            return emptyList()
        }
        return try {
            val arr = JSONArray(str)
            val out = ArrayList<SmsItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(SmsItem(o.optString("phone", ""), o.optString("message", ""), o.optInt("status", 0)))
            }
            out
        } catch (e: Exception) { 
            emptyList() 
        }
    }

    private fun sendStatusEvent(index: Int, status: String) {
        val intent = Intent(ACTION_EVENT).apply {
            putExtra("index", index)
            putExtra("status", status)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    data class SmsItem(
        val phone: String,
        val message: String,
        val status: Int
    )
}
