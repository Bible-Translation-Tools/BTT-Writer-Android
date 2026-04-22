package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.TestUtils
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.util.FileUtilities
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.bibletranslationtools.gogsclient.GogsAPI
import org.bibletranslationtools.gogsclient.PublicKey
import org.bibletranslationtools.gogsclient.Response
import java.io.IOException
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify

class RegisterSSHKeysTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var resources: Resources

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(App)
        every { App.udid() }.returns("1234567890")

        mockkObject(FileUtilities)
        every { FileUtilities.readFileToString(any()) }.returns("public_key_string")

        mockkConstructor(GogsAPI::class)
        coEvery { anyConstructed<GogsAPI>().listPublicKeys(any()) }.returns(listOf())
        coEvery { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }.returns(true)
        coEvery { anyConstructed<GogsAPI>().createPublicKey(any(), any()) }.returns(mockk())

        every { context.resources }.returns(resources)
        every { progressListener.onProgress(any(), any()) }.just(runs)
        every { directoryProvider.generateSSHKeys() }.just(runs)
        every { directoryProvider.publicKey }.returns(mockk())

        every { prefRepository.getDefaultPref(any(), any(), String::class.java) }
            .returns("/api")

        every { resources.getString(R.string.gogs_public_key_name) }
            .returns("public key")
        every { context.getString(R.string.pref_default_gogs_api) }.returns("/api")
        every { context.getString(R.string.gogs_user_agent) }.returns("btt-writer-android")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test register ssh keys authorized, no force`() = runTest {
        every { profile.gogsUser }.returns(mockk())
        every { directoryProvider.hasSSHKeys() }.returns(true)

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(false, progressListener)

        assertTrue(success)

        verifyCommonCalls()

        coVerify(exactly = 0) { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }
        verify(exactly = 0) { anyConstructed<GogsAPI>().getLastResponse() }
        verify(exactly = 0) { directoryProvider.generateSSHKeys() }
    }

    @Test
    fun `test register ssh keys not authorized`() = runTest {
        every { profile.gogsUser }.returns(null)

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(false, progressListener)

        assertFalse(success)

        verify { profile.gogsUser }
        verify { App.udid() }
        verify { progressListener.onProgress(any(), "Authenticating") }

        coVerify(exactly = 0) { anyConstructed<GogsAPI>().listPublicKeys(any()) }
        verify(exactly = 0) { directoryProvider.generateSSHKeys() }
    }

    @Test
    fun `test register ssh keys, no ssh keys`() = runTest {
        every { profile.gogsUser }.returns(mockk())
        every { directoryProvider.hasSSHKeys() }.returns(false)

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(false, progressListener)

        assertTrue(success)

        verifyCommonCalls()

        verify { directoryProvider.generateSSHKeys() }
        coVerify(exactly = 0) { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }
        verify(exactly = 0) { anyConstructed<GogsAPI>().getLastResponse() }
    }

    @Test
    fun `test register ssh keys, force generate`() = runTest {
        every { profile.gogsUser }.returns(mockk())
        every { directoryProvider.hasSSHKeys() }.returns(true)

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(true, progressListener)

        assertTrue(success)

        verifyCommonCalls()

        verify { directoryProvider.generateSSHKeys() }
        coVerify(exactly = 0) { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }
        verify(exactly = 0) { anyConstructed<GogsAPI>().getLastResponse() }
    }

    @Test
    fun `test register ssh keys, read key fails`() = runTest {
        every { profile.gogsUser }.returns(mockk())
        every { directoryProvider.hasSSHKeys() }.returns(true)

        every { FileUtilities.readFileToString(any()) }.throws(IOException("An error occurred."))

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(false, progressListener)

        assertFalse(success)

        verify { profile.gogsUser }
        verify { directoryProvider.hasSSHKeys() }
        verify(exactly = 0) { directoryProvider.generateSSHKeys() }
        coVerify(exactly = 0) { anyConstructed<GogsAPI>().listPublicKeys(any()) }
        coVerify(exactly = 0) { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }
        verify(exactly = 0) { anyConstructed<GogsAPI>().getLastResponse() }
    }

    @Test
    fun `test register ssh keys, delete app public keys`() = runTest {
        every { profile.gogsUser }.returns(mockk())
        every { directoryProvider.hasSSHKeys() }.returns(true)

        val publicKey: PublicKey = mockk {
            every { title }.returns("public key ${App.udid()}")
        }
        coEvery { anyConstructed<GogsAPI>().listPublicKeys(any()) }.returns(listOf(publicKey))

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(false, progressListener)

        assertTrue(success)

        verify { profile.gogsUser }
        verify { directoryProvider.hasSSHKeys() }
        coVerify { anyConstructed<GogsAPI>().listPublicKeys(any()) }
        coVerify { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }
        verify(exactly = 0) { directoryProvider.generateSSHKeys() }
        verify(exactly = 0) { anyConstructed<GogsAPI>().getLastResponse() }
    }

    @Test
    fun `test register ssh keys, delete custom public keys fails`() = runTest {
        every { profile.gogsUser }.returns(mockk())
        every { directoryProvider.hasSSHKeys() }.returns(true)

        val publicKey: PublicKey = mockk {
            every { title }.returns("my personal key")
        }
        coEvery { anyConstructed<GogsAPI>().listPublicKeys(any()) }.returns(listOf(publicKey))

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(false, progressListener)

        assertTrue(success)

        verify { profile.gogsUser }
        verify { directoryProvider.hasSSHKeys() }
        coVerify { anyConstructed<GogsAPI>().listPublicKeys(any()) }
        coVerify(exactly = 0) { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }
        verify(exactly = 0) { directoryProvider.generateSSHKeys() }
        verify(exactly = 0) { anyConstructed<GogsAPI>().getLastResponse() }
    }

    @Test
    fun `test register ssh keys, create new key fails`() = runTest {
        every { profile.gogsUser }.returns(mockk())
        every { directoryProvider.hasSSHKeys() }.returns(true)

        coEvery { anyConstructed<GogsAPI>().createPublicKey(any(), any()) }.returns(null)

        val response: Response = mockk {
            every { code }.returns(500)
            every { message }.returns("Internal Server Error")
        }
        TestUtils.setPropertyReflection(response, "exception", Exception("Error!"))
        every { anyConstructed<GogsAPI>().getLastResponse() }.returns(response)

        val success = RegisterSSHKeys(
            context,
            profile,
            directoryProvider,
            prefRepository
        ).execute(false, progressListener)

        assertFalse(success)

        verify { profile.gogsUser }
        verify { directoryProvider.hasSSHKeys() }
        coVerify { anyConstructed<GogsAPI>().listPublicKeys(any()) }
        coVerify { anyConstructed<GogsAPI>().createPublicKey(any(), any()) }
        verify { anyConstructed<GogsAPI>().getLastResponse() }
        coVerify(exactly = 0) { anyConstructed<GogsAPI>().deletePublicKey(any(), any()) }
        verify(exactly = 0) { directoryProvider.generateSSHKeys() }
    }

    private fun verifyCommonCalls() {
        verify { profile.gogsUser }
        verify { directoryProvider.hasSSHKeys() }
        verify { App.udid() }
        verify { FileUtilities.readFileToString(any()) }
        coVerify { anyConstructed<GogsAPI>().listPublicKeys(any()) }
        coVerify { anyConstructed<GogsAPI>().createPublicKey(any(), any()) }
        verify { directoryProvider.publicKey }
        verify { progressListener.onProgress(any(), "Authenticating") }
    }
}