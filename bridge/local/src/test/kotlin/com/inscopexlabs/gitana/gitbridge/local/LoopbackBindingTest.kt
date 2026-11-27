package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackBindingTest {
    @Test
    fun allowsOnlyLoopback() {
        assertTrue(LoopbackBinding.isAllowed("127.0.0.1"))
        assertTrue(LoopbackBinding.isAllowed("::1"))
    }

    @Test
    fun rejectsWildcardLanAndHostnames() {
        listOf("0.0.0.0", "::", "192.168.1.10", "10.0.0.2", "localhost", "").forEach {
            assertFalse("should reject '$it'", LoopbackBinding.isAllowed(it))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireLoopbackThrowsForWildcard() {
        LoopbackBinding.requireLoopback("0.0.0.0")
    }

    @Test
    fun rejectsPrivilegedAndOutOfRangePorts() {
        listOf(-1, 0, 80, 1023, 65536).forEach { assertFalse("port $it", LoopbackBinding.isAllowedPort(it)) }
        listOf(1024, LoopbackBinding.DEFAULT_PORT, 65535).forEach {
            assertTrue("port $it", LoopbackBinding.isAllowedPort(it))
        }
    }

    @Test
    fun ipv6FailureIsNonFatalAndLogged() = runBlocking {
        val log = mutableListOf<String>()
        val server = LocalServer(fakeFactory(failing = LoopbackBinding.IPV6), { log.add(it) })
        server.start()
        assertEquals(
            LocalServerState.Running(listOf(LoopbackBinding.IPV4), LoopbackBinding.DEFAULT_PORT),
            server.state.value,
        )
        assertTrue(log.any { "IPv6" in it })
    }

    @Test
    fun ipv4FailurePreventsStart() = runBlocking {
        val attempted = mutableListOf<String>()
        val server = LocalServer(fakeFactory(failing = LoopbackBinding.IPV4, attempted), {})
        server.start()
        assertEquals(
            LocalServerState.Failed(LocalServerFailure.ADDRESS_UNAVAILABLE),
            server.state.value,
        )
        assertEquals(listOf(LoopbackBinding.IPV4), attempted)
    }

    @Test
    fun realListenerAppliesGuards() = runBlocking {
        val port = freePort()
        val server = ktorServer(port)
        try {
            server.start()
            assertTrue("${server.state.value}", server.state.value is LocalServerState.Running)
            val unknownRoute = exchange(port, "Host: 127.0.0.1:$port")
            assertTrue(unknownRoute, unknownRoute.startsWith("HTTP/1.1 404"))
            assertTrue(unknownRoute, "\"code\":\"NOT_FOUND\"" in unknownRoute)
            val badHost = exchange(port, "Host: example.org")
            assertTrue(badHost, badHost.startsWith("HTTP/1.1 403"))
            val tooLarge = exchange(port, "Host: localhost", "Content-Length: 2097153")
            assertTrue(tooLarge, tooLarge.startsWith("HTTP/1.1 413"))
        } finally {
            server.stop()
        }
        assertEquals(LocalServerState.Stopped, server.state.value)
    }

    @Test
    fun realBindConflictIsReportedAsFailure() = runBlocking {
        ServerSocket().use { occupied ->
            occupied.bind(InetSocketAddress(InetAddress.getByName(LoopbackBinding.IPV4), 0))
            val server = ktorServer(occupied.localPort)
            try {
                server.start()
                assertTrue("${server.state.value}", server.state.value is LocalServerState.Failed)
            } finally {
                server.stop()
            }
        }
    }

    private fun fakeFactory(failing: String, attempted: MutableList<String> = mutableListOf()) =
        LocalListenerFactory { host, _, _ ->
            object : LocalListener {
                override suspend fun start() {
                    attempted.add(host)
                    if (host == failing) throw BindException("in use")
                }

                override suspend fun stop() = Unit
            }
        }

    private fun ktorServer(port: Int): LocalServer {
        val clock = object : ClockPort {
            override fun nowMillis(): Long = System.currentTimeMillis()
        }
        return LocalServer(KtorListenerFactory(clock, {}), {}, port)
    }

    private fun freePort(): Int =
        ServerSocket(0, 1, InetAddress.getByName(LoopbackBinding.IPV4)).use { it.localPort }

    private fun exchange(port: Int, vararg headers: String): String =
        Socket(LoopbackBinding.IPV4, port).use { socket ->
            socket.soTimeout = 5_000
            val head = headers.joinToString("") { "$it\r\n" }
            socket.getOutputStream().apply {
                write("GET /unknown HTTP/1.1\r\n${head}Connection: close\r\n\r\n".toByteArray())
                flush()
            }
            val reader = socket.getInputStream().bufferedReader()
            val status = reader.readLine().orEmpty()
            // A rejected body is never sent, so only the status line is guaranteed to complete.
            if (" 413" in status) status else status + "\n" + reader.readText()
        }
}
