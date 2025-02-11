package com.door43.translationstudio.ui.dialogs

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.door43.translationstudio.App.Companion.deviceNetworkAlias
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.MergeConflictsHandler.OnMergeConflictListener
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.DialogShareWithPeerBinding
import com.door43.translationstudio.network.Peer
import com.door43.translationstudio.services.BroadcastListenerService
import com.door43.translationstudio.services.BroadcastService
import com.door43.translationstudio.services.ClientService
import com.door43.translationstudio.services.ClientService.OnClientEventListener
import com.door43.translationstudio.services.Request
import com.door43.translationstudio.services.ServerService
import com.door43.translationstudio.services.ServerService.OnServerEventListener
import com.door43.translationstudio.ui.home.HomeActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.viewmodels.ExportViewModel
import com.door43.usecases.ImportProjects
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONException
import org.unfoldingword.tools.logger.Logger
import java.security.InvalidParameterException
import javax.inject.Inject

@AndroidEntryPoint
class ShareWithPeerDialog : DialogFragment(), OnServerEventListener,
    BroadcastListenerService.Callbacks, OnClientEventListener {

    @Inject lateinit var translator: Translator

    private var serverIntent: Intent? = null
    private var clientIntent: Intent? = null
    private var broadcastIntent: Intent? = null
    private var listenerIntent: Intent? = null

    private var operationMode = 0
    private var targetTranslationId: String? = null
    private var shutDownServices = true
    private var deviceAlias: String? = null
    private var dialogShown: DialogShown? = DialogShown.NONE
    private lateinit var targetTranslation: TargetTranslation
    private lateinit var adapter: PeerAdapter

    private val viewModel: ExportViewModel by viewModels()

    private var _binding: DialogShareWithPeerBinding? = null
    private val binding get() = _binding!!

    private var clientService: ClientService? = null
    private val clientConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ClientService.LocalBinder
            clientService = binder.serviceInstance
            clientService!!.setOnClientEventListener(this@ShareWithPeerDialog)
            Logger.i(ShareWithPeerDialog::class.java.name, "Connected to import service")
            val hand = Handler(Looper.getMainLooper())
            hand.post { updatePeerList(clientService!!.peers) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clientService!!.setOnClientEventListener(null)
            Logger.i(ShareWithPeerDialog::class.java.name, "Disconnected from import service")
            // TODO: notify fragment that service was dropped.
        }
    }

    private var serverService: ServerService? = null
    private val serverConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ServerService.LocalBinder
            serverService = binder.serviceInstance
            serverService!!.setOnServerEventListener(this@ShareWithPeerDialog)
            Logger.i(ShareWithPeerDialog::class.java.name, "Connected to export service")
            val hand = Handler(Looper.getMainLooper())
            hand.post { updatePeerList(serverService!!.peers) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serverService!!.setOnServerEventListener(null)
            Logger.i(ShareWithPeerDialog::class.java.name, "Disconnected from export service")
            // TODO: notify fragment that service was dropped.
        }
    }

    // TODO: 11/20/2015 we don't actually need to bind to the broadcast service
    private var broadcastService: BroadcastService? = null
    private val broadcastConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as BroadcastService.LocalBinder
            broadcastService = binder.serviceInstance
            Logger.i(ShareWithPeerDialog::class.java.name, "Connected to broadcast service")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Logger.i(ShareWithPeerDialog::class.java.name, "Disconnected from broadcast service")
            // TODO: notify fragment that service was dropped.
        }
    }

    private var listenerService: BroadcastListenerService? = null
    private val listenerConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as BroadcastListenerService.LocalBinder
            listenerService = binder.serviceInstance
            listenerService!!.registerCallback(this@ShareWithPeerDialog)
            Logger.i(
                ShareWithPeerDialog::class.java.name,
                "Connected to broadcast listener service"
            )
        }

        override fun onServiceDisconnected(name: ComponentName) {
            listenerService!!.registerCallback(null)
            Logger.i(
                ShareWithPeerDialog::class.java.name,
                "Disconnected from broadcast listener service"
            )
            // TODO: notify fragment that service was dropped.
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogShareWithPeerBinding.inflate(inflater, container, false)

        val args = arguments
        if (args != null && args.containsKey(ARG_OPERATION_MODE) && args.containsKey(
                ARG_DEVICE_ALIAS
            )
        ) {
            operationMode = args.getInt(ARG_OPERATION_MODE, MODE_CLIENT)
            targetTranslationId = args.getString(ARG_TARGET_TRANSLATION, null)
            deviceAlias = args.getString(ARG_DEVICE_ALIAS, null)
            targetTranslationId?.let { viewModel.loadTargetTranslation(it) }

            if (deviceAlias == null) {
                throw InvalidParameterException("The device alias cannot be null")
            }
        } else {
            throw InvalidParameterException("Missing intent arguments")
        }

        setupObservers()

        adapter = PeerAdapter()

        with(binding) {
            if (operationMode == MODE_CLIENT) {
                title.text = resources.getString(R.string.import_from_device)
                targetTranslationTitle.text = ""
            }

            list.adapter = adapter
            list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val peer = adapter.getItem(position)
                if (operationMode == MODE_SERVER) {
                    // offer target translation to the client
                    val sourceTranslationSlugs = viewModel.getOpenSourceTranslations(
                        targetTranslationId
                    )
                    var sourceLanguageSlug = "en"
                    // try using their selected source first
                    if (sourceTranslationSlugs.isNotEmpty()) {
                        val t = viewModel.getTranslation(sourceTranslationSlugs[0])
                        if (t != null) {
                            sourceLanguageSlug = t.language.slug
                        }
                    } else {
                        // try using the next available source
                        val p = viewModel.getProject(targetTranslationId)
                        if (p != null) {
                            sourceLanguageSlug = p.languageSlug
                        }
                    }

                    Thread {
                        serverService?.offerTargetTranslation(
                            peer,
                            sourceLanguageSlug,
                            targetTranslationId!!
                        )
                    }.start()
                } else if (operationMode == MODE_CLIENT) {
                    // TODO: 12/1/2015 eventually provide a ui for viewing multiple different requests from this peer
                    // display request user
                    val requests = peer.requests
                    if (requests.isNotEmpty()) {
                        val request = requests[0]
                        if (request.type == Request.Type.AlertTargetTranslation) {
                            // TRICKY: for now we are just looking at one request at a time.
                            try {
                                val targetTranslationSlug =
                                    request.context.getString("target_translation_id")
                                val projectName = request.context.getString("project_name")
                                val targetLanguageName =
                                    request.context.getString("target_language_name")
                                val packageVersion = request.context.getInt("package_version")
                                if (packageVersion <= TargetTranslation.PACKAGE_VERSION) {
                                    AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                                        .setTitle(peer.name)
                                        .setMessage(
                                            String.format(
                                                resources.getString(R.string.confirm_import_target_translation),
                                                "$projectName - $targetLanguageName"
                                            )
                                        )
                                        .setPositiveButton(R.string.label_import) { dialog, which ->
                                            peer.dismissRequest(request)
                                            adapter.notifyDataSetChanged()
                                            Thread {
                                                clientService!!.requestTargetTranslation(
                                                    peer,
                                                    targetTranslationSlug
                                                )
                                            }.start()
                                            dialog.dismiss()
                                        }
                                        .setNegativeButton(R.string.dismiss) { dialog, _ ->
                                            peer.dismissRequest(request)
                                            adapter.notifyDataSetChanged()
                                            dialog.dismiss()
                                        }
                                        .show()
                                } else {
                                    // our app is to old to import this version of a target translation
                                    Logger.w(
                                        ShareWithPeerDialog::class.java.name,
                                        "Could not import target translation with package version " + TargetTranslation.PACKAGE_VERSION + ". Supported version is " + TargetTranslation.PACKAGE_VERSION
                                    )
                                    peer.dismissRequest(request)
                                    adapter.notifyDataSetChanged()
                                    AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                                        .setTitle(peer.name)
                                        .setMessage(
                                            String.format(
                                                resources.getString(R.string.error_importing_unsupported_target_translation),
                                                projectName,
                                                targetLanguageName,
                                                resources.getString(R.string.app_name)
                                            )
                                        )
                                        .setNeutralButton(R.string.dismiss, null)
                                        .show()
                                }
                            } catch (e: JSONException) {
                                peer.dismissRequest(request)
                                adapter.notifyDataSetChanged()
                                AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                                    .setTitle(peer.name)
                                    .setMessage(R.string.error)
                                    .setNeutralButton(R.string.dismiss, null)
                                    .show()
                                Logger.e(
                                    ShareWithPeerDialog::class.java.name,
                                    "Invalid request context",
                                    e
                                )
                            }
                        } else {
                            // we do not currently support other requests
                        }
                    }
                }
            }

            dismissButton.setOnClickListener { dismiss() }
        }

        if (savedInstanceState != null) {
            dialogShown = DialogShown.fromInt(
                savedInstanceState.getInt(
                    STATE_DIALOG_SHOWN,
                    DialogShown.NONE.value
                )
            )
            targetTranslationId = savedInstanceState.getString(STATE_DIALOG_TRANSLATION_ID, null)
        }

        restoreDialogs()
        return binding.root
    }

    private fun setupObservers() {
        with(viewModel) {
            translation.observe(this@ShareWithPeerDialog) {
                it?.let { translation ->
                    targetTranslation = translation
                    onTranslationLoaded()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onTranslationLoaded() {
        with(binding) {
            title.text = resources.getString(R.string.export_to_device)
            targetTranslationTitle.text = viewModel.getProjectTitle(targetTranslation)
        }
    }

    /**
     * restore the dialogs that were displayed before rotation
     */
    private fun restoreDialogs() {
        when (dialogShown) {
            DialogShown.MERGE_CONFLICT -> showMergeConflict(targetTranslationId)
            DialogShown.NONE -> {}
            else -> Logger.e(TAG, "Unsupported restore dialog: " + dialogShown.toString())
        }
    }

    override fun onStart() {
        super.onStart()
        shutDownServices = true

        if (operationMode == MODE_SERVER) {
            serverIntent = Intent(activity, ServerService::class.java)
            broadcastIntent = Intent(activity, BroadcastService::class.java)
            if (!ServerService.isRunning) {
                try {
                    initializeService(serverIntent)
                } catch (e: Exception) {
                    Logger.e(this.javaClass.name, "Failed to initialize the server service", e)
                    dismiss()
                }
            }
            requireActivity().bindService(
                serverIntent!!,
                serverConnection,
                Context.BIND_AUTO_CREATE
            )
        } else if (operationMode == MODE_CLIENT) {
            clientIntent = Intent(activity, ClientService::class.java)
            listenerIntent = Intent(activity, BroadcastListenerService::class.java)
            if (!ClientService.isRunning) {
                try {
                    initializeService(clientIntent)
                } catch (e: Exception) {
                    Logger.e(this.javaClass.name, "Failed to initialize the client service", e)
                    dismiss()
                }
            }
            requireActivity().bindService(
                clientIntent!!,
                clientConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    /**
     * Initializes the service intent
     * @param intent
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun initializeService(intent: Intent?) {
        viewModel.initializeP2PKeys()

        intent?.putExtra(ServerService.PARAM_DEVICE_ALIAS, deviceNetworkAlias)
        Logger.i(
            this.javaClass.name, "Starting service " + intent?.component?.className
        )
        requireActivity().startService(intent)
    }

    /**
     * Updates the peer list on the screen
     * @param peers
     */
    fun updatePeerList(peers: ArrayList<Peer>) {
        if (_binding == null) return
        with(binding) {
            if (peers.size == 0) {
                // display no peer notice
                list.visibility = View.GONE
                noPeersNotice.visibility = View.VISIBLE
            } else {
                // display peer list
                list.visibility = View.VISIBLE
                noPeersNotice.visibility = View.GONE
            }
            adapter.setPeers(peers)
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        shutDownServices = false
        out.putInt(STATE_DIALOG_SHOWN, dialogShown!!.value)
        out.putString(STATE_DIALOG_TRANSLATION_ID, targetTranslationId)
        super.onSaveInstanceState(out)
    }

    override fun onDestroy() {
        // unbind services
        try {
            requireActivity().unbindService(broadcastConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            requireActivity().unbindService(listenerConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            requireActivity().unbindService(serverConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            requireActivity().unbindService(clientConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // shut down services
        if (shutDownServices) {
            if (BroadcastService.isRunning && broadcastIntent != null) {
                if (!requireActivity().stopService(broadcastIntent)) {
                    Logger.w(
                        this.javaClass.name,
                        "Failed to stop service " + BroadcastService::class.java.name
                    )
                }
            }
            if (BroadcastListenerService.isRunning && listenerIntent != null) {
                if (!requireActivity().stopService(listenerIntent)) {
                    Logger.w(
                        this.javaClass.name,
                        "Failed to stop service " + BroadcastListenerService::class.java.name
                    )
                }
            }
            if (ServerService.isRunning && serverIntent != null) {
                if (!requireActivity().stopService(serverIntent)) {
                    Logger.w(
                        this.javaClass.name,
                        "Failed to stop service " + ServerService::class.java.name
                    )
                }
            }
            if (ClientService.isRunning && clientIntent != null) {
                if (!requireActivity().stopService(clientIntent)) {
                    Logger.w(
                        this.javaClass.name,
                        "Failed to stop service " + ClientService::class.java.name
                    )
                }
            }
        }
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onServerServiceReady(port: Int) {
        // begin broadcasting
        if (!BroadcastService.isRunning) {
            broadcastIntent!!.putExtra(BroadcastService.PARAM_BROADCAST_PORT, PORT_CLIENT_UDP)
            broadcastIntent!!.putExtra(BroadcastService.PARAM_SERVICE_PORT, port)
            broadcastIntent!!.putExtra(BroadcastService.PARAM_FREQUENCY, 2000)
            requireActivity().startService(broadcastIntent)
        }
        requireActivity().bindService(
            broadcastIntent!!,
            broadcastConnection,
            Context.BIND_AUTO_CREATE
        )
        val hand = Handler(Looper.getMainLooper())
        hand.post { updatePeerList(serverService!!.peers) }
    }

    override fun onClientConnected(peer: Peer?) {
        peer?.let { serverService?.acceptConnection(it) }
    }

    override fun onClientLost(peer: Peer?) {
        val hand = Handler(Looper.getMainLooper())
        hand.post { updatePeerList(serverService?.peers ?: arrayListOf()) }
    }

    override fun onClientChanged(peer: Peer?) {
        val hand = Handler(Looper.getMainLooper())
        hand.post { updatePeerList(serverService?.peers ?: arrayListOf()) }
    }

    override fun onServerServiceError(e: Throwable?) {
        e?.let {
            Toast.makeText(requireActivity(), it.message ?: "An error occurred.", Toast.LENGTH_SHORT).show()
            Logger.e(this.javaClass.name, "Server service encountered an exception: " + it.message, it)
        }
    }

    override fun onFoundServer(server: Peer?) {
        server?.let { clientService?.connectToServer(it) }
    }

    @Deprecated("")
    override fun onLostServer(server: Peer?) {
    }

    override fun onClientServiceReady() {
        // begin listening for servers
        if (!BroadcastListenerService.isRunning) {
            listenerIntent!!.putExtra(
                BroadcastListenerService.PARAM_BROADCAST_PORT,
                PORT_CLIENT_UDP
            )
            listenerIntent!!.putExtra(
                BroadcastListenerService.PARAM_REFRESH_FREQUENCY,
                REFRESH_FREQUENCY
            )
            listenerIntent!!.putExtra(BroadcastListenerService.PARAM_SERVER_TTL, SERVER_TTL)
            requireActivity().startService(listenerIntent)
        }
        requireActivity().bindService(
            listenerIntent!!,
            listenerConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onServerConnectionLost(peer: Peer) {
        val hand = Handler(Looper.getMainLooper())
        hand.post { updatePeerList(clientService!!.peers) }
    }

    override fun onServerConnectionChanged(peer: Peer) {
        val hand = Handler(Looper.getMainLooper())
        hand.post { updatePeerList(clientService!!.peers) }
    }

    override fun onClientServiceError(e: Throwable) {
        Logger.e(this.javaClass.name, "Client service encountered an exception: " + e.message, e)
    }

    override fun onReceivedTargetTranslations(
        server: Peer,
        results: ImportProjects.ImportResults?
    ) {
        if (results == null) return

        // build name list
        val name = viewModel.getTargetTranslationName(results.importedSlug)

        // notify user
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            if (results.isSuccess && results.mergeConflict) {
                MergeConflictsHandler.backgroundTestForConflictedChunks(
                    results.importedSlug,
                    translator,
                    object : OnMergeConflictListener {
                        override fun onNoMergeConflict(targetTranslationId: String) {
                            showShareSuccess(name)
                        }

                        override fun onMergeConflict(targetTranslationId: String) {
                            showMergeConflict(targetTranslationId)
                        }
                    })
            } else {
                showShareSuccess(name)
            }
        }
    }

    /**
     * show share success
     * @param name
     */
    private fun showShareSuccess(name: String) {
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.success)
            .setMessage(
                String.format(
                    resources.getString(R.string.success_import_target_translation),
                    name
                )
            )
            .setPositiveButton(R.string.dismiss, null)
            .show()
        // TODO: 12/1/2015 this is a bad hack
        (activity as? HomeActivity)?.loadTranslations()
    }

    fun showMergeConflict(targetTranslationID: String?) {
        dialogShown = DialogShown.MERGE_CONFLICT
        this.targetTranslationId = targetTranslationID
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.merge_conflict_title).setMessage(R.string.import_merge_conflict)
            .setPositiveButton(R.string.label_ok) { _, _ ->
                dialogShown = DialogShown.NONE
                doManualMerge()
            }.show()
    }

    private fun doManualMerge() {
        // ask parent activity to navigate to target translation review mode with merge filter on
        val intent = Intent(activity, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslationId)
        args.putBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, true)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)
        startActivity(intent)
        dismiss()
    }

    override fun onReceivedRequest(peer: Peer, request: Request) {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            adapter.newRequestAlert(peer, request)
        }
    }

    /**
     * for keeping track which dialog is being shown for orientation changes (not for DialogFragments)
     */
    enum class DialogShown(val value: Int) {
        NONE(0),
        MERGE_CONFLICT(1);

        companion object {
            fun fromInt(i: Int): DialogShown? {
                for (b in entries) {
                    if (b.value == i) {
                        return b
                    }
                }
                return null
            }
        }
    }

    companion object {
        val TAG: String = ShareWithPeerDialog::class.java.simpleName

        const val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        const val STATE_DIALOG_TRANSLATION_ID: String = "state_dialog_translationID"

        // TODO: 11/30/2015 get port from settings
        const val PORT_CLIENT_UDP = 9939
        const val REFRESH_FREQUENCY = 2000
        const val SERVER_TTL = 2000
        const val MODE_CLIENT: Int = 0
        const val MODE_SERVER: Int = 1
        const val ARG_DEVICE_ALIAS: String = "arg_device_alias"
        const val ARG_OPERATION_MODE: String = "arg_operation_mode"
        const val ARG_TARGET_TRANSLATION: String = "arg_target_translation"
    }
}
