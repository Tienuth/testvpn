package com.example.vpncat.wireguardvpnapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MyVpnService : VpnService() {

    companion object {
        private const val TAG = "MyVpnService"
        private const val CHANNEL_ID = "vpn_service_channel"

        const val ACTION_VPN_STATUS_UPDATE = "com.example.vpncat.ACTION_VPN_STATUS_UPDATE"
        const val EXTRA_VPN_STATUS = "com.example.vpncat.EXTRA_VPN_STATUS"
        const val EXTRA_CONNECTED_VPN_NAME = "com.example.vpncat.EXTRA_CONNECTED_VPN_NAME"

        fun startVpn(context: Context, config: VpnConfig) {
            val intent = Intent(context, MyVpnService::class.java)
            intent.putExtra("VPN_CONFIG", config)
            context.startService(intent)
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, MyVpnService::class.java)
            context.stopService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vpnConfig: VpnConfig? = intent?.getParcelableExtra("VPN_CONFIG")

        if (vpnConfig == null) {
            Log.e(TAG, "No VPN configuration provided.")
            return START_NOT_STICKY
        }

        try {
            // Kiểm tra cấu hình VPN
            Log.d(TAG, "Attempting to connect to VPN at endpoint: ${vpnConfig.endpoint}")

            // Khởi tạo NotificationChannel (dành cho Android 8.0 trở lên)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "VPN Service",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }

            // Tạo thông báo cho dịch vụ foreground
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VPN is Running")
                .setContentText("VPN is connected to ${vpnConfig.name}")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()

            // Bắt đầu dịch vụ VPN trong chế độ foreground
            startForeground(1, notification)

            // Lấy địa chỉ IP (Private IP) từ cấu hình VPN
            val ipAddress = vpnConfig.address.split("/")[0]

            val vpnBuilder = Builder()
            vpnBuilder.setSession("VPNCat Session")
                .addAddress(ipAddress, 32)  // Sử dụng Private IP (10.7.0.2)
                .addRoute("0.0.0.0", 0) // Cho phép tất cả lưu lượng mạng đi qua VPN
                .addDnsServer(vpnConfig.dnsServers[0])
                .addDnsServer(vpnConfig.dnsServers[1])

            val vpnInterface = vpnBuilder.establish()

            // Kiểm tra kết nối VPN
            if (vpnInterface != null) {
                sendVpnStatusUpdate(VpnConnectionStatus.Connecting, vpnConfig.name)
                Log.d(TAG, "VPN started with address: ${vpnConfig.address}")
            } else {
                Log.e(TAG, "Failed to establish VPN connection")
                sendVpnStatusUpdate(VpnConnectionStatus.Error, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            sendVpnStatusUpdate(VpnConnectionStatus.Error, null)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VPN service stopped")
        sendVpnStatusUpdate(VpnConnectionStatus.Disconnected, null)
    }

    private fun sendVpnStatusUpdate(status: VpnConnectionStatus, vpnName: String?) {
        val intent = Intent(ACTION_VPN_STATUS_UPDATE)
        intent.putExtra(EXTRA_VPN_STATUS, status.name)
        intent.putExtra(EXTRA_CONNECTED_VPN_NAME, vpnName)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
