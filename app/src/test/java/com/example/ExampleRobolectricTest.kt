package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProxyUriParser
import com.example.model.ProxyType
import com.example.vpn.PacketUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches CubaTunnel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CubaTunnel", appName)
    }

    @Test
    fun `proxy uri parser handles socks5 and vless uris`() {
        val socks = ProxyUriParser.parseUri("socks5://cubauser:secret@192.0.2.10:1080#MiSocks")
        assertNotNull(socks)
        assertEquals(ProxyType.SOCKS5, socks!!.resolvedProxyType())
        assertEquals("192.0.2.10", socks.host)
        assertEquals(1080, socks.port)
        assertEquals("cubauser", socks.username)
        assertEquals("MiSocks", socks.name)

        val vless = ProxyUriParser.parseUri("vless://11111111-2222-3333-4444-555555555555@example.org:443?sni=cdn.example.org&path=/ws#VlessNode")
        assertNotNull(vless)
        assertEquals(ProxyType.VLESS, vless!!.resolvedProxyType())
        assertEquals("cdn.example.org", vless.sniHost)
        assertEquals("/ws", vless.wsPath)
    }

    @Test
    fun `packet utils builds valid ipv4 udp packet`() {
        val src = InetAddress.getByName("10.8.0.1").address
        val dst = InetAddress.getByName("10.8.0.2").address
        val payload = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val packet = PacketUtils.buildUdpIpv4Packet(src, 53, dst, 49152, payload)

        val parsed = PacketUtils.parseIpv4Header(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(PacketUtils.PROTO_UDP, parsed!!.protocol)
        assertEquals("10.8.0.1", parsed.srcAddressString())
        assertEquals("10.8.0.2", parsed.dstAddressString())
        assertTrue(packet.size == 20 + 8 + payload.size)
    }
}
