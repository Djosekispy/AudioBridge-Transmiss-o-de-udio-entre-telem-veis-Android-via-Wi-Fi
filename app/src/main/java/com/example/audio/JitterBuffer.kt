package com.example.audio

import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Representa um pacote de áudio individual com o seu cabeçalho e dados PCM.
 */
data class AudioPacket(
    val sequenceNumber: Int,
    val timestampMs: Int,
    val payload: ByteArray
)

/**
 * Jitter Buffer para ordenar pacotes UDP, suavizar a variação de latência (jitter),
 * descartar duplicados/atrasados e preencher lacunas com silêncio para evitar estalidos.
 */
class JitterBuffer {

    private val packets = ConcurrentSkipListMap<Int, AudioPacket>()
    
    private var expectedSeqNumber = -1
    private var isBuffering = true
    
    val totalReceivedCount = AtomicInteger(0)
    val totalLostCount = AtomicInteger(0)
    val currentLatencyMs = AtomicLong(0)
    
    val silencePayload = ByteArray(AudioConstants.PAYLOAD_SIZE_BYTES)

    @Synchronized
    fun reset() {
        packets.clear()
        expectedSeqNumber = -1
        isBuffering = true
        totalReceivedCount.set(0)
        totalLostCount.set(0)
        currentLatencyMs.set(0)
    }

    @Synchronized
    fun offer(seq: Int, timestampMs: Int, payload: ByteArray) {
        totalReceivedCount.incrementAndGet()

        // Calcula a latência estimada
        val nowMs = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val lat = (nowMs - timestampMs).coerceAtLeast(0)
        currentLatencyMs.set(lat.toLong())

        if (expectedSeqNumber == -1) {
            expectedSeqNumber = seq
        }

        // Se o pacote for mais antigo do que o que já estamos a reproduzir, descarta
        if (seq < expectedSeqNumber) {
            return
        }

        // Adiciona ao buffer se não for duplicado
        if (!packets.containsKey(seq)) {
            // Evita crescimento ilimitado em redes muito ruidosas
            if (packets.size >= AudioConstants.MAX_BUFFER_PACKETS) {
                // Remove o mais antigo
                packets.pollFirstEntry()
            }
            packets[seq] = AudioPacket(seq, timestampMs, payload)
        }

        // Verifica se terminou o buffer inicial (ex: 60-100ms)
        if (isBuffering && packets.size >= AudioConstants.INITIAL_BUFFER_PACKETS) {
            isBuffering = false
        }
    }

    @Synchronized
    fun pollNextPayload(): ByteArray {
        // Se ainda estiver no estado de buffer inicial e houver poucos pacotes, devolve silêncio
        if (isBuffering && packets.size < AudioConstants.INITIAL_BUFFER_PACKETS) {
            return silencePayload
        }

        if (expectedSeqNumber == -1) {
            return silencePayload
        }

        val packet = packets.remove(expectedSeqNumber)
        if (packet != null) {
            expectedSeqNumber++
            return packet.payload
        }

        // Se o pacote esperado não estiver presente, mas já existirem pacotes mais recentes no buffer,
        // consideramos o pacote perdido, avançamos o número de sequência e geramos silêncio.
        val highestKey = packets.keys.maxOrNull()
        if (highestKey != null && highestKey > expectedSeqNumber) {
            totalLostCount.incrementAndGet()
            expectedSeqNumber++
            return silencePayload
        }

        // Se a fila estiver vazia, re-entramos em estado de buffer curto para evitar gaguez contínua
        if (packets.isEmpty()) {
            isBuffering = true
        }

        return silencePayload
    }
}
