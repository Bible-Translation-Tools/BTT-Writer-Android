package com.door43.translationstudio

import android.content.Context
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.ImportUSFM
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.usecases.SearchGogsUsers
import com.door43.util.FileUtilities.readStreamToString
import org.json.JSONArray
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.gogsclient.Token

/**
 * Created by joel on 2/25/2015.
 */
object TestUtils {
    /**
     * Utility to load a signature
     * @param assetProvider
     * @param sig
     * @return Signature string from asset
     * @throws Exception
     */
    @Throws(Exception::class)
    fun loadSig(assetProvider: AssetsProvider, sig: String?): String {
        val sigStream = assetProvider.open(sig!!)
        val sigJson = readStreamToString(sigStream)
        val json = JSONArray(sigJson)
        val sigObj = json.getJSONObject(0)
        return sigObj.getString("sig")
    }

    /**
     * import USFM file to be used for testing
     * @param library
     * @param appContext
     * @param directoryProvider
     * @param profile
     * @param assetsProvider
     * @param path
     * @return created TargetTranslation
     */
    fun importTargetTranslation(
        library: Door43Client,
        appContext: Context,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        assetsProvider: AssetsProvider,
        langCode: String,
        path: String
    ): TargetTranslation? {
        val targetLanguage = library.index.getTargetLanguage(langCode)
        val usfm = ImportUSFM.Builder(
            appContext,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, path, null)
            .build()

        assertNotNull("usfm should not be null", usfm)
        assertNotNull("target language should not be null", targetLanguage)
        assertTrue("import usfm test file should succeed", usfm!!.isProcessSuccess)
        val imports = usfm.importProjects
        Assert.assertEquals("import usfm test file should succeed", 1, imports.size.toLong())

        //open import as targetTranslation
        val projectFolder = imports[0]

        return TargetTranslation.open(projectFolder, null)
    }

    fun loginGogsUser(
        profile: Profile,
        searchGogsUsers: SearchGogsUsers
    ) {
        val username = BuildConfig.TEST_USER
        val token = BuildConfig.TEST_TOKEN

        assertTrue("Username should not be empty", username.isNotEmpty())
        assertTrue("Token should not be empty", token.isNotEmpty())

        val user = searchGogsUsers.execute(username, 1).singleOrNull()
        user?.token = Token("token", token)

        assertNotNull("User should not be null", user)

        profile.gogsUser = user
    }
}
