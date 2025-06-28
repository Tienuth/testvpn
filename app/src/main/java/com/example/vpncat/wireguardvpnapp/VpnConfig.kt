package com.example.vpncat.wireguardvpnapp

import android.os.Parcel
import android.os.Parcelable

data class VpnConfig(
    val id: String,
    val name: String,
    val privateKey: String,
    val publicKey: String,
    val address: String, // Địa chỉ IP private của thiết bị khi kết nối
    val dnsServers: List<String>,
    val peerPublicKey: String,
    val presharedKey: String,
    val allowedIps: List<String>, // Các địa chỉ IP được phép đi qua VPN
    val endpoint: String, // Địa chỉ Public IP của máy chủ VPN
    val persistentKeepalive: Int
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        name = parcel.readString() ?: "",
        privateKey = parcel.readString() ?: "",
        publicKey = parcel.readString() ?: "",
        address = parcel.readString() ?: "",
        dnsServers = parcel.createStringArrayList() ?: emptyList(),
        peerPublicKey = parcel.readString() ?: "",
        presharedKey = parcel.readString() ?: "",
        allowedIps = parcel.createStringArrayList() ?: emptyList(),
        endpoint = parcel.readString() ?: "",
        persistentKeepalive = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(privateKey)
        parcel.writeString(publicKey)
        parcel.writeString(address)
        parcel.writeStringList(dnsServers)
        parcel.writeString(peerPublicKey)
        parcel.writeString(presharedKey)
        parcel.writeStringList(allowedIps)
        parcel.writeString(endpoint)
        parcel.writeInt(persistentKeepalive)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<VpnConfig> {
        override fun createFromParcel(parcel: Parcel): VpnConfig {
            return VpnConfig(parcel)
        }

        override fun newArray(size: Int): Array<VpnConfig?> {
            return arrayOfNulls(size)
        }
    }
}
