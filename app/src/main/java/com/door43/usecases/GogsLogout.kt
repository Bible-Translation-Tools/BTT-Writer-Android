package com.door43.usecases

import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.tasks.io.OkHttpRequest
import com.door43.translationstudio.tasks.io.RequestAPI
import com.door43.translationstudio.ui.SettingsActivity
import org.json.JSONArray
import org.json.JSONException
import org.unfoldingword.gogsclient.User
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class GogsLogout @Inject constructor(
    private val profile: Profile,
    private val prefs: IPreferenceRepository
) {
    fun execute() {
        // local user (non-server account)
        val user = profile.gogsUser ?: return
        val tokenName = user.token.name
        val tokenSha1 = user.token.toString()

        // uses Basic authorization scheme, token should be null
        user.password = tokenSha1
        user.token = null

        val apiUrl = prefs.getDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            R.string.pref_default_gogs_api
        )
        val requester: RequestAPI = OkHttpRequest(apiUrl)
        val tokenId: Int = getTokenId(user, requester, tokenName)
        if (tokenId < 0) {
            return
        }
        deleteToken(user, tokenId, requester)
    }

    private fun getTokenId(
        user: User,
        requester: RequestAPI,
        tokenName: String
    ): Int {
        var tokenId = -1
        val requestPath = String.format("/users/%s/tokens", user.username)

        val tokenResponse = requester[requestPath, user]
        if (tokenResponse.code == 200) {
            try {
                val data = JSONArray(tokenResponse.data)
                for (i in 0 until data.length()) {
                    val tkName = data.getJSONObject(i).getString("name")

                    if (tkName == tokenName) {
                        tokenId = data.getJSONObject(i).getInt("id")
                        break
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        return tokenId
    }

    private fun deleteToken(
        user: User,
        tokenId: Int,
        requester: RequestAPI
    ) {
        val path = String.format("/users/%s/tokens/%s", user.username, tokenId)
        val response = requester.delete(path, user)

        if (response.code != 200 || response.code != 204) {
            Logger.w(
                this::class.java.name,
                "delete access token - gogs api responded with code " + response.code,
                response.exception
            )
        }
    }
}