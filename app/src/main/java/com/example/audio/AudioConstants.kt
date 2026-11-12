package com.example.audio

import android.media.AudioFormat

/**
 * Constantes globais para a configuração de áudio e pacotes UDP do AudioBridge.
 */
object AudioConstants {
    const val SAMPLE_RATE = 44100
    const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_STEREO
    const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_STEREO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    // 44100 amostras/seg * 2 canais * 2 bytes/amostra * 0.02s (20 ms) = 3528 bytes
    const val FRAME_SIZE_BYTES = 4 // 2 canais * 2 bytes (16-bit)
    const val DURATION_MS_PER_PACKET = 20
    const val PAYLOAD_SIZE_BYTES = (SAMPLE_RATE * FRAME_SIZE_BYTES * DURATION_MS_PER_PACKET) / 1000 // 3528 bytes
    const val HEADER_SIZE_BYTES = 8 // 4 bytes número de sequência + 4 bytes timestamp (ms)
    const val PACKET_SIZE_BYTES = HEADER_SIZE_BYTES + PAYLOAD_SIZE_BYTES // 3536 bytes

    const val DEFAULT_PORT = 5000
    
    // Jitter Buffer (60ms a 100ms de buffer inicial = 3 a 5 pacotes)
    const val INITIAL_BUFFER_PACKETS = 4
    const val MAX_BUFFER_PACKETS = 20

    // Constantes de Intent / Actions
    const val ACTION_START_SENDER = "com.example.audiobridge.START_SENDER"
    const val ACTION_STOP_SENDER = "com.example.audiobridge.STOP_SENDER"
    const val ACTION_START_RECEIVER = "com.example.audiobridge.START_RECEIVER"
    const val ACTION_STOP_RECEIVER = "com.example.audiobridge.STOP_RECEIVER"

    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
    const val EXTRA_TARGET_IP = "extra_target_ip"
    const val EXTRA_TARGET_PORT = "extra_target_port"
    const val EXTRA_LISTEN_PORT = "extra_listen_port"
}
