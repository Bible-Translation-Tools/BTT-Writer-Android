package com.door43.translationstudio.services

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Timer
import java.util.TimerTask

/**
 * Broadcasts services provided by this device on the network
 */
class BroadcastService : NetworkService() {
    private val binder: IBinder = LocalBinder()
    private lateinit var socket: DatagramSocket
    private var timer: Timer? = null

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

    override fun onCreate() {
        Logger.i(this.javaClass.name, "Starting broadcaster")
        timer = Timer()
        try {
            socket = DatagramSocket().apply {
                broadcast = true
            }
        } catch (e: SocketException) {
            // TODO: notify app
            Logger.e(this.javaClass.name, "Failed to start the broadcaster", e)
            stopService()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startid: Int): Int {
        if (intent != null) {
            val args = intent.extras
            if (args != null) {
                setRunning(true)
                val udpPort = args.getInt(PARAM_BROADCAST_PORT)
                val serviceTCPPort = args.getInt(PARAM_SERVICE_PORT)
                val broadcastFrequency = args.getInt(PARAM_FREQUENCY, 2000)

                val json = JSONObject()
                try {
                    json.put("version", TS_PROTOCOL_VERSION)
                    json.put("port", serviceTCPPort)
                } catch (e: JSONException) {
                    // TODO: 11/24/2015 notify app
                    Logger.e(this.javaClass.name, "Failed to prepare the broadcast payload", e)
                    stopService()
                    return START_NOT_STICKY
                }

                val data = json.toString()

                // prepare packet
                if (data.length > 1024) {
                    // TODO: notify app
                    Logger.w(
                        this.javaClass.name,
                        "The broadcast data cannot be longer than 1024 bytes"
                    )
                    stopService()
                    return START_NOT_STICKY
                }

                val ipAddress: InetAddress
                try {
                    ipAddress = broadcastAddress
                } catch (e: UnknownHostException) {
                    // TODO: notify app
                    Logger.e(this.javaClass.name, "Failed to get the broadcast ip address", e)
                    stopService()
                    return START_NOT_STICKY
                }
                val packet = DatagramPacket(data.toByteArray(), data.length, ipAddress, udpPort)

                // schedule broadcast
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        try {
                            socket.send(packet)
                        } catch (e: IOException) {
                            Logger.e(this.javaClass.name, "Failed to send the broadcast packet", e)
                        }
                    }
                }, 0, broadcastFrequency.toLong())
                return START_STICKY
            }
        }
        Logger.w(this.javaClass.name, "The broadcaster requires arguments to operate correctly")
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
        timer?.cancel()
        socket.close()
        setRunning(false)
        Logger.i(this.javaClass.name, "Stopping broadcaster")
    }

    /**
     * Class to retrieve instance of service
     */
    inner class LocalBinder : Binder() {
        val serviceInstance: BroadcastService
            get() = this@BroadcastService
    }

    companion object {
        const val PARAM_BROADCAST_PORT: String = "param_broadcast_udp_port"
        const val PARAM_SERVICE_PORT: String = "param_service_tcp_port"
        const val PARAM_FREQUENCY: String = "param_broadcast_frequency"
        const val TS_PROTOCOL_VERSION: Int = 2

        /**
         * Checks if the service is currently running
         * @return
         */
        var isRunning: Boolean = false
            private set
    }
}