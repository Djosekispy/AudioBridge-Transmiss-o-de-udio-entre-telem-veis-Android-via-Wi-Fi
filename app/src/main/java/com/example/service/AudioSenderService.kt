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
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.audio.AudioConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.math.sin

enum class AudioSourceType {
    SYSTEM_AUDIO, MICROPHONE
}

/**
 * Serviço de Foreground para capturar o áudio do sistema ou do microfone
 * e transmiti-lo em tempo real via rede UDP para o receptor.
 */
class AudioSenderService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var captureJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var audioRecord: AudioRecord? = null
    private var datagramSocket: DatagramSocket? = null

    companion object {
        private const val TAG = "AudioSenderService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_bridge_sender_channel"

        private val _isTransmitting = MutableStateFlow(false)
        val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

        private val _audioPeakLevel = MutableStateFlow(0f)
        val audioPeakLevel: StateFlow<Float> = _audioPeakLevel.asStateFlow()

        private val _packetsSentCount = MutableStateFlow(0L)
        val packetsSentCount: StateFlow<Long> = _packetsSentCount.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _isTestToneEnabled = MutableStateFlow(false)
        val isTestToneEnabled: StateFlow<Boolean> = _isTestToneEnabled.asStateFlow()

        private val _audioSourceType = MutableStateFlow(AudioSourceType.SYSTEM_AUDIO)
        val audioSourceType: StateFlow<AudioSourceType> = _audioSourceType.asStateFlow()

        fun setTestToneEnabled(enabled: Boolean) {
            _isTestToneEnabled.value = enabled
        }

        fun setAudioSourceType(type: AudioSourceType) {
            _audioSourceType.value = type
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == AudioConstants.ACTION_STOP_SENDER) {
            stopTransmission()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == AudioConstants.ACTION_START_SENDER) {
            val resultCode = intent.getIntExtra(AudioConstants.EXTRA_RESULT_CODE, -1)
            val resultData = intent.getParcelableExtra<Intent>(AudioConstants.EXTRA_RESULT_DATA)
            val targetIp = intent.getStringExtra(AudioConstants.EXTRA_TARGET_IP)?.trim() ?: ""
            val targetPort = intent.getIntExtra(AudioConstants.EXTRA_TARGET_PORT, AudioConstants.DEFAULT_PORT)

            if (targetIp.isBlank()) {
                _errorMessage.value = "Endereço IP inválido."
                stopSelf()
                return START_NOT_STICKY
            }

            startForegroundServiceNotification()
            startAudioCaptureAndStream(resultCode, resultData, targetIp, targetPort)
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification = createNotification("AudioBridge: Transmissão Ativa")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (_audioSourceType.value == AudioSourceType.MICROPHONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AudioBridge (Emissor)")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transmissão de Áudio em Directo",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createMicrophoneRecord(bufferSize: Int): Pair<AudioRecord?, Boolean /* isMono */> {
        // 1. Tenta formato Estéreo
        try {
            val minStereo = AudioRecord.getMinBufferSize(
                AudioConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioConstants.AUDIO_FORMAT
            )
            if (minStereo > 0) {
                val bufSize = Math.max(minStereo, bufferSize)
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioConstants.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioConstants.AUDIO_FORMAT,
                    bufSize
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "AudioRecord inicializado com sucesso em Modo Estéreo.")
                    return Pair(rec, false)
                }
                rec.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gravação do microfone em Estéreo falhou, a tentar Mono...", e)
        }

        // 2. Fallback para Mono (suportado por todos os microfones Android)
        try {
            val minMono = AudioRecord.getMinBufferSize(
                AudioConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioConstants.AUDIO_FORMAT
            )
            if (minMono > 0) {
                val bufSize = Math.max(minMono, bufferSize)
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioConstants.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioConstants.AUDIO_FORMAT,
                    bufSize
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "AudioRecord inicializado com sucesso em Modo Mono.")
                    return Pair(rec, true)
                }
                rec.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gravação do microfone em Mono também falhou.", e)
        }

        return Pair(null, false)
    }

    private fun startAudioCaptureAndStream(
        resultCode: Int,
        resultData: Intent?,
        targetIp: String,
        targetPort: Int
    ) {
        captureJob?.cancel()
        _errorMessage.value = null
        _packetsSentCount.value = 0L

        captureJob = serviceScope.launch {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    AudioConstants.SAMPLE_RATE,
                    AudioConstants.CHANNEL_CONFIG_IN,
                    AudioConstants.AUDIO_FORMAT
                )
                val bufferSize = Math.max(minBufferSize, AudioConstants.PAYLOAD_SIZE_BYTES * 4)

                var isMonoCapture = false
                val record: AudioRecord?

                if (_audioSourceType.value == AudioSourceType.MICROPHONE) {
                    // Captura do Microfone com Fallback Estéreo -> Mono
                    val micPair = createMicrophoneRecord(bufferSize)
                    record = micPair.first
                    isMonoCapture = micPair.second

                    if (record == null) {
                        _errorMessage.value = "Não foi possível aceder ao microfone do dispositivo."
                        stopSelf()
                        return@launch
                    }
                } else {
                    // Captura do Áudio Interno do Sistema via MediaProjection
                    if (resultData == null || resultCode != -1) {
                        _errorMessage.value = "Permissão de captura de ecrã/áudio necessária."
                        stopSelf()
                        return@launch
                    }

                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection = projectionManager.getMediaProjection(resultCode, resultData)
                    mediaProjection = projection

                    if (projection == null) {
                        _errorMessage.value = "Falha ao obter MediaProjection."
                        stopSelf()
                        return@launch
                    }

                    val callback = object : MediaProjection.Callback() {
                        override fun onStop() {
                            stopTransmission()
                        }
                    }
                    projectionCallback = callback
                    projection.registerCallback(callback, Handler(Looper.getMainLooper()))

                    val configBuilder = AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)

                    record = AudioRecord.Builder()
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioConstants.AUDIO_FORMAT)
                                .setSampleRate(AudioConstants.SAMPLE_RATE)
                                .setChannelMask(AudioConstants.CHANNEL_CONFIG_IN)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setAudioPlaybackCaptureConfig(configBuilder.build())
                        .build()
                }

                audioRecord = record

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    _errorMessage.value = "Erro ao inicializar AudioRecord."
                    stopSelf()
                    return@launch
                }

                val socket = DatagramSocket().apply {
                    sendBufferSize = 1024 * 1024
                }
                datagramSocket = socket
                val targetAddress = InetAddress.getByName(targetIp)

                record.startRecording()
                _isTransmitting.value = true

                val payloadBuffer = ByteArray(AudioConstants.PAYLOAD_SIZE_BYTES)
                val monoReadBuffer = if (isMonoCapture) ByteArray(AudioConstants.PAYLOAD_SIZE_BYTES / 2) else ByteArray(0)
                val packetBuffer = ByteArray(AudioConstants.PACKET_SIZE_BYTES)
                var sequenceNumber = 0
                var totalSent = 0L

                var toneSampleIndex = 0.0
                val toneFrequency = 440.0
                val toneSampleRate = AudioConstants.SAMPLE_RATE.toDouble()

                while (_isTransmitting.value) {
                    var bytesRead = 0

                    if (_isTestToneEnabled.value) {
                        // Tom de teste 440Hz
                        var i = 0
                        while (i < payloadBuffer.size - 3) {
                            val sampleVal = (sin(2.0 * Math.PI * toneFrequency * toneSampleIndex / toneSampleRate) * 16000.0).toInt()
                            toneSampleIndex += 1.0

                            payloadBuffer[i] = (sampleVal and 0xFF).toByte()
                            payloadBuffer[i + 1] = ((sampleVal shr 8) and 0xFF).toByte()
                            payloadBuffer[i + 2] = (sampleVal and 0xFF).toByte()
                            payloadBuffer[i + 3] = ((sampleVal shr 8) and 0xFF).toByte()

                            i += 4
                        }
                        bytesRead = payloadBuffer.size
                        Thread.sleep(AudioConstants.DURATION_MS_PER_PACKET.toLong())
                    } else if (isMonoCapture) {
                        // Captura Mono e converte para Estéreo duplicando amostras
                        val readResult = record.read(monoReadBuffer, 0, monoReadBuffer.size)
                        if (readResult > 0) {
                            var monoIdx = 0
                            var stereoIdx = 0
                            while (monoIdx < readResult - 1 && stereoIdx < payloadBuffer.size - 3) {
                                val b1 = monoReadBuffer[monoIdx]
                                val b2 = monoReadBuffer[monoIdx + 1]

                                // Canal Esquerdo
                                payloadBuffer[stereoIdx] = b1
                                payloadBuffer[stereoIdx + 1] = b2
                                // Canal Direito
                                payloadBuffer[stereoIdx + 2] = b1
                                payloadBuffer[stereoIdx + 3] = b2

                                monoIdx += 2
                                stereoIdx += 4
                            }
                            bytesRead = stereoIdx
                        }
                    } else {
                        // Captura Estéreo Directa
                        val readResult = record.read(payloadBuffer, 0, payloadBuffer.size)
                        if (readResult > 0) {
                            bytesRead = readResult
                        }
                    }

                    if (bytesRead > 0) {
                        calculateAudioPeak(payloadBuffer, bytesRead)

                        val timestampMs = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
                        ByteBuffer.wrap(packetBuffer, 0, 4).putInt(sequenceNumber++)
                        ByteBuffer.wrap(packetBuffer, 4, 4).putInt(timestampMs)

                        System.arraycopy(payloadBuffer, 0, packetBuffer, AudioConstants.HEADER_SIZE_BYTES, bytesRead)

                        val packetLength = AudioConstants.HEADER_SIZE_BYTES + bytesRead
                        val datagramPacket = DatagramPacket(
                            packetBuffer,
                            packetLength,
                            targetAddress,
                            targetPort
                        )

                        socket.send(datagramPacket)
                        totalSent++
                        _packetsSentCount.value = totalSent
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro na transmissão de áudio", e)
                _errorMessage.value = "Erro na transmissão: ${e.localizedMessage}"
            } finally {
                cleanupResources()
            }
        }
    }

    private fun calculateAudioPeak(buffer: ByteArray, readSize: Int) {
        var maxAmplitude = 0
        var i = 0
        while (i < readSize - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val absSample = Math.abs(sample.toShort().toInt())
            if (absSample > maxAmplitude) {
                maxAmplitude = absSample
            }
            i += 2
        }
        _audioPeakLevel.value = (maxAmplitude / 32768f).coerceIn(0f, 1f)
    }

    private fun stopTransmission() {
        _isTransmitting.value = false
        cleanupResources()
    }

    private fun cleanupResources() {
        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao libertar AudioRecord", e)
        }
        audioRecord = null

        try {
            projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao libertar MediaProjection", e)
        }
        mediaProjection = null
        projectionCallback = null

        try {
            datagramSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar DatagramSocket", e)
        }
        datagramSocket = null

        _isTransmitting.value = false
        _audioPeakLevel.value = 0f
    }

    override fun onDestroy() {
        stopTransmission()
        serviceScope.launch { captureJob?.cancel() }
        super.onDestroy()
    }
}
