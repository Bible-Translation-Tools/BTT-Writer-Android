package com.door43.translationstudio.services

import android.app.Service
import android.net.wifi.WifiManager
import android.util.Base64
import com.door43.translationstudio.network.Connection
import com.door43.translationstudio.network.Peer
import com.door43.util.RSAEncryption
import com.tozny.crypto.android.AesCbcWithIntegrity
import com.tozny.crypto.android.AesCbcWithIntegrity.CipherTextIvMac
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Enumeration

/**
 * Created by joel on 7/23/2015.
 */
abstract class NetworkService : Service() {
    private val _peers: MutableMap<String, Peer> = HashMap()

    @get:Throws(UnknownHostException::class)
    /**
     * Returns the broadcast ip address
     * @return
     * @throws IOException
     */
    val broadcastAddress: InetAddress
        get() {
            val wifi = application.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val dhcp = wifi.dhcpInfo ?: throw UnknownHostException()

            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = ((broadcast shr k * 8) and 0xFF).toByte()
            }
            return InetAddress.getByAddress(quads)
        }

    /**
     * Adds a network peer to the list of known peers for this service
     * @param p The peer to be added to the list of peers
     * @return returns true if the peer is new and was added
     */
    protected fun addPeer(p: Peer): Boolean {
        if (!_peers.containsKey(p.ipAddress)) {
            _peers[p.ipAddress] = p
            return true
        } else if (_peers[p.ipAddress]?.port != p.port) {
            // the port changed, replace peer
            _peers[p.ipAddress] = p
            return true
        } else {
            _peers[p.ipAddress]?.touch()
            return false
        }
    }

    /**
     * Removes a peer from the list of peers
     * @param p the peer to be removed
     */
    protected fun removePeer(p: Peer) {
        if (_peers.containsKey(p.ipAddress)) {
            _peers.remove(p.ipAddress)
        }
    }

    /**
     * Returns a list of peers that are connected to this service
     * @return
     */
    val peers: ArrayList<Peer>
        get() = ArrayList(_peers.values)

    /**
     * Opens a new temporary socket for transfering a file and lets the client know it should connect to it.
     * TODO: I don't think we should attempt to throw too much into the client and server classes.
     * They work well at establishing initial contact. We should place this elsewhere.
     */
    fun openWriteSocket(listener: OnSocketEventListener): ServerSocket? {
        val serverSocket: ServerSocket
        try {
            serverSocket = ServerSocket(0)
            serverSocket.soTimeout = CONNECTION_TIMEOUT
        } catch (e: IOException) {
            Logger.e(this.javaClass.name, "failed to create a sender socket", e)
            return null
        }
        // begin listening for the socket connection
        val t = Thread(object : Runnable {
            override fun run() {
                val socket: Socket
                try {
                    socket = serverSocket.accept()
                    listener.onOpen(Connection(socket))
                } catch (e: IOException) {
                    Logger.e(this.javaClass.name, "failed to accept the receiver socket", e)
                    return
                }
                try {
                    serverSocket.close()
                } catch (e: IOException) {
                    Logger.e(this.javaClass.name, "failed to close the sender socket", e)
                }
            }
        })
        t.start()
        return serverSocket
    }

    /**
     * Connects to the end of a data socket
     * @param listener
     * @return
     */
    fun openReadSocket(peer: Peer, port: Int, listener: OnSocketEventListener) {
        val t = Thread {
            try {
                val serverAddress = InetAddress.getByName(peer.ipAddress)
                val socket = Socket(serverAddress, port)
                socket.soTimeout = CONNECTION_TIMEOUT
                listener.onOpen(Connection(socket))
            } catch (e: UnknownHostException) {
                Thread.currentThread().interrupt()
            } catch (e: IOException) {
                Thread.currentThread().interrupt()
            }
        }
        t.start()
    }

    /**
     * Encrypts a message with a public key
     * @param publicKey the public key that will be used to encrypt the message
     * @param message the message to be encrypted
     * @return the encrypted message
     */
    fun encryptMessage(publicKey: PublicKey?, message: String?): String? {
        // TRICKY: RSA is not good for encrypting large amounts of data.
        // So we first encrypt the data then encrypt the encryption key using the public key.
        // the encrypted key is then attached to the encrypted message.

        try {
            // encrypt message
            val key = AesCbcWithIntegrity.generateKey()
            val civ = AesCbcWithIntegrity.encrypt(message, key)
            val encryptedMessage = civ.toString()

            // encrypt key
            val encryptedKeyBytes =
                RSAEncryption.encryptData(AesCbcWithIntegrity.keyString(key), publicKey)
            if (encryptedKeyBytes == null) {
                Logger.e(this.javaClass.name, "Failed to encrypt the message")
                return null
            }
            // encode key
            val encryptedKey = String(Base64.encode(encryptedKeyBytes, Base64.NO_WRAP))
            return "$encryptedKey-key-$encryptedMessage"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Decrypts a message using the private key
     * @param privateKey
     * @param message the message to be decrypted
     * @return the decrypted message
     */
    fun decryptMessage(privateKey: PrivateKey?, message: String?): String? {
        // extract encryption key
        try {
            val pieces = message?.split("-key-".toRegex()) ?: listOf()
            if (pieces.size == 2) {
                // decode key
                val data = Base64.decode(pieces[0].toByteArray(), Base64.NO_WRAP)
                // decrypt key
                val key = AesCbcWithIntegrity.keys(RSAEncryption.decryptData(data, privateKey))

                // decrypt message
                val civ = CipherTextIvMac(pieces[1])
                return AesCbcWithIntegrity.decryptString(civ, key)
            } else {
                Logger.w(this.javaClass.name, "Invalid message to decrypt")
                return null
            }
        } catch (e: Exception) {
            Logger.e(this.javaClass.name, "Invalid message to decrypt", e)
            return null
        }
    }

    interface OnSocketEventListener {
        fun onOpen(connection: Connection)
    }

    companion object {
        private const val CONNECTION_TIMEOUT = 30000 // 30 seconds
        val ipAddress: String?
            /**
             * Returns the ip address of the device
             * @return
             */
            get() {
                try {
                    val en: Enumeration<*> = NetworkInterface.getNetworkInterfaces()
                    while (en.hasMoreElements()) {
                        val intf = en.nextElement() as NetworkInterface
                        val enumIpAddr: Enumeration<*> = intf.inetAddresses
                        while (enumIpAddr.hasMoreElements()) {
                            val inetAddress = enumIpAddr.nextElement() as InetAddress
                            if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                                val ipAddress = inetAddress.getHostAddress().toString()
                                return ipAddress
                            }
                        }
                    }
                } catch (ex: SocketException) {
                    ex.printStackTrace()
                }
                return null
            }

        /**
         * Returns a list of ip addresses on the local network
         * @param subnet the subnet address. This can be any ip address and the subnet will automatically be retreived.
         * @return
         */
        fun checkHosts(subnet: String): List<String> {
            var subnet = subnet
            val hosts: MutableList<String> = ArrayList()
            val timeout = 1000

            // trim down to just the subnet
            val pieces = subnet.trim().split("\\.".toRegex())
            if (pieces.size == 4) {
                subnet = pieces[0] + "." + pieces[1] + "." + pieces[2]
            } else if (pieces.size != 3) {
                return hosts
            }

            for (i in 1..253) {
                val host = "$subnet.$i"
                try {
                    if (InetAddress.getByName(host).isReachable(timeout)) {
                        hosts.add(host)
                        Logger.i(NetworkService::class.java.name, "$host is reachable")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return hosts
        }

        fun getPrivateKey(file: File?): PrivateKey? {
            return RSAEncryption.readPrivateKeyFromFile(file)
        }

        fun readPublicKey(file: File?): String? {
            val publicKey = RSAEncryption.readPublicKeyFromFile(file)
            return RSAEncryption.getPublicKeyAsString(publicKey)
        }
    }
}
