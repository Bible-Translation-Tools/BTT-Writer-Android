package com.door43.usecases

import android.content.Context
import android.os.Build
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.tasks.io.OkHttpRequest
import com.door43.translationstudio.tasks.io.RequestAPI
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONException
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.Token
import org.unfoldingword.gogsclient.User
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class GogsLogin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository
) {
    fun execute(
        username: String,
        password: String,
        fullName: String? = null
    ): LoginResult {
        val apiUrl = prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            context.getString(R.string.pref_default_gogs_api)
        )
        val api = GogsAPI(apiUrl)
        val authUser = User(username, password)
        val tokenName = getTokenStub()

        // get user
        val user = api.getUser(authUser, authUser)
        if (user != null) {
            val customRequester: RequestAPI = OkHttpRequest(apiUrl)
            val tokenId = getTokenId(tokenName, authUser, customRequester)
            if (tokenId != -1) {
                // Delete (if exists) matching token for this device on server
                deleteToken(tokenId, authUser, customRequester)
            }

            // Create a new token
            val t = Token(tokenName, arrayOf("write:repository", "write:user"))
            user.token = api.createToken(t, authUser)

            // validate access token
            if (user.token == null) {
                val response = api.lastResponse
                Logger.w(
                    GogsLogin::class.java.name,
                    "gogs api responded with " + response.code + ": " + response.toString(),
                    response.exception
                )
                return LoginResult(null)
            }

            // set missing full_name
            if (user.fullName.isNullOrEmpty() && !fullName.isNullOrEmpty()) {
                user.fullName = fullName
                val updatedUser = api.editUser(user, authUser)
                if (updatedUser == null) {
                    val response = api.lastResponse
                    Logger.w(
                        GogsLogin::class.java.name,
                        "The full_name could not be updated gogs api responded with " + response.code + ": " + response.toString(),
                        response.exception
                    )
                }
            }
        }

        return LoginResult(user)
    }

    private fun getTokenStub(): String {
        val defaultTokenName = context.resources.getString(R.string.gogs_token_name)
        val androidId = Build.DEVICE.lowercase()
        val nickname = App.udid()
        val tokenSuffix = String.format("%s_%s__%s", Build.MANUFACTURER, nickname, androidId)
        return (defaultTokenName + "__" + tokenSuffix).replace(" ", "_")
    }

    private fun getTokenId(tokenName: String, userAuth: User, requester: RequestAPI): Int {
        var tokenId = -1
        val urlPath = String.format("/users/%s/tokens", userAuth.username)
        val tokenResponse = requester[urlPath, userAuth]

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

    private fun deleteToken(tokenId: Int, userAuth: User, requester: RequestAPI) {
        val urlPath = String.format("/users/%s/tokens/%s", userAuth.username, tokenId)
        val response = requester.delete(urlPath, userAuth)

        if (response.code != 204) {
            Logger.w(
                GogsLogin::class.java.name,
                "delete access token - gogs api responded with code " + response.code,
                response.exception
            )
        }
    }

    data class LoginResult(val user: User?)
}