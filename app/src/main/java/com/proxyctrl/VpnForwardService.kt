package com.proxyctrl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VpnForwardService : VpnService() {

    companion object {
        private const val TAG = "VpnForwardService"
        private const val ACTION_START = "com.proxyctrl.START"
        private const val ACTION_STOP = "com.proxyctrl.STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_forward_channel"

        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ROUTE_MASK = 0
        private const val MTU = 1500

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, VpnForwardService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VpnForwardService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isStopping = false
    private var isStarted = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startVpn() {
        if (isStarted) {
            Log.d(TAG, "VPN already started")
            return
        }

        try {
            val builder = Builder()
                .setAddresses(VPN_ADDRESS, 24)
                .addRoute(VPN_ROUTE, VPN_ROUTE_MASK)
                .setMtu(MTU)
                .setSession("VPN Forward Proxy")
                .setConfigureIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("1.1.1.1")

            vpnInterface = builder.establish()
            isStarted = true
            isRunning = true

            startForeground(NOTIFICATION_ID, createNotification())
            startForwarding()

            Log.d(TAG, "VPN started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            isRunning = false
            isStarted = false
        }
    }

    private fun stopVpn() {
        if (!isStarted) return

        isStopping = true
        isRunning = false

        try {
            vpnInterface?.close()
            vpnInterface = null
            isStarted = false
            Log.d(TAG, "VPN stopped")
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
    }

    private fun startForwarding() {
        if (vpnInterface == null) return

        Thread {
            try {
                val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
                val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
                val buffer = ByteBuffer.allocate(MTU * 2)

                while (isRunning && !isStopping) {
                    buffer.clear()
                    val len = inputStream.channel.read(buffer)
                    if (len > 0) {
                        buffer.flip()
                        // 简单转发所有数据包
                        val data = ByteArray(buffer.remaining())
                        buffer.get(data)
                        outputStream.write(data)
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Forwarding error", e)
            }
        }.start()
    }

    private fun createNotification(): Notification {
        val channelName = "VPN Forward Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, VpnForwardService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN Forward Proxy")
            .setContentText("正在运行中...")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .build()
    }
}
