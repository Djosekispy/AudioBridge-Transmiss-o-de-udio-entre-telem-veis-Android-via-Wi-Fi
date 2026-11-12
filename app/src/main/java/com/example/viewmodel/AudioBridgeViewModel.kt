package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioConstants
import com.example.network.DiscoveredChannel
import com.example.network.NsdDiscoveryManager
import com.example.service.AudioReceiverService
import com.example.service.AudioSenderService
import com.example.service.AudioSourceType
import com.example.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppMode {
    SENDER, RECEIVER, CHANNELS
}

class AudioBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("audio_bridge_prefs", Context.MODE_PRIVATE)
    private val nsdDiscoveryManager = NsdDiscoveryManager(application)

    private val _selectedMode = MutableStateFlow(
        try {
            AppMode.valueOf(prefs.getString("pref_active_mode", AppMode.SENDER.name) ?: AppMode.SENDER.name)
        } catch (e: Exception) {
            AppMode.SENDER
        }
    )
    val selectedMode: StateFlow<AppMode> = _selectedMode.asStateFlow()

    private val _targetIp = MutableStateFlow(prefs.getString("pref_target_ip", "") ?: "")
    val targetIp: StateFlow<String> = _targetIp.asStateFlow()

    private val _targetPort = MutableStateFlow(prefs.getInt("pref_target_port", AudioConstants.DEFAULT_PORT).toString())
    val targetPort: StateFlow<String> = _targetPort.asStateFlow()

    private val _localIp = MutableStateFlow("0.0.0.0")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _receiverVolume = MutableStateFlow(1.0f)
    val receiverVolume: StateFlow<Float> = _receiverVolume.asStateFlow()

    // Configurações do Modo Público e Anúncio de IP
    private val _isPublicMode = MutableStateFlow(prefs.getBoolean("pref_public_mode", true))
    val isPublicMode: StateFlow<Boolean> = _isPublicMode.asStateFlow()

    private val _channelName = MutableStateFlow(
        prefs.getString("pref_channel_name", "Canal AudioBridge") ?: "Canal AudioBridge"
    )
    val channelName: StateFlow<String> = _channelName.asStateFlow()

    // Tom de Teste e Fonte de Áudio
    val isTestToneEnabled: StateFlow<Boolean> = AudioSenderService.isTestToneEnabled
    val audioSourceType: StateFlow<AudioSourceType> = AudioSenderService.audioSourceType

    // Canais Descobertos em Segundo Plano na Rede Local
    val discoveredChannels: StateFlow<List<DiscoveredChannel>> = nsdDiscoveryManager.discoveredChannels
    val isDiscovering: StateFlow<Boolean> = nsdDiscoveryManager.isDiscovering
    val isAnnouncing: StateFlow<Boolean> = nsdDiscoveryManager.isAnnouncing

    // Flows expostos a partir dos Serviços
    val isTransmitting: StateFlow<Boolean> = AudioSenderService.isTransmitting
    val senderAudioPeak: StateFlow<Float> = AudioSenderService.audioPeakLevel
    val senderPacketsCount: StateFlow<Long> = AudioSenderService.packetsSentCount
    val senderError: StateFlow<String?> = AudioSenderService.errorMessage

    val isReceiving: StateFlow<Boolean> = AudioReceiverService.isReceiving
    val receiverLatencyMs: StateFlow<Long> = AudioReceiverService.latencyMs
    val receiverPacketLoss: StateFlow<Int> = AudioReceiverService.packetLossCount
    val receiverPacketsCount: StateFlow<Int> = AudioReceiverService.totalReceivedCount
    val receiverError: StateFlow<String?> = AudioReceiverService.errorMessage

    init {
        refreshLocalIp()
        nsdDiscoveryManager.startDiscovery()
    }

    fun setTestToneEnabled(enabled: Boolean) {
        AudioSenderService.setTestToneEnabled(enabled)
    }

    fun setAudioSourceType(type: AudioSourceType) {
        AudioSenderService.setAudioSourceType(type)
    }

    fun selectMode(mode: AppMode) {
        _selectedMode.value = mode
        prefs.edit().putString("pref_active_mode", mode.name).apply()
        refreshLocalIp()

        if (mode == AppMode.CHANNELS || mode == AppMode.SENDER) {
            startChannelDiscovery()
        }
    }

    fun setPublicMode(isPublic: Boolean) {
        _isPublicMode.value = isPublic
        prefs.edit().putBoolean("pref_public_mode", isPublic).apply()

        if (isReceiving.value) {
            if (isPublic) {
                val port = _targetPort.value.toIntOrNull() ?: AudioConstants.DEFAULT_PORT
                nsdDiscoveryManager.startAnnouncement(_channelName.value, port)
            } else {
                nsdDiscoveryManager.stopAnnouncement()
            }
        }
    }

    fun setChannelName(name: String) {
        _channelName.value = name
        prefs.edit().putString("pref_channel_name", name).apply()

        if (isReceiving.value && _isPublicMode.value) {
            val port = _targetPort.value.toIntOrNull() ?: AudioConstants.DEFAULT_PORT
            nsdDiscoveryManager.startAnnouncement(name, port)
        }
    }

    fun selectDiscoveredChannel(channel: DiscoveredChannel) {
        setTargetIp(channel.ip)
        setTargetPort(channel.port.toString())
        selectMode(AppMode.SENDER)
    }

    fun startChannelDiscovery() {
        nsdDiscoveryManager.startDiscovery()
    }

    fun stopChannelDiscovery() {
        nsdDiscoveryManager.stopDiscovery()
    }

    fun setTargetIp(ip: String) {
        _targetIp.value = ip
        prefs.edit().putString("pref_target_ip", ip).apply()
    }

    fun setTargetPort(portStr: String) {
        _targetPort.value = portStr
        val port = portStr.toIntOrNull() ?: AudioConstants.DEFAULT_PORT
        prefs.edit().putInt("pref_target_port", port).apply()
    }

    fun setReceiverVolume(vol: Float) {
        _receiverVolume.value = vol
        AudioReceiverService.setVolume(vol)
    }

    fun refreshLocalIp() {
        viewModelScope.launch {
            _localIp.value = NetworkUtils.getLocalIpAddress(getApplication())
        }
    }

    fun startSender(resultCode: Int, resultData: Intent?) {
        val context = getApplication<Application>()
        val ip = _targetIp.value.trim()
        val port = _targetPort.value.toIntOrNull() ?: AudioConstants.DEFAULT_PORT

        val intent = Intent(context, AudioSenderService::class.java).apply {
            action = AudioConstants.ACTION_START_SENDER
            putExtra(AudioConstants.EXTRA_RESULT_CODE, resultCode)
            if (resultData != null) {
                putExtra(AudioConstants.EXTRA_RESULT_DATA, resultData)
            }
            putExtra(AudioConstants.EXTRA_TARGET_IP, ip)
            putExtra(AudioConstants.EXTRA_TARGET_PORT, port)
        }
        context.startForegroundService(intent)
    }

    fun stopSender() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioSenderService::class.java).apply {
            action = AudioConstants.ACTION_STOP_SENDER
        }
        context.startService(intent)
    }

    fun startReceiver() {
        val context = getApplication<Application>()
        val port = _targetPort.value.toIntOrNull() ?: AudioConstants.DEFAULT_PORT

        val intent = Intent(context, AudioReceiverService::class.java).apply {
            action = AudioConstants.ACTION_START_RECEIVER
            putExtra(AudioConstants.EXTRA_LISTEN_PORT, port)
        }
        context.startForegroundService(intent)

        if (_isPublicMode.value) {
            nsdDiscoveryManager.startAnnouncement(_channelName.value, port)
        }
    }

    fun stopReceiver() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioReceiverService::class.java).apply {
            action = AudioConstants.ACTION_STOP_RECEIVER
        }
        context.startService(intent)
        nsdDiscoveryManager.stopAnnouncement()
    }

    override fun onCleared() {
        nsdDiscoveryManager.stopDiscovery()
        nsdDiscoveryManager.stopAnnouncement()
        super.onCleared()
    }
}
