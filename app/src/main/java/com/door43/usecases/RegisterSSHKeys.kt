package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.util.FileUtilities.readFileToString
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.PublicKey
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import javax.inject.Inject

class RegisterSSHKeys @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profile: Profile,
    private val directoryProvider: IDirectoryProvider,
    private val prefRepository: IPreferenceRepository
) {
    private val max = 100

    fun execute(force: Boolean, progressListener: OnProgressListener? = null): Boolean {
        progressListener?.onProgress(-1, max, "Authenticating")

        val keyName = context.resources.getString(R.string.gogs_public_key_name) + " " + App.udid();

        val api = GogsAPI(
            prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_GOGS_API,
                context.getString(R.string.pref_default_gogs_api)
            )
        )

        if (profile.gogsUser != null) {
            if (!directoryProvider.hasSSHKeys() || force) {
                directoryProvider.generateSSHKeys()
            }
            var keyString: String? = null
            try {
                keyString = readFileToString(directoryProvider.publicKey).trim()
            } catch (e: IOException) {
                e.printStackTrace()
                Logger.e(this.javaClass.name, "Failed to retrieve the public key", e)
                return false
            }

            val keyTemplate = PublicKey(keyName, keyString)

            // delete old key
            val keys = api.listPublicKeys(profile.gogsUser)
            for (k in keys) {
                if (k.title == keyTemplate.title) {
                    api.deletePublicKey(k, profile.gogsUser)
                    break
                }
            }

            // create new key
            val key = api.createPublicKey(keyTemplate, profile.gogsUser)
            if (key != null) {
                return true
            } else {
                val response = api.lastResponse
                Logger.w(
                    this.javaClass.name,
                    "Failed to register the public key. Gogs responded with " + response.code + ": " + response.data,
                    response.exception
                )
            }
        }

        return false
    }
}