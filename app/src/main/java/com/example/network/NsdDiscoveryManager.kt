package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

data class DiscoveredChannel(
    val name: String,
    val ip: String,
    val port: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Gestor de Descoberta de Serviços na Rede Local via NsdManager (mDNS/DNS-SD).
 * Permite que Receptores em Modo Público anunciem a sua presença e que Emissores
 * descubram canais de transmissão disponíveis sem introduzir IPs manualmente.
 */
class NsdDiscoveryManager(context: Context) {

    private val applicationContext = context.applicationContext
    private val nsdManager = applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredChannels = MutableStateFlow<List<DiscoveredChannel>>(emptyList())
    val discoveredChannels: StateFlow<List<DiscoveredChannel>> = _discoveredChannels.asStateFlow()

    private val _isAnnouncing = MutableStateFlow(false)
    val isAnnouncing: StateFlow<Boolean> = _isAnnouncing.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    companion object {
        const val SERVICE_TYPE = "_audiobridge._udp."
        private const val TAG = "NsdDiscoveryManager"
    }

    /**
     * Anuncia o Receptor na rede Wi-Fi local (Modo Público).
     */
    fun startAnnouncement(channelName: String, port: Int) {
        stopAnnouncement()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = channelName.ifBlank { "Canal AudioBridge" }
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Serviço anunciado com sucesso: ${NsdServiceInfo.serviceName}")
                _isAnnouncing.value = true
            }

            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Log.e(TAG, "Falha ao anunciar serviço NSD: $arg1")
                _isAnnouncing.value = false
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Anúncio de serviço removido.")
                _isAnnouncing.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Falha ao remover anúncio NSD: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            e.printStackTrace()
            _isAnnouncing.value = false
        }
    }

    /**
     * Pára o anúncio do IP na rede local (Modo Privado ou Desativado).
     */
    fun stopAnnouncement() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        registrationListener = null
        _isAnnouncing.value = false
    }

    /**
     * Procura por canais de receptores públicos disponíveis na rede Wi-Fi local.
     */
    fun startDiscovery() {
        stopDiscovery()
        _discoveredChannels.value = emptyList()

        acquireMulticastLock()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Procura de canais NSD iniciada.")
                _isDiscovering.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Canal encontrado: ${service.serviceName}")
                if (service.serviceType.contains("audiobridge")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Canal perdido: ${service.serviceName}")
                removeChannelByName(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Procura de canais NSD parada.")
                _isDiscovering.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Falha ao iniciar procura NSD: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Falha ao parar procura NSD: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
            _isDiscovering.value = false
        }
    }

    private fun resolveService(service: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Falha ao resolver canal: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host: InetAddress? = serviceInfo.host
                val port = serviceInfo.port
                val name = serviceInfo.serviceName

                if (host != null) {
                    val ip = host.hostAddress ?: ""
                    if (ip.isNotBlank() && !ip.startsWith("127.")) {
                        addOrUpdateChannel(DiscoveredChannel(name = name, ip = ip, port = port))
                    }
                }
            }
        }

        try {
            nsdManager.resolveService(service, resolveListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun addOrUpdateChannel(channel: DiscoveredChannel) {
        val current = _discoveredChannels.value.toMutableList()
        current.removeAll { it.name == channel.name || it.ip == channel.ip }
        current.add(channel)
        _discoveredChannels.value = current
    }

    @Synchronized
    private fun removeChannelByName(name: String) {
        val current = _discoveredChannels.value.toMutableList()
        current.removeAll { it.name == name }
        _discoveredChannels.value = current
    }

    /**
     * Pára a procura por canais de rede.
     */
    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        discoveryListener = null
        _isDiscovering.value = false
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("AudioBridge::MulticastLock").apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.acquire()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
