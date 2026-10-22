package com.example.adblocker

import java.io.ByteArrayOutputStream

/** Utilidades mínimas para leer consultas DNS y armar paquetes IPv4/UDP de respuesta. */
object DnsPackets {

    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28

    fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    /** Devuelve el nombre consultado (p. ej. "ads.example.com") o null si no es una consulta válida. */
    fun queryName(dns: ByteArray): String? = parseQuestion(dns)?.first

    /** (nombre, offset donde termina la pregunta, tipo de consulta) */
    private fun parseQuestion(dns: ByteArray): Triple<String, Int, Int>? {
        if (dns.size < 17) return null
        if ((dns[2].toInt() and 0x80) != 0) return null // es una respuesta, no una consulta
        if (u16(dns, 4) < 1) return null
        val labels = ArrayList<String>()
        var pos = 12
        while (true) {
            if (pos >= dns.size) return null
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if (len and 0xC0 != 0) return null // las consultas no usan compresión
            if (pos + 1 + len > dns.size) return null
            labels.add(String(dns, pos + 1, len, Charsets.US_ASCII))
            pos += 1 + len
        }
        if (pos + 4 > dns.size) return null
        val qtype = u16(dns, pos)
        return Triple(labels.joinToString(".").lowercase(), pos + 4, qtype)
    }

    /**
     * Respuesta DNS para un dominio bloqueado: 0.0.0.0 para A, :: para AAAA,
     * y respuesta vacía (NODATA) para cualquier otro tipo.
     */
    fun blockedResponse(query: ByteArray): ByteArray? {
        val (_, qEnd, qtype) = parseQuestion(query) ?: return null
        val withAnswer = qtype == TYPE_A || qtype == TYPE_AAAA
        val out = ByteArrayOutputStream()
        out.write(query[0].toInt()); out.write(query[1].toInt())            // ID
        out.write((query[2].toInt() and 0x79) or 0x80)                       // QR=1, opcode y RD copiados
        out.write(0x80)                                                       // RA=1, RCODE=0
        out.write(0); out.write(1)                                            // QDCOUNT
        out.write(0); out.write(if (withAnswer) 1 else 0)                     // ANCOUNT
        repeat(4) { out.write(0) }                                            // NSCOUNT, ARCOUNT
        out.write(query, 12, qEnd - 12)                                       // pregunta original
        if (withAnswer) {
            out.write(0xC0); out.write(0x0C)                                  // puntero al nombre
            out.write(0); out.write(qtype)                                    // TYPE
            out.write(0); out.write(1)                                        // CLASS IN
            out.write(0); out.write(0); out.write(0x01); out.write(0x2C)      // TTL 300 s
            val rdLen = if (qtype == TYPE_A) 4 else 16
            out.write(0); out.write(rdLen)
            repeat(rdLen) { out.write(0) }                                    // 0.0.0.0 o ::
        }
        return out.toByteArray()
    }

    /**
     * Construye un paquete IPv4/UDP de respuesta para la consulta [request]
     * (paquete IPv4 original), con [payload] como datos DNS.
     */
    fun buildIpv4UdpResponse(request: ByteArray, ihl: Int, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40                                   // Don't Fragment
        p[8] = 64                                     // TTL
        p[9] = 17                                     // UDP
        System.arraycopy(request, 16, p, 12, 4)       // origen = destino original
        System.arraycopy(request, 12, p, 16, 4)       // destino = origen original
        val cs = ipChecksum(p, 0, 20)
        p[10] = (cs shr 8).toByte(); p[11] = cs.toByte()
        // UDP: puertos invertidos, checksum 0 (opcional en IPv4)
        p[20] = request[ihl + 2]; p[21] = request[ihl + 3]
        p[22] = request[ihl]; p[23] = request[ihl + 1]
        p[24] = (udpLen shr 8).toByte(); p[25] = udpLen.toByte()
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    private fun ipChecksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len) {
            sum += u16(b, i)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
