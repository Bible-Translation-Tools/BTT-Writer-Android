package com.door43.translationstudio.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentDevicePeerListItemBinding
import com.door43.translationstudio.network.Peer
import com.door43.translationstudio.services.PeerStatusKeys
import com.door43.translationstudio.services.Request
import com.facebook.rebound.SimpleSpringListener
import com.facebook.rebound.Spring
import com.facebook.rebound.SpringSystem

/**
 * Created by joel on 12/11/2014.
 */
class PeerAdapter : BaseAdapter() {
    private val peers = arrayListOf<Peer>()
    private var animateNotification = BooleanArray(0)

    /**
     * Updates the peer list
     * @param peerList
     */
    fun setPeers(peerList: ArrayList<Peer>) {
        peers.clear()
        peers.addAll(peerList)
        animateNotification = BooleanArray(peerList.size)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return peers.size
    }

    override fun getItem(i: Int): Peer {
        return peers[i]
    }

    override fun getItemId(i: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val peer = getItem(position)
        val binding: FragmentDevicePeerListItemBinding
        val holder: ViewHolder

        if (convertView == null) {
            binding = FragmentDevicePeerListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            holder = ViewHolder(binding)
        } else {
            holder = convertView.tag as ViewHolder
        }

        val deviceNameView = holder.binding.ipAddressText
        val ipAddressView = holder.binding.ipAddress
        val favoriteIcon = holder.binding.favorite
        val notificationIcon = holder.binding.notification
        val progressBar = holder.binding.progressBar
        val deviceIcon = holder.binding.peerIcon

        // name
        deviceNameView.text = peer.name
        ipAddressView.text = peer.ipAddress

        // device type
        if (peer.device == "tablet") {
            deviceIcon.setBackgroundResource(R.drawable.ic_tablet_android_black_24dp)
        } else if (peer.device == "phone") {
            deviceIcon.setBackgroundResource(R.drawable.ic_phone_android_black_24dp)
        } else {
            deviceIcon.setBackgroundResource(R.drawable.ic_devices_other_black_24dp)
        }

        // progress bar
        val isWaiting = peer.keyStore.getBool(PeerStatusKeys.WAITING)
        val progress = peer.keyStore.getInt(PeerStatusKeys.PROGRESS)
        progressBar.isIndeterminate = isWaiting
        progressBar.progress = progress
        if (!isWaiting && progress == 0) {
            progressBar.visibility = View.GONE
            deviceIcon.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.VISIBLE
            deviceIcon.visibility = View.GONE
        }

        // requests
        favoriteIcon.visibility = View.GONE
        if (peer.requests.isNotEmpty()) {
            notificationIcon.visibility = View.VISIBLE
        } else {
            notificationIcon.visibility = View.GONE
        }

        // animate notification
        if (animateNotification[position]) {
            animateNotification[position] = false
            val springSystem = SpringSystem.create()
            val spring = springSystem.createSpring()
            val springConfig = spring.springConfig
            springConfig.friction = 10.0
            springConfig.tension = 440.0
            spring.addListener(object : SimpleSpringListener() {
                override fun onSpringUpdate(spring: Spring) {
                    val value = spring.currentValue.toFloat()
                    val scale = 1f - (value * 0.5f)
                    notificationIcon.scaleX = scale
                    notificationIcon.scaleY = scale
                }
            })
            spring.setCurrentValue(-1.0, true)
            spring.setEndValue(0.0)
        }

        return holder.binding.root
    }

    /**
     * Indicates that a request from the peer is awaiting approval
     * @param peer
     * @param request
     */
    fun newRequestAlert(peer: Peer, request: Request?) {
        // schedule an animation for the notification icon
        val index = peers.indexOf(peer)
        if (index >= 0 && index < animateNotification.size) {
            animateNotification[index] = true
        }
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: FragmentDevicePeerListItemBinding) {
        init {
            binding.root.tag = this
        }
    }
}
