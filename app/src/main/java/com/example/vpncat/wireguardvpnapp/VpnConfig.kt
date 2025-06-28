package com.example.vpncat.wireguardvpnapp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize // Import this for @Parcelize

/**
 * Data class representing a WireGuard VPN configuration.
 * This class holds all the necessary information to establish a WireGuard tunnel.
 *
 * @param id A unique identifier for this configuration (e.g., UUID).
 * @param name The display name for this VPN server (e.g., "Singapore Server", "Home VPN").
 * @param privateKey The private key of the client.
 * @param publicKey The public key of the client (derived from privateKey).
 * @param address The IP address(es) assigned to the VPN interface (e.g., "10.0.0.2/24").
 * @param dnsServers List of DNS server IP addresses (e.g., "8.8.8.8").
 * @param peerPublicKey The public key of the WireGuard server (peer).
 * @param endpoint The IP address and port of the WireGuard server (e.g., "vpn.example.com:51820").
 * @param allowedIps List of IP ranges to route through the VPN (e.g., "0.0.0.0/0" for all traffic).
 * @param persistentKeepalive The interval in seconds for sending keepalive packets (optional).
 */
@Parcelize // Add this annotation
data class VpnConfig(
    val id: String,
    val name: String,
    val privateKey: String,
    val publicKey: String, // Public key derived from privateKey
    val address: String, // e.g., "10.0.0.2/24"
    val dnsServers: List<String>, // e.g., listOf("8.8.8.8", "8.8.4.4")
    val peerPublicKey: String,
    val endpoint: String, // e.g., "vpn.example.com:51820"
    val allowedIps: List<String>, // e.g., listOf("0.0.0.0/0")
    val persistentKeepalive: Int? = null // Optional, in seconds
) : Parcelable // Implement Parcelable

/**
 * Enum to represent the current status of the VPN connection.
 */
enum class VpnConnectionStatus {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Error
}
