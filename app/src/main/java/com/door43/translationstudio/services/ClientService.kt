package com.door43.translationstudio.services

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.isTablet
import com.door43.translationstudio.App.Companion.udid
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.network.Connection
import com.door43.translationstudio.network.Peer
import com.door43.usecases.ImportProjects
import com.door43.usecases.ImportProjects.ImportResults
import com.door43.util.RSAEncryption
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.tools.logger.Logger
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.PrivateKey
import java.util.UUID
import javax.inject.Inject

/**
 * This class provides an importing service (effectively a client) that can
 * communicate with an exporting service (server) to browse and retrieve translations
 */
@AndroidEntryPoint
class ClientService : NetworkService() {
    @Inject lateinit var translator: Translator
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var directoryProvider: IDirectoryProvider

    private val binder: IBinder = LocalBinder()
    private var listener: OnClientEventListener? = null
    private val serverConnections: MutableMap<String, Connection> = HashMap()
    private var privateKey: PrivateKey? = null
    private var publicKey: String? = null
    private var deviceAlias: String? = null
    private val requests: MutableMap<UUID, Request> = HashMap()

    /**
     * Sets whether or not the service is running
     * @param running
     */
    private fun setRunning(running: Boolean) {
        isRunning = running
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun setOnClientEventListener(callback: OnClientEventListener?) {
        listener = callback
        if (isRunning) {
            listener?.onClientServiceReady()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startid: Int): Int {
        if (intent != null) {
            val args = intent.extras
            if (args != null && args.containsKey(PARAM_DEVICE_ALIAS)) {
                privateKey = getPrivateKey(directoryProvider.p2pPrivateKey)
                publicKey = readPublicKey(directoryProvider.p2pPublicKey)
                deviceAlias = args.getString(PARAM_DEVICE_ALIAS)
                listener?.onClientServiceReady()
                setRunning(true)
                return START_STICKY
            }
        }
        Logger.e(this.javaClass.name, "Import service requires arguments")
        stopService()
        return START_NOT_STICKY
    }

    /**
     * Establishes a TCP connection with the server.
     * Once this connection has been made the cleanup thread won't identify the server as lost unless the tcp connection is also disconnected.
     * @param server the server we will connect to
     */
    fun connectToServer(server: Peer) {
        if (!serverConnections.containsKey(server.ipAddress)) {
            val serverThread = ServerThread(server)
            Thread(serverThread).start()
        }
    }

    /**
     * Stops the service
     */
    private fun stopService() {
        Logger.i(this.javaClass.name, "Stopping client service")
        // close sockets
        for (key in serverConnections.keys) {
            serverConnections[key]?.close()
        }
        setRunning(false)
    }

    override fun onDestroy() {
        stopService()
    }

    /**
     * Sends a message to the peer
     * @param server the client to which the message will be sent
     * @param message the message being sent to the client
     */
    private fun sendMessage(server: Peer, message: String) {
        var text: String? = message
        if (serverConnections.containsKey(server.ipAddress)) {
            if (server.isSecure) {
                // encrypt message
                val key = RSAEncryption.getPublicKeyFromString(
                    server.keyStore.getString(PeerStatusKeys.PUBLIC_KEY)
                )
                if (key != null) {
                    text = encryptMessage(key, text)
                } else {
                    Logger.w(this.javaClass.name, "Missing the server's public key")
                    text = SocketMessages.MSG_EXCEPTION
                }
            }
            serverConnections[server.ipAddress]?.write(text)
        }
    }

    /**
     * Requests a list of projects from the server
     * @param server the server that will give the project list
     * @param preferredLanguages the languages preferred by the client
     */
    fun requestProjectList(server: Peer, preferredLanguages: List<String?>) {
        val languagesJson = JSONArray()
        for (l in preferredLanguages) {
            languagesJson.put(l)
        }
        sendMessage(server, SocketMessages.MSG_PROJECT_LIST + ":" + languagesJson)
    }

    /**
     * Requests a target translation from the server
     * @param server
     * @param targetTranslationSlug
     */
    fun requestTargetTranslation(server: Peer, targetTranslationSlug: String?) {
        val json = JSONObject()
        try {
            json.put("target_translation_id", targetTranslationSlug)
            val request = Request(Request.Type.TargetTranslation, json)
            sendRequest(server, request)
        } catch (e: JSONException) {
            listener?.onClientServiceError(e)
        }
    }

    /**
     * Handles the initial handshake and authorization
     * @param server
     * @param message
     */
    private fun onMessageReceived(server: Peer, message: String) {
        var text: String? = message
        if (server.isSecure && server.hasIdentity()) {
            text = decryptMessage(privateKey, text)
            if (text != null) {
                try {
                    val request = Request.parse(text)
                    onRequestReceived(server, request)
                } catch (e: JSONException) {
                    if (listener != null) {
                        listener!!.onClientServiceError(e)
                    } else {
                        Logger.e(this.javaClass.name, "Failed to parse request", e)
                    }
                }
            } else {
                listener?.onClientServiceError(Exception("Message decryption failed"))
            }
        } else if (!server.isSecure) {
            // receive the key
            try {
                val json = JSONObject(text!!)
                server.keyStore.add(PeerStatusKeys.PUBLIC_KEY, json.getString("key"))
                server.setIsSecure(true)
            } catch (e: JSONException) {
                Logger.w(this.javaClass.name, "Invalid request: $text", e)
                //                sendMessage(server, SocketMessages.MSG_INVALID_REQUEST);
            } catch (e: NullPointerException) {
                Logger.w(this.javaClass.name, "Failed to parse request", e)
            }

            // send public key
            try {
                val json = JSONObject()
                json.put("key", publicKey)
                // TRICKY: manually write to server so we don't encrypt it
                if (serverConnections.containsKey(server.ipAddress)) {
                    serverConnections[server.ipAddress]?.write(json.toString())
                }
            } catch (e: JSONException) {
                listener?.onClientServiceError(e)
            }

            // send identity
            if (server.isSecure) {
                try {
                    val json = JSONObject()
                    json.put("name", deviceAlias)
                    if (isTablet) {
                        json.put("device", "tablet")
                    } else {
                        json.put("device", "phone")
                    }
                    val md = MessageDigest.getInstance("SHA-256")
                    md.update(udid().toByteArray(charset("UTF-8")))
                    val digest = md.digest()
                    val bigInt = BigInteger(1, digest)
                    val hash = bigInt.toString()
                    json.put("id", hash)
                    sendMessage(server, json.toString())
                } catch (e: Exception) {
                    Logger.w(this.javaClass.name, "Failed to prepare response ", e)
                    listener?.onClientServiceError(e)
                }
            }
        } else if (!server.hasIdentity()) {
            // receive identity
            text = decryptMessage(privateKey, text)
            try {
                val json = JSONObject(text!!)
                server.name = json.getString("name")
                server.device = json.getString("device")
                server.id = json.getString("id")
                server.setHasIdentity(true)
                listener?.onServerConnectionChanged(server)
            } catch (e: JSONException) {
                Logger.w(this.javaClass.name, "Invalid request: $text", e)
            } catch (e: NullPointerException) {
                Logger.w(this.javaClass.name, "Failed to parse request", e)
            }
        }
    }

    /**
     * Sends a request to a peer.
     * Requests are stored for reference when the client responds to the request
     * @param client
     * @param request
     */
    private fun sendRequest(client: Peer, request: Request) {
        if (serverConnections.containsKey(client.ipAddress) && client.isSecure) {
            // remember request
            requests[request.uuid] = request
            // send request
            sendMessage(client, request.toString())
        }
    }

    /**
     * Handles commands sent from the server
     * @param server
     * @param request
     */
    private fun onRequestReceived(server: Peer, request: Request) {
        val contextJson = request.context

        when (request.type) {
            Request.Type.AlertTargetTranslation -> queueRequest(server, request)
            Request.Type.TargetTranslation -> if (requests.containsKey(request.uuid)) {
                requests.remove(request.uuid)
                // receive file download details
                val port: Int
                val size: Long
                val name: String
                try {
                    port = contextJson.getInt("port")
                    size = contextJson.getLong("size")
                    name = contextJson.getString("name")
                } catch (e: JSONException) {
                    if (listener != null) {
                        listener?.onClientServiceError(e)
                    } else {
                        Logger.e(this.javaClass.name, "Invalid context", e)
                    }
                    return
                }
                // open download socket
                openReadSocket(server, port, object : OnSocketEventListener {
                    override fun onOpen(connection: Connection) {
                        connection.setOnCloseListener {
                            listener?.onClientServiceError(
                                Exception("Socket was closed before download completed")
                            )
                        }

                        var file: File? = null
                        try {
                            file = File.createTempFile("p2p", name)
                            // download archive
                            val inputStream = DataInputStream(connection.socket.getInputStream())
                            file.parentFile.mkdirs()
                            file.createNewFile()
                            val out: OutputStream = FileOutputStream(file.absolutePath)
                            val buffer = ByteArray(8 * 1024)
                            var totalCount = 0
                            var count: Int
                            while ((inputStream.read(buffer).also { count = it }) > 0) {
                                totalCount += count
                                server.keyStore.add(
                                    PeerStatusKeys.PROGRESS,
                                    totalCount / (size.toInt()) * 100
                                )
                                listener?.onServerConnectionChanged(server)
                                out.write(buffer, 0, count)
                            }
                            server.keyStore.add(PeerStatusKeys.PROGRESS, 0)
                            listener?.onServerConnectionChanged(server)
                            out.close()
                            inputStream.close()

                            // import the target translation
                            // TODO: 11/23/2015 perform a diff first
                            try {
                                val results = importProjects.importProject(file)
                                listener?.onReceivedTargetTranslations(server, results)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            file.delete()
                        } catch (e: IOException) {
                            Logger.e(this.javaClass.name, "Failed to download the file", e)
                            file?.delete()
                            listener?.onClientServiceError(e)
                        }
                    }
                })
            } else {
                // the server is trying to send the target translation without asking
                // TODO: 12/1/2015 accept according to user configuration
            }

            else -> Logger.i(
                this.javaClass.name,
                "received invalid request from " + server.ipAddress + ": " + request.toString()
            )
        }
    }

    /**
     * Queues a request to be reviewed by the user
     *
     * @param server
     * @param request
     */
    private fun queueRequest(server: Peer, request: Request) {
        server.queueRequest(request)
        listener?.onReceivedRequest(server, request)
    }

    /**
     * Interface for communication with service clients.
     */
    interface OnClientEventListener {
        fun onClientServiceReady()
        fun onServerConnectionLost(peer: Peer)
        fun onServerConnectionChanged(peer: Peer)
        fun onClientServiceError(e: Throwable)

        //        void onReceivedProjectList(Peer server, Model[] models);
        //        void onReceivedProject(Peer server, ProjectImport[] importStatuses);
        fun onReceivedTargetTranslations(server: Peer, results: ImportResults?)
        fun onReceivedRequest(peer: Peer, request: Request)
    }

    /**
     * Class to retrieve instance of service
     */
    inner class LocalBinder : Binder() {
        val serviceInstance: ClientService
            get() = this@ClientService
    }

    /**
     * Manages a single server connection on it's own thread
     */
    private inner class ServerThread(private val server: Peer) : Runnable {
        private var connection: Connection? = null

        override fun run() {
            // set up sockets
            try {
                val serverAddress = InetAddress.getByName(server.ipAddress)
                connection = Connection(Socket(serverAddress, server.port)).apply {
                    setOnCloseListener {
                        Thread.currentThread().interrupt()
                    }
                }
                // we store references to all connections so we can access them later
                if (!serverConnections.containsKey(connection!!.ipAddress)) {
                    addPeer(server)
                    serverConnections[connection!!.ipAddress] = connection!!
                } else {
                    // we already have a connection to this server
                    connection!!.close()
                    return
                }
            } catch (e: Exception) {
                // the connection could not be established
                connection?.close()
                listener?.onClientServiceError(e)
                return
            }

            // begin listening to server
            while (!Thread.currentThread().isInterrupted) {
                val message = connection!!.readLine()
                if (message == null) {
                    Thread.currentThread().interrupt()
                } else {
                    onMessageReceived(server, message)
                }
            }
            // close the connection
            connection?.close()
            // remove all instances of the peer
            if (serverConnections.containsKey(connection!!.ipAddress)) {
                serverConnections.remove(connection!!.ipAddress)
            }
            removePeer(server)
            listener?.onServerConnectionLost(server)
        }
    }

    companion object {
        private const val PARAM_PUBLIC_KEY = "param_public_key"
        private const val PARAM_PRIVATE_KEY = "param_private_key"
        private const val PARAM_DEVICE_ALIAS = "param_device_alias"

        /**
         * Checks if the service is currently running
         * @return
         */
        var isRunning: Boolean = false
            private set
    }
}
