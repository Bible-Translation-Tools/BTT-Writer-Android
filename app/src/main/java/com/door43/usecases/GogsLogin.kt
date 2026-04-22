package com.door43.usecases

import android.content.Context
import android.os.Build
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import org.bibletranslationtools.gogsclient.GogsAPI
import org.bibletranslationtools.gogsclient.Token
import org.bibletranslationtools.gogsclient.User
import org.bibletranslationtools.logger.Logger

class GogsLogin(
    private val context: Context,
    prefRepository: IPreferenceRepository
) {
    private val apiUrl = prefRepository.getDefaultPref(
        IPreferenceRepository.KEY_PREF_GOGS_API,
        context.getString(R.string.pref_default_gogs_api)
    )
    private val api = GogsAPI(
        apiUrl = apiUrl,
        userAgent = context.getString(R.string.gogs_user_agent)
    )

    suspend fun execute(
        username: String,
        password: String,
        fullName: String? = null
    ): LoginResult {
        val authUser = User(username = username, password = password)
        val tokenName = getTokenStub()

        // get user
        var user = api.getUser(authUser, authUser)
        if (user != null) {
            val tokenId = getTokenId(tokenName, authUser)
            if (tokenId != -1) {
                // Delete (if exists) matching token for this device on server
                deleteToken(tokenId, authUser)
            }

            // Create a new token
            val t = Token(name = tokenName, scopes = listOf("write:repository", "write:user"), id = 0)
            user = user.copy(token = api.createToken(t, authUser))

            // validate access token
            if (user.token == null) {
                val response = api.getLastResponse()
                Logger.w(
                    GogsLogin::class.java.name,
                    "gogs api responded with " + response?.code + ": " + response?.message
                )
                return LoginResult(null)
            }

            // set missing full_name
            if (user.fullName.isNullOrEmpty() && !fullName.isNullOrEmpty()) {
                user = user.copy(fullName = fullName)
                val updatedUser = api.editUser(user, authUser)
                if (updatedUser == null) {
                    val response = api.getLastResponse()
                    Logger.w(
                        GogsLogin::class.java.name,
                        "The full_name could not be updated gogs api responded with " + response?.code + ": " + response?.message
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

    private suspend fun getTokenId(tokenName: String, user: User): Int {
        return api.listTokens(user)
            .find { it.name == tokenName }
            ?.id ?: -1
    }

    private suspend fun deleteToken(tokenId: Int, user: User) {
        val deleted = api.deleteToken(tokenId, user)
        if (!deleted) {
            val response = api.getLastResponse()
            Logger.w(
                GogsLogin::class.java.name,
                "Delete access token - gogs api responded with code " + response?.code
            )
        }
    }

    data class LoginResult(val user: User?)
}