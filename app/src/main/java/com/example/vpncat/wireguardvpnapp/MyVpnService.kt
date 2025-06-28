// MyVpnService.kt
package com.example.wireguardvpnapp // Thay đổi package name của bạn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.vpncat.MainActivity
import com.example.vpncat.wireguardvpnapp.VpnConfig
import com.example.vpncat.wireguardvpnapp.VpnConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress

// IMPORTS TỪ THƯ VIỆN WIREGUARD THỰC TẾ
// Đảm bảo bạn đã thêm dependency và kiểm tra các import này
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.GoBackend // Sử dụng GoBackend làm ví dụ
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Key // Đảm bảo import này có
import com.wireguard.config.Peer
import com.wireguard.crypto.Keypair // Đảm bảo import này có
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MyVpnService is the core component that extends Android's VpnService.
 * It manages the VPN tunnel lifecycle, including starting, stopping, and configuring the tunnel.
 *
 * This implementation now integrates the actual WireGuard Android library.
 */
class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)

    // Instance của backend WireGuard
    private var wireguardBackend: GoBackend? = null
    private var activeTunnel: Tunnel? = null

    // Constants for Notification Channel
    private val NOTIFICATION_CHANNEL_ID = "vpn_service_channel"
    private val NOTIFICATION_CHANNEL_NAME = "VPN Service Notifications"
    private val NOTIFICATION_ID = 1

    companion object {
        private const val TAG = "MyVpnService"
        private const val VPN_MTU = 1420 // Maximum Transmission Unit for VPN tunnel

        // Action strings for Intents to control the service
        const val ACTION_CONNECT = "com.yourcompany.wireguardvpnapp.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.yourcompany.wireguardvpnapp.ACTION_DISCONNECT"
        const val EXTRA_VPN_CONFIG = "com.yourcompany.wireguardvpnapp.EXTRA_VPN_CONFIG"

        // Broadcast action for VPN status updates
        const val ACTION_VPN_STATUS_UPDATE = "com.yourcompany.wireguardvpnapp.ACTION_VPN_STATUS_UPDATE"
        const val EXTRA_VPN_STATUS = "com.yourcompany.wireguardvpnapp.EXTRA_VPN_STATUS"
        const val EXTRA_CONNECTED_VPN_NAME = "com.yourcompany.wireguardvpnapp.EXTRA_CONNECTED_VPN_NAME"

        // Helper function to start the VPN service
        fun startVpn(context: Context, config: VpnConfig) {
            val intent = Intent(context, MyVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_VPN_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // Helper function to stop the VPN service
        fun stopVpn(context: Context) {
            val intent = Intent(context, MyVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MyVpnService created")
        // Khởi tạo backend WireGuard
        try {
            wireguardBackend = GoBackend(this)
            Log.d(TAG, "WireGuard GoBackend initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WireGuard backend: ${e.message}", e)
            // Gửi lỗi về UI nếu backend không thể khởi tạo
            sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
        }
    }

    /**
     * Called when the service is started. Handles CONNECT and DISCONNECT actions.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createNotification("VPN Service Running"), ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN)
                } else {
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, createNotification("VPN Service Running"))
                }
                sendVpnStatusBroadcast(VpnConnectionStatus.Connecting, null)

                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_VPN_CONFIG, VpnConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_VPN_CONFIG)
                }
                if (config != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        startVpnTunnel(config)
                    }
                } else {
                    Log.e(TAG, "VpnConfig is null for CONNECT action")
                    sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
                    stopForeground(true)
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
            }
            else -> {
                Log.w(TAG, "Unknown action received: ${intent?.action}")
            }
        }
        return START_STICKY
    }

    /**
     * Establishes and configures the VPN tunnel.
     * This method should be called from a background thread (e.g., Coroutine).
     */
    private fun startVpnTunnel(config: VpnConfig) {
        if (wireguardBackend == null) {
            Log.e(TAG, "WireGuard backend is not initialized.")
            sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
            stopForeground(true)
            stopSelf()
            return
        }

        // Kiểm tra trạng thái tunnel thực tế từ backend
        if (activeTunnel?.state == Tunnel.State.UP) {
            Log.d(TAG, "VPN is already running.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification("VPN Connected: ${config.name}"), ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN)
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, createNotification("VPN Connected: ${config.name}"))
            }
            sendVpnStatusBroadcast(VpnConnectionStatus.Connected, config.name)
            return
        }

        Log.d(TAG, "Starting VPN tunnel with config: ${config.name}")

        val builder = Builder()
            .setMtu(VPN_MTU)
            // addAddress expects IP and prefix length separately
            .addAddress(InetAddress.getByName(config.address.split("/")[0]), config.address.split("/")[1].toInt())
            .setSession(config.name)

        for (dnsServer in config.dnsServers) {
            try {
                builder.addDnsServer(InetAddress.getByName(dnsServer))
            } catch (e: Exception) {
                Log.e(TAG, "Invalid DNS server address: $dnsServer", e)
            }
        }

        for (allowedIp in config.allowedIps) {
            try {
                val network = InetNetwork.parse(allowedIp)
                builder.addRoute(network.address, network.mask)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid AllowedIPs address: $allowedIp", e)
            }
        }

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface.")
                sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
                stopForeground(true)
                stopSelf()
                return
            }

            // --- TÍCH HỢP THƯ VIỆN WIREGUARD THỰC TẾ ---
            try {
                val privateKey = Key.fromBase64(config.privateKey)
                val peerPublicKey = Key.fromBase64(config.peerPublicKey)
                val presharedKey = config.presharedKey?.let { Key.fromBase64(it) }

                val peerBuilder = Peer.Builder()
                    .setPublicKey(peerPublicKey)
                    .setEndpoint(InetEndpoint.parse(config.endpoint))
                    .setAllowedIps(config.allowedIps.map { InetNetwork.parse(it) })
                    .setPersistentKeepalive(config.persistentKeepalive ?: 0)
                if (presharedKey != null) {
                    peerBuilder.setPresharedKey(presharedKey)
                }
                val peer = peerBuilder.build()

                val wgConfig = Config.Builder()
                    .setPrivateKey(privateKey)
                    .addPeer(peer)
                    // addAddresses expects a Collection of InetNetwork
                    .addAddresses(config.address.split(",").map { InetNetwork.parse(it) })
                    // addDnsServers expects a Collection of InetAddress
                    .addDnsServers(config.dnsServers.map { InetAddress.getByName(it) })
                    .build()

                // Tạo và khởi động tunnel
                activeTunnel = wireguardBackend?.create(
                    vpnInterface!!.fileDescriptor,
                    wgConfig,
                    object : Tunnel.OnStateChangeCallback {
                        override fun onStateChange(state: Tunnel.State) {
                            when (state) {
                                Tunnel.State.UP -> {
                                    isRunning.set(true)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        startForeground(NOTIFICATION_ID, createNotification("VPN Connected: ${config.name}"), ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        startForeground(NOTIFICATION_ID, createNotification("VPN Connected: ${config.name}"))
                                    }
                                    sendVpnStatusBroadcast(VpnConnectionStatus.Connected, config.name)
                                }
                                Tunnel.State.DOWN -> {
                                    isRunning.set(false)
                                    stopForeground(true)
                                    stopSelf()
                                    sendVpnStatusBroadcast(VpnConnectionStatus.Disconnected, null)
                                }
                                Tunnel.State.TOGGLE_ERROR -> {
                                    isRunning.set(false)
                                    stopForeground(true)
                                    stopSelf()
                                    sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
                                }
                            }
                        }
                    }
                )
                activeTunnel?.start()

            } catch (e: Exception) {
                Log.e(TAG, "Error configuring/starting WireGuard tunnel: ${e.message}", e)
                sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
                stopForeground(true)
                stopSelf()
                return
            }
            // --- KẾT THÚC TÍCH HỢP THƯ VIỆN WIREGUARD THỰC TẾ ---

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up VPN: ${e.message}", e)
            sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
            stopVpnTunnel()
        }
    }

    /**
     * Stops the VPN tunnel and cleans up resources.
     */
    private fun stopVpnTunnel() {
        Log.d(TAG, "Stopping VPN tunnel.")
        sendVpnStatusBroadcast(VpnConnectionStatus.Disconnecting, null)

        // --- DỪNG THƯ VIỆN WIREGUARD THỰC TẾ ---
        try {
            activeTunnel?.stop()
            activeTunnel = null
            Log.d(TAG, "WireGuard tunnel stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WireGuard tunnel: ${e.message}", e)
            sendVpnStatusBroadcast(VpnConnectionStatus.Error, null)
        }
        // --- KẾT THÚC DỪNG THƯ VIỆN WIREGUARD THỰC TẾ ---

        isRunning.set(false)
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN interface closed.")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        } finally {
            stopForeground(true)
            stopSelf()
            Log.d(TAG, "MyVpnService stopped.")
            sendVpnStatusBroadcast(VpnConnectionStatus.Disconnected, null)
        }
    }

    /**
     * Sends a broadcast with the current VPN connection status.
     * This allows the UI (ViewModel) to react to changes in the service.
     */
    private fun sendVpnStatusBroadcast(status: VpnConnectionStatus, vpnName: String?) {
        val intent = Intent(ACTION_VPN_STATUS_UPDATE).apply {
            putExtra(EXTRA_VPN_STATUS, status.name)
            putExtra(EXTRA_CONNECTED_VPN_NAME, vpnName)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Sent VPN status broadcast: $status, name: $vpnName")
    }

    /**
     * Creates a persistent notification for the foreground service.
     */
    private fun createNotification(message: String): Notification {
        createNotificationChannel()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WireGuard VPN")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Creates a notification channel for Android 8.0 (Oreo) and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MyVpnService destroyed")
        stopVpnTunnel()
    }
}
