package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.util.FileUtilities
import org.bibletranslationtools.gogsclient.GogsAPI
import org.bibletranslationtools.gogsclient.PublicKey
import org.bibletranslationtools.logger.Logger
import java.io.IOException

class RegisterSSHKeys(
    private val context: Context,
    private val profile: Profile,
    private val directoryProvider: IDirectoryProvider,
    private val prefRepository: IPreferenceRepository
) {
    suspend fun execute(force: Boolean, progressListener: OnProgressListener? = null): Boolean {
        progressListener?.onProgress(-1f, "Authenticating")

        val keyName = context.resources.getString(R.string.gogs_public_key_name) + " " + App.udid()

        val api = GogsAPI(
            apiUrl = prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_GOGS_API,
                context.getString(R.string.pref_default_gogs_api)
            ),
            userAgent = context.getString(R.string.gogs_user_agent)
        )

        profile.gogsUser?.let { user ->
            if (!directoryProvider.hasSSHKeys() || force) {
                directoryProvider.generateSSHKeys()
            }
            val keyString: String?
            try {
                keyString = FileUtilities.readFileToString(directoryProvider.publicKey).trim()
            } catch (e: IOException) {
                e.printStackTrace()
                Logger.e(this.javaClass.name, "Failed to retrieve the public key", e)
                return false
            }

            val keyTemplate = PublicKey(title = keyName, key = keyString)

            // delete old key
            val keys = api.listPublicKeys(user)
            for (k in keys) {
                if (k.title == keyTemplate.title) {
                    api.deletePublicKey(k, user)
                    break
                }
            }

            // create new key
            val key = api.createPublicKey(keyTemplate, user)
            if (key != null) {
                return true
            } else {
                val response = api.getLastResponse()
                Logger.w(
                    this.javaClass.name,
                    "Failed to register the public key. Gogs responded with " + response?.code + ": " + response?.message
                )
            }
        }

        return false
    }
}