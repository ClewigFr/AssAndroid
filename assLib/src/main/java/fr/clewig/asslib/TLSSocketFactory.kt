package fr.clewig.asslib

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * The platform's TLS socket factory with TLS 1.2/1.3 turned on (off by default on API 16-19)
 * and never the broken SSLv3/TLS 1.0/1.1.
 */
internal class TLSSocketFactory : SSLSocketFactory() {
    private val delegate: SSLSocketFactory = SSLContext.getInstance("TLS").apply { init(null, null, null) }.socketFactory

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        secure(delegate.createSocket(s, host, port, autoClose))

    override fun createSocket(host: String, port: Int): Socket =
        secure(delegate.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        secure(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        secure(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        secure(delegate.createSocket(address, port, localAddress, localPort))

    private fun secure(socket: Socket): Socket {
        if (socket is SSLSocket) {
            val protocols = socket.supportedProtocols.filter { it in SECURE_PROTOCOLS }
            if (protocols.isNotEmpty()) socket.enabledProtocols = protocols.toTypedArray()
        }
        return socket
    }

    private companion object {
        val SECURE_PROTOCOLS = setOf("TLSv1.2", "TLSv1.3")
    }
}
