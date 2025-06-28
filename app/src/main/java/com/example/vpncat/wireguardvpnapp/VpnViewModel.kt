package com.example.vpncat.wireguardvpnapp

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager

enum class VpnConnectionStatus {
    Disconnected, Connecting, Connected, Disconnecting, Error
}

class VpnViewModel : ViewModel() {
    private val TAG = "VpnViewModel"

    // Trạng thái kết nối VPN
    private val _vpnStatus = MutableStateFlow(VpnConnectionStatus.Disconnected)
    val vpnStatus: StateFlow<VpnConnectionStatus> = _vpnStatus

    // Tên VPN kết nối
    private val _connectedVpnName = MutableStateFlow<String?>(null)
    val connectedVpnName: StateFlow<String?> = _connectedVpnName

    // Danh sách cấu hình VPN
    private val _vpnConfigs = MutableStateFlow<List<VpnConfig>>(emptyList())
    val vpnConfigs: StateFlow<List<VpnConfig>> = _vpnConfigs

    // Cấu hình VPN đã chọn
    private val _selectedVpnConfig = MutableStateFlow<VpnConfig?>(null)
    val selectedVpnConfig: StateFlow<VpnConfig?> = _selectedVpnConfig

    // Kiểm tra yêu cầu quyền VPN
    private val _vpnPermissionRequired = MutableStateFlow(false)
    val vpnPermissionRequired: StateFlow<Boolean> = _vpnPermissionRequired

    init {
        loadWireGuardConfig() // Nạp cấu hình WireGuard
    }

    private fun loadWireGuardConfig() {
        val wireGuardConfig1 = VpnConfig(
            id = "my_wireguard_server_1",
            name = "China",
            privateKey = "4GAt9Zoo4JoJ9oZtr9IbiwCqUIxPzAz5PGaTizGcHW0=",
            publicKey = "YOUR_CLIENT_PUBLIC_KEY_DERIVED_FROM_PRIVATE_KEY",
            address = "10.7.0.2/24",
            dnsServers = listOf("94.140.14.14", "94.140.15.15"),
            peerPublicKey = "Rz2VCeTmh0PhK1XPRMq2Ow/sXFyM76LEaNz+JMxlPV4=",
            presharedKey = "3dSQi6ONEufLcIRkliZz3VJTqf+4Je2U3hTuZoHeQ8M=",
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            endpoint = "8.217.136.246:51820",
            persistentKeepalive = 25
        )

        val wireGuardConfig2 = VpnConfig(
            id = "test",
            name = "Singapor",
            privateKey = "3GAt9Zoo4JoJ9oZtr9IbiwCqUIxPzAz5PGaTizGcHW1=", // Thay đổi với Private Key của server 2
            publicKey = "YOUR_CLIENT_PUBLIC_KEY_DERIVED_FROM_PRIVATE_KEY",
            address = "10.7.0.3/24", // Thay đổi địa chỉ IP của client cho server 2
            dnsServers = listOf("94.140.14.14", "94.140.15.15"),
            peerPublicKey = "Rz2VCeTmh0PhK1XPRMq2Ow/sXFyM76LEaNz+JMxlPV5=", // Thay đổi Public Key của server 2
            presharedKey = "4dSQi6ONEufLcIRkliZz3VJTqf+4Je2U3hTuZoHeQ9M=", // Thay đổi Preshared Key của server 2
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            endpoint = "203.0.113.10:51820", // Địa chỉ IP và Port của server 2
            persistentKeepalive = 25
        )

        _vpnConfigs.value = listOf(wireGuardConfig1, wireGuardConfig2)
        _selectedVpnConfig.value = wireGuardConfig1 // Đặt cấu hình mặc định là Server 1
    }

    fun toggleVpnConnection(context: Context) {
        viewModelScope.launch {
            when (_vpnStatus.value) {
                VpnConnectionStatus.Disconnected, VpnConnectionStatus.Error -> {
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent != null) {
                        _vpnPermissionRequired.value = true
                    } else {
                        connectVpn(context)
                    }
                }
                VpnConnectionStatus.Connected -> {
                    disconnectVpn(context)
                }
                else -> {
                    Log.d(TAG, "VPN is already in a transition state: ${_vpnStatus.value}")
                }
            }
        }
    }

    private fun connectVpn(context: Context) {
        val configToUse = _selectedVpnConfig.value
        if (configToUse == null) {
            Log.e(TAG, "No VPN configuration selected.")
            _vpnStatus.value = VpnConnectionStatus.Error
            return
        }

        Log.d(TAG, "Attempting to connect VPN with config: ${configToUse.name}")
        MyVpnService.startVpn(context, configToUse)
    }

    private fun disconnectVpn(context: Context) {
        Log.d(TAG, "Attempting to disconnect VPN.")
        MyVpnService.stopVpn(context)
    }

    fun onVpnPermissionGranted(context: Context) {
        _vpnPermissionRequired.value = false
        connectVpn(context)
    }

    fun onVpnPermissionDenied() {
        _vpnPermissionRequired.value = false
        _vpnStatus.value = VpnConnectionStatus.Error
        Log.w(TAG, "VPN permission denied by user.")
    }

    fun updateVpnStatusFromService(status: VpnConnectionStatus, vpnName: String? = null) {
        _vpnStatus.value = status
        _connectedVpnName.value = vpnName
        Log.d(TAG, "Status updated from service: $status, Name: $vpnName")
    }

    // Phương thức để chọn cấu hình VPN
    fun selectVpnConfig(config: VpnConfig) {
        _selectedVpnConfig.value = config
    }
}