package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.audio.AudioConstants
import com.example.audio.JitterBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

/**
 * Serviço de Foreground para receber os pacotes UDP de áudio, processá-los
 * através do JitterBuffer e reproduzi-los com AudioTrack.
 */
class AudioReceiverService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var receiveJob: Job? = null
    private var playbackJob: Job? = null

    private var audioTrack: AudioTrack? = null
    private var datagramSocket: DatagramSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val jitterBuffer = JitterBuffer()

    companion object {
        private const val TAG = "AudioReceiverService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "audio_bridge_receiver_channel"

        private val _isReceiving = MutableStateFlow(false)
        val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

        private val _volume = MutableStateFlow(1.0f)
        val volume: StateFlow<Float> = _volume.asStateFlow()

        private val _latencyMs = MutableStateFlow(0L)
        val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

        private val _packetLossCount = MutableStateFlow(0)
        val packetLossCount: StateFlow<Int> = _packetLossCount.asStateFlow()

        private val _totalReceivedCount = MutableStateFlow(0)
        val totalReceivedCount: StateFlow<Int> = _totalReceivedCount.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        fun setVolume(vol: Float) {
            val clamped = vol.coerceIn(0f, 1f)
            _volume.value = clamped
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == AudioConstants.ACTION_STOP_RECEIVER) {
            stopReceiver()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == AudioConstants.ACTION_START_RECEIVER) {
            val listenPort = intent.getIntExtra(AudioConstants.EXTRA_LISTEN_PORT, AudioConstants.DEFAULT_PORT)
            startForegroundServiceNotification()
            acquireLocks()
            startAudioReceiver(listenPort)
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification = createNotification("AudioBridge: Receptor Ativo")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AudioBridge (Receptor)")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recepção de Áudio em Directo",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AudioBridge::ReceiverWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "AudioBridge::WifiLock")
        } else {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioBridge::WifiLock")
        }.apply {
            acquire()
        }
    }

    private fun startAudioReceiver(port: Int) {
        stopReceiverJobs()
        _errorMessage.value = null
        jitterBuffer.reset()

        // Inicialização robusta do AudioTrack
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                AudioConstants.SAMPLE_RATE,
                AudioConstants.CHANNEL_CONFIG_OUT,
                AudioConstants.AUDIO_FORMAT
            )
            val bufferSize = Math.max(minBufferSize, AudioConstants.PAYLOAD_SIZE_BYTES * 8)

            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioConstants.AUDIO_FORMAT)
                            .setSampleRate(AudioConstants.SAMPLE_RATE)
                            .setChannelMask(AudioConstants.CHANNEL_CONFIG_OUT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
            } catch (e: Exception) {
                // Fallback para modo de performance padrão caso LOW_LATENCY falhe
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioConstants.AUDIO_FORMAT)
                            .setSampleRate(AudioConstants.SAMPLE_RATE)
                            .setChannelMask(AudioConstants.CHANNEL_CONFIG_OUT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }

            track.play()
            track.setVolume(_volume.value)
            audioTrack = track
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar AudioTrack", e)
            _errorMessage.value = "Erro ao criar AudioTrack: ${e.localizedMessage}"
            stopSelf()
            return
        }

        try {
            val socket = DatagramSocket(port).apply {
                reuseAddress = true
                receiveBufferSize = 1024 * 1024 // 1 MB buffer
                soTimeout = 2000 // Timeout de 2s para nao bloquear infinitamente
            }
            datagramSocket = socket
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir porta UDP $port", e)
            _errorMessage.value = "Erro ao abrir porta UDP $port: ${e.localizedMessage}"
            stopSelf()
            return
        }

        _isReceiving.value = true

        // Thread 1: Escuta pacotes UDP e guarda no JitterBuffer
        receiveJob = serviceScope.launch {
            val buffer = ByteArray(AudioConstants.PACKET_SIZE_BYTES)
            val packet = DatagramPacket(buffer, buffer.size)

            while (_isReceiving.value) {
                try {
                    datagramSocket?.receive(packet)
                    val len = packet.length
                    if (len >= AudioConstants.HEADER_SIZE_BYTES) {
                        val seq = ByteBuffer.wrap(buffer, 0, 4).int
                        val timestampMs = ByteBuffer.wrap(buffer, 4, 4).int
                        val payloadSize = len - AudioConstants.HEADER_SIZE_BYTES
                        val payload = ByteArray(payloadSize)
                        System.arraycopy(buffer, AudioConstants.HEADER_SIZE_BYTES, payload, 0, payloadSize)

                        jitterBuffer.offer(seq, timestampMs, payload)

                        _latencyMs.value = jitterBuffer.currentLatencyMs.get()
                        _totalReceivedCount.value = jitterBuffer.totalReceivedCount.get()
                        _packetLossCount.value = jitterBuffer.totalLostCount.get()
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout normal quando não há pacotes a chegar
                } catch (e: Exception) {
                    if (_isReceiving.value) {
                        Log.e(TAG, "Erro na recepção UDP", e)
                    }
                }
            }
        }

        // Thread 2: Extrai do JitterBuffer e escreve no AudioTrack
        playbackJob = serviceScope.launch {
            while (_isReceiving.value) {
                val track = audioTrack ?: break
                track.setVolume(_volume.value)

                val pcmData = jitterBuffer.pollNextPayload()
                if (pcmData.isNotEmpty()) {
                    track.write(pcmData, 0, pcmData.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    private fun stopReceiverJobs() {
        _isReceiving.value = false
        receiveJob?.cancel()
        playbackJob?.cancel()
        receiveJob = null
        playbackJob = null
    }

    private fun stopReceiver() {
        stopReceiverJobs()

        try {
            datagramSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar socket", e)
        }
        datagramSocket = null

        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao libertar AudioTrack", e)
        }
        audioTrack = null

        releaseLocks()
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao libertar WakeLock", e)
        }
        wakeLock = null

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao libertar WifiLock", e)
        }
        wifiLock = null
    }

    override fun onDestroy() {
        stopReceiver()
        super.onDestroy()
    }
}
