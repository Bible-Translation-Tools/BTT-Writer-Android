package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import org.bibletranslationtools.gogsclient.GogsAPI
import org.bibletranslationtools.gogsclient.User
import org.bibletranslationtools.logger.Logger
import kotlin.code

class GogsLogout(
    context: Context,
    prefs: IPreferenceRepository,
    private val profile: Profile
) {
    private val apiUrl = prefs.getDefaultPref(
        IPreferenceRepository.KEY_PREF_GOGS_API,
        context.resources.getString(R.string.pref_default_gogs_api)
    )
    private val api = GogsAPI(
        apiUrl = apiUrl,
        userAgent = context.getString(R.string.gogs_user_agent)
    )

    suspend fun execute() {
        // local user (non-server account)
        var user = profile.gogsUser ?: return
        val token = user.token ?: return

        val tokenName = token.name
        val tokenSha1 = token.toString()

        // uses Basic authorization scheme, token should be null
        user = user.copy(password = tokenSha1, token = null)

        val tokenId = getTokenId(user, tokenName)
        if (tokenId < 0) return
        deleteToken(user, tokenId)
    }

    private suspend fun getTokenId(user: User, tokenName: String): Int {
        return api.listTokens(user)
            .find { it.name == tokenName }
            ?.id ?: -1
    }

    private suspend fun deleteToken(user: User, tokenId: Int) {
        val deleted = api.deleteToken(tokenId, user)
        if (!deleted) {
            val response = api.getLastResponse()
            Logger.w(
                GogsLogin::class.java.name,
                "Delete access token - gogs api responded with code " + response?.code
            )
        }
    }
}