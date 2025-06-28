package com.example.vpncat.wireguardvpnapp

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the VPN application.
 * Manages the UI state related to VPN connection and interacts with MyVpnService.
 */
class VpnViewModel : ViewModel() {

    private val TAG = "VpnViewModel"

    // MutableStateFlow to hold the current VPN connection status
    private val _vpnStatus = MutableStateFlow(VpnConnectionStatus.Disconnected)
    val vpnStatus: StateFlow<VpnConnectionStatus> = _vpnStatus.asStateFlow()

    // MutableStateFlow to hold the name of the currently connected VPN server
    private val _connectedVpnName = MutableStateFlow<String?>(null)
    val connectedVpnName: StateFlow<String?> = _connectedVpnName.asStateFlow()

    // MutableStateFlow to hold the list of available VPN configurations
    private val _vpnConfigs = MutableStateFlow<List<VpnConfig>>(emptyList())
    val vpnConfigs: StateFlow<List<VpnConfig>> = _vpnConfigs.asStateFlow()

    // MutableStateFlow to hold the currently selected VPN configuration
    private val _selectedVpnConfig = MutableStateFlow<VpnConfig?>(null)
    val selectedVpnConfig: StateFlow<VpnConfig?> = _selectedVpnConfig.asStateFlow()

    // MutableStateFlow to indicate if a VPN permission request is pending
    private val _vpnPermissionRequired = MutableStateFlow(false)
    val vpnPermissionRequired: StateFlow<Boolean> = _vpnPermissionRequired.asStateFlow()

    init {
        // Initialize with the provided WireGuard configuration
        loadWireGuardConfig()
    }

    /**
     * Loads the provided WireGuard configuration.
     * In a real application, this would fetch configurations from persistent storage or a remote server.
     */
    private fun loadWireGuardConfig() {
        val wireGuardConfig = VpnConfig(
            id = "my_wireguard_server",
            name = "My WireGuard Server", // You can change this display name
            privateKey = "4GAt9Zoo4JoJ9oZtr9IbiwCqUIxPzAz5PGaTizGcHW0=",
            publicKey = "YOUR_CLIENT_PUBLIC_KEY_DERIVED_FROM_PRIVATE_KEY", // This should be derived or provided
            address = "10.7.0.2/24",
            dnsServers = listOf("94.140.14.14", "94.140.15.15"),
            peerPublicKey = "Rz2VCeTmh0PhK1XPRMq2Ow/sXFyM76LEaNz+JMxlPV4=",
            endpoint = "8.217.136.246:51820",
            allowedIps = listOf("0.0.0.0/0", "::/0"), // IPv4 and IPv6 all traffic
            persistentKeepalive = 25
        )
        _vpnConfigs.value = listOf(wireGuardConfig)
        _selectedVpnConfig.value = wireGuardConfig // Select this config by default
    }

    /**
     * Toggles the VPN connection status.
     * Starts VPN if disconnected, stops if connected.
     *
     * @param context The application context, needed to start the VpnService.
     */
    fun toggleVpnConnection(context: Context) {
        viewModelScope.launch {
            when (_vpnStatus.value) {
                VpnConnectionStatus.Disconnected, VpnConnectionStatus.Error -> {
                    // Check for VPN permission first
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent != null) {
                        // Permission not granted, request it
                        _vpnPermissionRequired.value = true
                        Log.d(TAG, "VPN permission required, launching intent.")
                    } else {
                        // Permission already granted, proceed to connect
                        connectVpn(context)
                    }
                }
                VpnConnectionStatus.Connected -> {
                    disconnectVpn(context)
                }
                else -> {
                    // Do nothing if connecting/disconnecting
                    Log.d(TAG, "VPN is already in a transition state: ${_vpnStatus.value}")
                }
            }
        }
    }

    /**
     * Initiates the VPN connection process.
     *
     * @param context The application context.
     */
    private fun connectVpn(context: Context) {
        val configToUse = _selectedVpnConfig.value
        if (configToUse == null) {
            Log.e(TAG, "No VPN configuration selected.")
            _vpnStatus.value = VpnConnectionStatus.Error
            return
        }

        // Status update will now come from MyVpnService via broadcast
        Log.d(TAG, "Attempting to connect VPN with config: ${configToUse.name}")

        // Start the MyVpnService to establish the tunnel
        MyVpnService.startVpn(context, configToUse)
    }

    /**
     * Initiates the VPN disconnection process.
     *
     * @param context The application context.
     */
    private fun disconnectVpn(context: Context) {
        // Status update will now come from MyVpnService via broadcast
        Log.d(TAG, "Attempting to disconnect VPN.")

        // Stop the MyVpnService
        MyVpnService.stopVpn(context)
    }

    /**
     * Call this after the user has granted VPN permission.
     *
     * @param context The application context.
     */
    fun onVpnPermissionGranted(context: Context) {
        _vpnPermissionRequired.value = false
        connectVpn(context) // Retry connection after permission granted
    }

    /**
     * Call this if the user denied VPN permission.
     */
    fun onVpnPermissionDenied() {
        _vpnPermissionRequired.value = false
        _vpnStatus.value = VpnConnectionStatus.Error // Set status to error
        Log.w(TAG, "VPN permission denied by user.")
    }

    /**
     * Sets the currently selected VPN configuration.
     *
     * @param config The VpnConfig to select.
     */
    fun selectVpnConfig(config: VpnConfig) {
        _selectedVpnConfig.value = config
        Log.d(TAG, "Selected VPN config: ${config.name}")
        // If VPN is connected, disconnect and reconnect with new config
        // This logic can be refined based on UX requirements
        // if (_vpnStatus.value == VpnConnectionStatus.Connected) {
        //    disconnectVpn(context) // Need context here, or handle in UI
        // }
    }

    /**
     * Placeholder for receiving actual status updates from MyVpnService.
     * In a real application, MyVpnService would send broadcasts or use other
     * mechanisms to update the ViewModel's status.
     *
     * @param status The new VPN connection status.
     * @param vpnName The name of the VPN if connected, or null.
     */
    fun updateVpnStatusFromService(status: VpnConnectionStatus, vpnName: String? = null) {
        _vpnStatus.value = status
        _connectedVpnName.value = vpnName
        Log.d(TAG, "Status updated from service: $status, Name: $vpnName")
    }
}