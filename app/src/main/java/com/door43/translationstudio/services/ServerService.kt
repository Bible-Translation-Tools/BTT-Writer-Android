package com.door43.translationstudio.services

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.isTablet
import com.door43.translationstudio.App.Companion.udid
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.network.Connection
import com.door43.translationstudio.network.Peer
import com.door43.usecases.ExportProjects
import com.door43.util.RSAEncryption
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.PrivateKey
import java.util.UUID
import javax.inject.Inject

/**
 * This class provides an exporting service (effectively a server) from which
 * other devices may browse and retrieve translations
 */
@AndroidEntryPoint
class ServerService : NetworkService() {
    @Inject lateinit var translator: Translator
    @Inject lateinit var library: Door43Client
    @Inject lateinit var exportProjects: ExportProjects
    @Inject lateinit var profile: Profile
    @Inject lateinit var directoryProvider: IDirectoryProvider

    private val binder: IBinder = LocalBinder()
    private var listener: OnServerEventListener? = null
    private var port = 0
    private var serverThread: Thread? = null
    private val clientConnections: MutableMap<String, Connection> = HashMap()
    private var privateKey: PrivateKey? = null
    private var publicKey: String? = null
    private var serverSocket: ServerSocket? = null
    private var deviceAlias: String? = null
    private val requests: MutableMap<UUID, Request> = HashMap()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * Sets whether or not the service is running
     * @param running
     */
    private fun setRunning(running: Boolean) {
        isRunning = running
    }

    fun setOnServerEventListener(callback: OnServerEventListener?) {
        listener = callback
        if (isRunning) {
            listener?.onServerServiceReady(port)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startid: Int): Int {
        if (intent != null) {
            val args = intent.extras
            if (args != null && args.containsKey(PARAM_DEVICE_ALIAS)) {
                privateKey = getPrivateKey(directoryProvider.p2pPrivateKey)
                publicKey = readPublicKey(directoryProvider.p2pPublicKey)
                deviceAlias = args.getString(PARAM_DEVICE_ALIAS)
                serverThread = Thread(ServerRunnable()).apply {
                    start()
                }
                return START_STICKY
            }
        }
        Logger.e(this.javaClass.name, "Export service requires arguments")
        stopService()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopService()
    }

    /**
     * Stops the service
     */
    private fun stopService() {
        Logger.i(this.javaClass.name, "Stopping export service")
        serverThread?.interrupt()
        if (serverSocket != null) {
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                Logger.e(this.javaClass.name, "Failed to close server socket", e)
            }
        }
        val clients = clientConnections.values
        for (c in clients) {
            c.close()
        }
        clientConnections.clear()
        setRunning(false)
    }

    /**
     * Sends a message to the peer
     * @param client the client to which the message will be sent
     * @param message the message being sent to the client
     */
    private fun sendMessage(client: Peer, message: String) {
        var text: String? = message
        if (clientConnections.containsKey(client.ipAddress)) {
            if (client.isSecure) {
                // encrypt message
                val key = RSAEncryption.getPublicKeyFromString(
                    client.keyStore.getString(PeerStatusKeys.PUBLIC_KEY)
                )
                if (key != null) {
                    text = encryptMessage(key, text)
                } else {
                    Logger.w(this.javaClass.name, "Missing the client's public key")
                    text = SocketMessages.MSG_EXCEPTION
                }
            }
            clientConnections[client.ipAddress]?.write(text)
        }
    }

    /**
     * Sends a request to a peer.
     * Requests are stored for reference when the client responds to the request
     * @param client
     * @param request
     */
    private fun sendRequest(client: Peer, request: Request) {
        if (clientConnections.containsKey(client.ipAddress) && client.isSecure) {
            // remember request
            requests[request.uuid] = request
            // send request
            sendMessage(client, request.toString())
        }
    }

    /**
     * Accepts a client connection
     * @param peer
     */
    fun acceptConnection(peer: Peer) {
        peer.setIsAuthorized(true)

        // send public key
        try {
            val json = JSONObject()
            json.put("key", publicKey)
            // TRICKY: we manually write to peer so we don't encrypt it
            if (clientConnections.containsKey(peer.ipAddress)) {
                clientConnections[peer.ipAddress]?.write(json.toString())
            }
        } catch (e: JSONException) {
            Logger.w(this.javaClass.name, "Failed to prepare response ", e)
            listener?.onServerServiceError(e)
        }
    }

    /**
     * Handles the initial handshake and authorization
     * @param client
     * @param message
     */
    private fun onMessageReceived(client: Peer, message: String) {
        var text: String? = message
        if (client.isAuthorized) {
            if (client.isSecure && client.hasIdentity()) {
                text = decryptMessage(privateKey, text)
                if (text != null) {
                    try {
                        val request = Request.parse(text)
                        onRequestReceived(client, request)
                    } catch (e: JSONException) {
                        if (listener != null) {
                            listener?.onServerServiceError(e)
                        } else {
                            Logger.e(this.javaClass.name, "Failed to parse request", e)
                        }
                    }
                } else if (listener != null) {
                    listener?.onServerServiceError(Exception("Message decryption failed"))
                }
            } else if (!client.isSecure) {
                // receive the key
                try {
                    val json = JSONObject(text!!)
                    client.keyStore.add(PeerStatusKeys.PUBLIC_KEY, json.getString("key"))
                    client.setIsSecure(true)
                } catch (e: JSONException) {
                    Logger.w(this.javaClass.name, "Invalid request: $text", e)
                } catch (e: NullPointerException) {
                    Logger.w(this.javaClass.name, "Failed to parse request", e)
                }

                // send identity
                if (client.isSecure) {
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
                        sendMessage(client, json.toString())
                    } catch (e: Exception) {
                        Logger.w(this.javaClass.name, "Failed to prepare response ", e)
                        listener?.onServerServiceError(e)
                    }
                }
            } else if (!client.hasIdentity()) {
                // receive identity
                text = decryptMessage(privateKey, text)
                try {
                    val json = JSONObject(text!!)
                    client.name = json.getString("name")
                    client.device = json.getString("device")
                    client.id = json.getString("id")
                    client.setHasIdentity(true)
                    if (listener != null) {
                        listener!!.onClientChanged(client)
                    }
                } catch (e: JSONException) {
                    Logger.w(this.javaClass.name, "Invalid request: $text", e)
                } catch (e: NullPointerException) {
                    Logger.w(this.javaClass.name, "Failed to parse request", e)
                }
            }
        } else {
            Logger.w(this.javaClass.name, "The client is not authorized")
            sendMessage(client, SocketMessages.MSG_AUTHORIZATION_ERROR)
        }
    }

    /**
     * Handles commands sent from the client
     * @param client
     * @param request
     */
    private fun onRequestReceived(client: Peer, request: Request) {
        val contextJson = request.context

        when (request.type) {
            Request.Type.TargetTranslation -> {
                val targetTranslationSlug = try {
                    contextJson.getString("target_translation_id")
                } catch (e: JSONException) {
                    Logger.e(this.javaClass.name, "invalid context", e)
                    return
                }
                val exportFile = try {
                    File.createTempFile(
                        targetTranslationSlug,
                        "." + Translator.TSTUDIO_EXTENSION
                    )
                } catch (e: IOException) {
                    Logger.e(this.javaClass.name, "Could not create a temp file", e)
                    return
                }

                val targetTranslation = translator.getTargetTranslation(targetTranslationSlug)
                if (targetTranslation != null) {
                    try {
                        targetTranslation.setDefaultContributor(profile.nativeSpeaker)
                        exportProjects.exportProject(targetTranslation, exportFile)
                        if (exportFile.exists()) {
                            var targetTranslationContext: JSONObject

                            openWriteSocket(object : OnSocketEventListener {
                                override fun onOpen(connection: Connection) {
                                    try {
                                        val out = DataOutputStream(connection.socket.getOutputStream())
                                        val inputStream = DataInputStream(
                                            BufferedInputStream(FileInputStream(exportFile))
                                        )
                                        val buffer = ByteArray(8 * 1024)
                                        var count: Int
                                        while ((inputStream.read(buffer).also { count = it }) > 0) {
                                            out.write(buffer, 0, count)
                                        }
                                        out.close()
                                        inputStream.close()
                                    } catch (e: IOException) {
                                        Logger.e(
                                            ServerService::class.java.name,
                                            "Failed to send the target translation",
                                            e
                                        )
                                    }
                                }
                            }).use { fileSocket ->
                                fileSocket?.let { socket ->
                                    // send file details
                                    targetTranslationContext = JSONObject()
                                    targetTranslationContext.put("port", socket.localPort)
                                    targetTranslationContext.put("name", exportFile.name)
                                    targetTranslationContext.put("size", exportFile.length())
                                    val reply = request.makeReply(targetTranslationContext)
                                    sendRequest(client, reply)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // export failed
                        Logger.e(this.javaClass.name, "Failed to export the archive", e)
                    }
                } else {
                    // we don't have it
                }
            }

            Request.Type.TargetTranslationList -> {
                Logger.i(
                    this.javaClass.name,
                    "received project list request from " + client.ipAddress
                )

                // send the project list to the client
                // TODO: we shouldn't use the project manager here because this may be running in the background (eventually)

                // read preferred source languages (for better readability on the client)
////                List<Language> preferredLanguages = new ArrayList<>();
//                try {
////                    JSONArray preferredLanguagesJson = contextJson.getJSONArray("preferred_source_language_ids");
////                    for(int i = 0; i < preferredLanguagesJson.length(); i ++) {
////                        Language lang =  null;//App.projectManager().getLanguage(preferredLanguagesJson.getString(i));
////                        if(lang != null) {
////                            preferredLanguages.add(lang);
////                        }
////                    }
//                } catch (JSONException e) {
//                    Logger.e(this.getClass().getName(), "failed to parse preferred language list", e);
//                }

                // generate project library
                // TODO: identifying the projects that have changes could be expensive if there are lots of clients and lots of projects. We might want to cache this
                val library: String? =
                    null //Sharing.generateLibrary(App.projectManager().getProjectSlugs(), preferredLanguages);

                sendMessage(client, SocketMessages.MSG_PROJECT_LIST + ":" + library)
            }

            else -> Logger.i(
                this.javaClass.name,
                "received invalid request from " + client.ipAddress + ": " + request.toString()
            )
        }
    }

    /**
     * Offers a target translation to the peer
     * @param client
     * @param targetTranslationSlug
     */
    fun offerTargetTranslation(
        client: Peer,
        sourceLanguageSlug: String?,
        targetTranslationSlug: String
    ) {
        val targetTranslation = translator.getTargetTranslation(targetTranslationSlug)
        if (targetTranslation != null) {
            try {
                val p = library.index.getProject(
                    sourceLanguageSlug,
                    targetTranslation.projectId,
                    true
                )
                val json = JSONObject()
                json.put("target_translation_id", targetTranslation.id)
                json.put("package_version", TargetTranslation.PACKAGE_VERSION)
                json.put("project_name", p.name)
                json.put("target_language_name", targetTranslation.targetLanguageName)
                json.put("progress", 0) // we don't use this right now
                val request = Request(Request.Type.AlertTargetTranslation, json)
                sendRequest(client, request)
            } catch (e: Exception) {
                listener?.onServerServiceError(e)
            }
        } else {
            // invalid target translation
            listener?.onServerServiceError(Exception("Invalid target translation: $targetTranslationSlug"))
        }
    }

    /**
     * Class to retrieve instance of service
     */
    inner class LocalBinder : Binder() {
        val serviceInstance: ServerService
            get() = this@ServerService
    }

    /**
     * Interface for communication with service clients.
     */
    interface OnServerEventListener {
        fun onServerServiceReady(port: Int)
        fun onClientConnected(peer: Peer?)
        fun onClientLost(peer: Peer?)
        fun onClientChanged(peer: Peer?)
        fun onServerServiceError(e: Throwable?)
    }

    /**
     * Manage the server instance on it's own thread
     */
    private inner class ServerRunnable : Runnable {
        override fun run() {
            var socket: Socket

            // set up sockets
            try {
                serverSocket = ServerSocket(0)
            } catch (e: Exception) {
                listener?.onServerServiceError(e)
                return
            }
            port = serverSocket!!.localPort

            listener?.onServerServiceReady(port)

            setRunning(true)

            // begin listening for connections
            while (!Thread.currentThread().isInterrupted) {
                try {
                    socket = serverSocket!!.accept()
                    val clientRunnable = ClientRunnable(socket)
                    Thread(clientRunnable).start()
                } catch (e: Exception) {
                    if (!Thread.currentThread().isInterrupted) {
                        Logger.e(this.javaClass.name, "failed to accept socket", e)
                    }
                }
            }
            try {
                serverSocket!!.close()
            } catch (e: Exception) {
                Logger.e(this.javaClass.name, "failed to shutdown the server socket", e)
            }
        }
    }

    /**
     * Manages a single client connection on it's own thread
     */
    private inner class ClientRunnable(clientSocket: Socket) : Runnable {
        private var connection: Connection? = null
        private val client: Peer

        init {
            // set up socket
            try {
                connection = Connection(clientSocket).apply {
                    setOnCloseListener {
                        Thread.currentThread().interrupt()
                    }
                    // we store a reference to all connections so we can access them later
                    clientConnections[ipAddress] = this
                }
            } catch (e: Exception) {
                listener?.onServerServiceError(e)
                Thread.currentThread().interrupt()
            }
            // create a new peer
            client = Peer(clientSocket.inetAddress.toString().replace("/", ""), clientSocket.port)
            if (addPeer(client)) {
                listener?.onClientConnected(client)
            }
        }

        override fun run() {
            while (!Thread.currentThread().isInterrupted) {
                val message = connection!!.readLine()
                if (message == null) {
                    Thread.currentThread().interrupt()
                } else {
                    onMessageReceived(client, message)
                }
            }
            // close the connection
            connection?.close()
            // remove all instances of the peer
            clientConnections.remove(connection!!.ipAddress)
            removePeer(client)
            listener?.onClientLost(client)
        }
    }

    companion object {
        const val PARAM_PRIVATE_KEY: String = "param_private_key"
        const val PARAM_PUBLIC_KEY: String = "param_public_key"
        const val PARAM_DEVICE_ALIAS: String = "param_device_alias"

        /**
         * Checks if the service is currently running
         * @return
         */
        var isRunning: Boolean = false
            private set
    }
}
