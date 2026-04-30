package com.door43.translationstudio

import android.content.Context
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.ProcessUSFM
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.util.FileUtilities.readStreamToString
import junit.framework.TestCase
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bibletranslationtools.gogsclient.User
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.lang.reflect.Field
import java.util.UUID

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
     * Sets a property of an object using reflection
     * @param obj the source object
     * @param fieldName the name of the field
     * @param value the value to set
     */
    fun setPropertyReflection(obj: Any, fieldName: String, value: Any) {
        val cls = obj::class.java
        val field = findField(cls, fieldName)
        field?.isAccessible = true
        field?.set(obj, value)
    }

    /**
     * Finds a field in a class hierarchy
     */
    private fun findField(cls: Class<*>, fieldName: String): Field? {
        var field: Field? = null
        try {
            field = cls.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            if (cls.superclass != null) {
                field = findField(cls.superclass, fieldName)
            }
        }
        return field
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
        catalogClient: ResourceCatalogClient,
        appContext: Context,
        platform: Platform,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        assetsProvider: AssetsProvider,
        importProjects: ImportProjects,
        translator: Translator,
        langCode: String,
        path: String
    ): TargetTranslation? {
        val targetLanguage = catalogClient.library.getTargetLanguage(langCode)
        val usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            catalogClient,
            assetsProvider
        )
            .fromRc(targetLanguage!!, path)
            .build()

        assertNotNull("usfm should not be null", usfm)
        assertNotNull("target language should not be null", targetLanguage)
        assertTrue("import usfm test file should succeed", usfm!!.isProcessSuccess)
        val imports = usfm.importProjects
        assertEquals("import usfm test file should succeed", 1, imports.size.toLong())

        //open import as targetTranslation
        val projectFolder = imports[0]

        val result = importProjects.importProject(projectFolder, true)

        assertNotNull("Import result should not be null", result)
        assertNotNull("importedSlug should not be null", result?.importedSlug)

        return translator.getTargetTranslation(result!!.importedSlug!!)
    }

    fun importTargetTranslation(
        importProjects: ImportProjects,
        translator: Translator,
        assetsProvider: AssetsProvider,
        directoryProvider: IDirectoryProvider,
        path: String
    ): TargetTranslation? {
        val projectFile = directoryProvider.createTempFile("project", ".tstudio")
        assetsProvider.open(path).use { stream ->
            projectFile.outputStream().use { out ->
                stream.copyTo(out)
            }
        }
        assertTrue("Project file should exist", projectFile.exists())
        assertTrue("Project file should not be empty", projectFile.length() > 0)

        val result = importProjects.importProject(projectFile, true)
        return translator.getTargetTranslation(result!!.importedSlug!!)
    }

    suspend fun simulateLoginGogsUser(
        context: Context,
        platform: Platform,
        server: MockWebServer,
        gogsLogin: GogsLogin,
        username: String,
        fullName: String? = null
    ): User {
        server.enqueue(createLoginResponse(username, fullName))
        server.enqueue(createGetTokenResponse(context, platform))
        server.enqueue(MockResponse().setResponseCode(204)) // Delete token response
        server.enqueue(createTokenResponse(context, platform))

        val result = gogsLogin.execute("username", "password", fullName)

        TestCase.assertNotNull("User should not be null", result.user)

        val user = result.user!!

        TestCase.assertEquals(username, user.username)
        TestCase.assertNotNull("Token should not be null", user.token)
        TestCase.assertTrue(
            "Token name should contain build model",
            user.token?.name?.contains(platform.udid) == true
        )

        return user
    }

    fun getTokenStub(context: Context, platform: Platform): String {
        val defaultTokenName = context.resources.getString(R.string.gogs_token_name)
        val androidId = platform.info.device.lowercase()
        val nickname = platform.udid
        val tokenSuffix = String.format("%s_%s__%s", platform.info.manufacturer, nickname, androidId)
        return (defaultTokenName + "__" + tokenSuffix).replace(" ", "_")
    }

    fun generateHash(): String {
        return UUID.randomUUID().toString()
    }

    private fun createLoginResponse(username: String, fullName: String? = null): MockResponse {
        val body = """
            {"id": 1, "username": "$username", "full_name": "${fullName ?: ""}"}
        """.trimIndent()

        return MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200)
    }

    private fun createGetTokenResponse(context: Context, platform: Platform): MockResponse {
        val body = """
            [{"id": 1, "name": "${getTokenStub(context, platform)}", "sha1": "${generateHash()}"}]
        """.trimIndent()

        return MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200)
    }

    private fun createTokenResponse(context: Context, platform: Platform): MockResponse {
        val body = """
            {"id": 1, "name": "${getTokenStub(context, platform)}", "sha1": "${generateHash()}"}
        """.trimIndent()

        return MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(201)
    }
}
