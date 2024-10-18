package com.door43.translationstudio.services

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.door43.translationstudio.network.BroadcastListenerRunnable
import com.door43.translationstudio.network.BroadcastListenerRunnable.OnBroadcastListenerEventListener
import com.door43.translationstudio.network.Peer
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.tools.logger.Logger
import java.util.Timer
import java.util.TimerTask

/**
 * This class listens for services being broadcast on the local network.
 * Notifications are fired whenever a service becomes or is no longer available.
 */
class BroadcastListenerService : NetworkService() {
    private val binder: IBinder = LocalBinder()
    private var broadcastListenerThread: Thread? = null
    private var broadcastListenerRunnable: BroadcastListenerRunnable? = null
    private var listener: Callbacks? = null
    private var cleanupTimer: Timer? = null

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

    fun registerCallback(callback: Callbacks?) {
        listener = callback
    }

    override fun onCreate() {
        Logger.i(this.javaClass.name, "Starting broadcast listener")
        cleanupTimer = Timer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startid: Int): Int {
        if (intent != null) {
            val args = intent.extras
            if (args != null && args.containsKey(PARAM_BROADCAST_PORT)) {
                val UDPPort = args.getInt(PARAM_BROADCAST_PORT)
                val serverTTL = args.getInt(PARAM_SERVER_TTL, 10000)
                val refreshFrequency = args.getInt(PARAM_REFRESH_FREQUENCY, 5000)
                // listener thread
                broadcastListenerRunnable =
                    BroadcastListenerRunnable(UDPPort, object : OnBroadcastListenerEventListener {
                        override fun onError(e: Exception) {
                            Logger.e(
                                BroadcastListenerService::class.java.name,
                                "Broadcast listener encountered an exception",
                                e
                            )
                        }

                        override fun onMessageReceived(message: String, senderIP: String) {
                            val version: Int
                            val port: Int
                            try {
                                val json = JSONObject(message)
                                version = json.getInt("version")
                                port = json.getInt("port")
                            } catch (e: JSONException) {
                                Logger.w(
                                    BroadcastListenerService::class.java.name,
                                    "Invalid message format $message",
                                    e
                                )
                                return
                            }

                            // validate protocol version
                            if (version == BroadcastService.TS_PROTOCOL_VERSION) {
                                val p = Peer(senderIP, port, "tS", version)
                                if (addPeer(p)) {
                                    listener?.onFoundServer(p)
                                }
                            } else {
                                Logger.w(
                                    BroadcastListenerService::class.java.name,
                                    "Unsupported tS protocol version $version"
                                )
                            }
                        }
                    })
                broadcastListenerThread = Thread(broadcastListenerRunnable).apply {
                    start()
                }
                // cleanup task
                cleanupTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        val connectedPeers = peers
                        for (p in connectedPeers) {
                            if (System.currentTimeMillis() - p.lastSeenAt > serverTTL) {
                                removePeer(p)
                                listener?.onLostServer(p)
                            }
                        }
                    }
                }, 0, refreshFrequency.toLong())
                setRunning(true)
                return START_STICKY
            }
        }
        Logger.e(this.javaClass.name, "Broadcast listener service requires arguments")
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
        broadcastListenerThread?.interrupt()
        broadcastListenerRunnable?.stop()
        setRunning(false)
        Logger.i(this.javaClass.name, "Stopping broadcast listener")
    }

    interface Callbacks {
        fun onFoundServer(server: Peer?)
        @Deprecated("")
        fun onLostServer(server: Peer?)
    }

    /**
     * Class to retrieve instance of service
     */
    inner class LocalBinder : Binder() {
        val serviceInstance: BroadcastListenerService
            get() = this@BroadcastListenerService
    }

    companion object {
        const val PARAM_SERVER_TTL: String = "param_server_ttl"
        const val PARAM_REFRESH_FREQUENCY: String = "param_refresh_frequency"
        const val PARAM_BROADCAST_PORT: String = "param_broadcast_udp_port"

        /**
         * Checks if the service is currently running
         * @return
         */
        var isRunning: Boolean = false
            private set
    }
}
