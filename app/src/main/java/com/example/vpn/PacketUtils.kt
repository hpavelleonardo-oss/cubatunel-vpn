package com.example.vpn

import java.net.InetAddress

object PacketUtils {
    const val PROTO_ICMP = 1
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    data class Ipv4Header(
        val version: Int,
        val headerLength: Int,
        val totalLength: Int,
        val protocol: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray
    ) {
        fun srcAddressString(): String = InetAddress.getByAddress(srcIp).hostAddress ?: "0.0.0.0"
        fun dstAddressString(): String = InetAddress.getByAddress(dstIp).hostAddress ?: "0.0.0.0"
    }

    fun parseIpv4Header(packet: ByteArray, length: Int): Ipv4Header? {
        if (length < 20) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null
        val totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        val protocol = packet[9].toInt() and 0xFF
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        return Ipv4Header(
            version = version,
            headerLength = ihl,
            totalLength = totalLen.coerceAtMost(length),
            protocol = protocol,
            srcIp = srcIp,
            dstIp = dstIp
        )
    }

    fun readUInt16(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)

    fun readUInt32(buf: ByteArray, offset: Int): Long =
        ((buf[offset].toLong() and 0xFF) shl 24) or
            ((buf[offset + 1].toLong() and 0xFF) shl 16) or
            ((buf[offset + 2].toLong() and 0xFF) shl 8) or
            (buf[offset + 3].toLong() and 0xFF)

    fun writeUInt16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    fun writeUInt32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    fun buildUdpIpv4Packet(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + payload.size
        val out = ByteArray(totalLen)

        // IPv4 header
        out[0] = 0x45.toByte()
        out[1] = 0x00
        writeUInt16(out, 2, totalLen)
        writeUInt16(out, 4, (System.nanoTime() and 0xFFFF).toInt())
        writeUInt16(out, 6, 0x4000) // Don't fragment
        out[8] = 64 // TTL
        out[9] = PROTO_UDP.toByte()
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        writeUInt16(out, 10, computeChecksum(out, 0, ipHeaderLen))

        // UDP header
        writeUInt16(out, ipHeaderLen, srcPort)
        writeUInt16(out, ipHeaderLen + 2, dstPort)
        writeUInt16(out, ipHeaderLen + 4, udpHeaderLen + payload.size)
        System.arraycopy(payload, 0, out, ipHeaderLen + udpHeaderLen, payload.size)

        val udpChecksum = computeTransportChecksum(
            srcIp = srcIp,
            dstIp = dstIp,
            protocol = PROTO_UDP,
            segment = out,
            segmentOffset = ipHeaderLen,
            segmentLength = udpHeaderLen + payload.size
        )
        writeUInt16(out, ipHeaderLen + 6, if (udpChecksum == 0) 0xFFFF else udpChecksum)
        return out
    }

    fun buildTcpIpv4Packet(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        windowSize: Int = 65535,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val totalLen = ipHeaderLen + tcpHeaderLen + payload.size
        val out = ByteArray(totalLen)

        // IPv4 header
        out[0] = 0x45.toByte()
        out[1] = 0x00
        writeUInt16(out, 2, totalLen)
        writeUInt16(out, 4, (System.nanoTime() and 0xFFFF).toInt())
        writeUInt16(out, 6, 0x4000)
        out[8] = 64
        out[9] = PROTO_TCP.toByte()
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        writeUInt16(out, 10, computeChecksum(out, 0, ipHeaderLen))

        // TCP header
        writeUInt16(out, ipHeaderLen, srcPort)
        writeUInt16(out, ipHeaderLen + 2, dstPort)
        writeUInt32(out, ipHeaderLen + 4, seqNum)
        writeUInt32(out, ipHeaderLen + 8, ackNum)
        out[ipHeaderLen + 12] = ((tcpHeaderLen / 4) shl 4).toByte()
        out[ipHeaderLen + 13] = (flags and 0x3F).toByte()
        writeUInt16(out, ipHeaderLen + 14, windowSize)
        writeUInt16(out, ipHeaderLen + 16, 0) // Checksum placeholder
        writeUInt16(out, ipHeaderLen + 18, 0) // Urgent pointer

        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, out, ipHeaderLen + tcpHeaderLen, payload.size)
        }

        val tcpChecksum = computeTransportChecksum(
            srcIp = srcIp,
            dstIp = dstIp,
            protocol = PROTO_TCP,
            segment = out,
            segmentOffset = ipHeaderLen,
            segmentLength = tcpHeaderLen + payload.size
        )
        writeUInt16(out, ipHeaderLen + 16, tcpChecksum)
        return out
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun computeTransportChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        protocol: Int,
        segment: ByteArray,
        segmentOffset: Int,
        segmentLength: Int
    ): Int {
        var sum = 0L
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += protocol.toLong()
        sum += segmentLength.toLong()

        var i = segmentOffset
        val end = segmentOffset + segmentLength
        while (i + 1 < end) {
            val word = ((segment[i].toInt() and 0xFF) shl 8) or (segment[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (segment[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }
}
