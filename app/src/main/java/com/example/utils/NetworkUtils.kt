package com.example.utils

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Utilitários de rede para obter o endereço IP local Wi-Fi e hotspot.
 */
object NetworkUtils {

    /**
     * Obtém o endereço IPv4 da interface Wi-Fi activa ou do hotspot do dispositivo.
     */
    fun getLocalIpAddress(context: Context): String {
        // Tenta obter o IP através do WifiManager primeiro
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        }

        // Caso falhe ou esteja em modo Hotspot/P2P, percorre as interfaces de rede
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "0.0.0.0"
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "0.0.0.0"
    }

    /**
     * Valida se uma string é um IP IPv4 válido.
     */
    fun isValidIpAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } ?: false
        }
    }
}
