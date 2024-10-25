package com.door43.translationstudio

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.util.signing.Crypto
import com.door43.util.signing.SigningEntity
import com.door43.util.signing.Status
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.PublicKey
import javax.inject.Inject

/**
 * Created by joel on 2/25/2015.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SigningTest {
    private var ca: PublicKey? = null
    private lateinit var data: ByteArray
    private lateinit var verifiedSE: SigningEntity
    private lateinit var expiredSE: SigningEntity
    private lateinit var errorSE: SigningEntity
    private lateinit var failedSE: SigningEntity

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @Inject
    lateinit var assetsProvider: AssetsProvider

    @Before
    @Throws(Exception::class)
    fun setUp() {
        hiltRule.inject()
        if (ca == null) {
            val caPubKey = assetsProvider.open("certs/ca.pub")
            ca = Crypto.loadPublicECDSAKey(caPubKey)

            val verifiedStream = assetsProvider.open("signing/si/verified.pem")
            verifiedSE = SigningEntity.generateFromIdentity(ca, verifiedStream)

            val failedStream = assetsProvider.open("signing/si/failed.pem")
            failedSE = SigningEntity.generateFromIdentity(ca, failedStream)

            val errorStream = assetsProvider.open("signing/si/error.pem")
            errorSE = SigningEntity.generateFromIdentity(ca, errorStream)

            val expiredStream = assetsProvider.open("signing/si/expired.pem")
            expiredSE = SigningEntity.generateFromIdentity(ca, expiredStream)

            val dataStream = assetsProvider.open("signing/data.json")
            data = Crypto.readInputStreamToBytes(dataStream)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testLoadPublicECDSAKey() {
        Assert.assertNotNull(ca)
    }

    @Test
    @Throws(Exception::class)
    fun testLoadSigningIdentity() {
        Assert.assertNotNull(verifiedSE)
    }

    @Test
    @Throws(Exception::class)
    fun testVerifySigningEntity() {
        Assert.assertEquals(Status.VERIFIED, verifiedSE.status())
        Assert.assertEquals(Status.FAILED, failedSE.status())
        //        assertEquals(Status.EXPIRED, mExpiredSE.status());
        // TODO: we need to get an expired SI for testing.
        Assert.assertEquals(Status.ERROR, errorSE.status())
    }

    @Test
    @Throws(Exception::class)
    fun testVerifyValidSESignatures() {
        // TODO: this test is broken
//        Status verified = mVerifiedSE.verifyContent(Util.loadSig("tests/signing/sig/verified.sig"), mData);
//        assertEquals(Status.VERIFIED, verified);

        val failed =
            verifiedSE.verifyContent(TestUtils.loadSig(assetsProvider, "signing/sig/failed.sig"), data)
        Assert.assertEquals(Status.FAILED, failed)

        val error =
            verifiedSE.verifyContent(TestUtils.loadSig(assetsProvider, "signing/sig/error.sig"), data)
        Assert.assertEquals(Status.ERROR, error)

        // NOTE: signatures don't expire themselves
    }

    //    public void testVerifyExpiredSESignatures() throws Exception {
    //        Status verified = mExpiredSE.verifyContent(Util.loadSig("tests/signing/sig/verified.sig"), mData);
    //        assertEquals(Status.EXPIRED, verified);
    //
    //        Status failed = mExpiredSE.verifyContent(Util.loadSig("tests/signing/sig/failed.sig"), mData);
    //        assertEquals(Status.FAILED, failed);
    //
    //        Status error = mExpiredSE.verifyContent(Util.loadSig("tests/signing/sig/error.sig"), mData);
    //        assertEquals(Status.ERROR, error);
    //    }
    @Test
    @Throws(Exception::class)
    fun testVerifyFailedSESignatures() {
        val verified = failedSE.verifyContent(TestUtils.loadSig(assetsProvider, "signing/sig/verified.sig"), data)
        Assert.assertEquals(Status.FAILED, verified)

        val failed = failedSE.verifyContent(TestUtils.loadSig(assetsProvider, "signing/sig/failed.sig"), data)
        Assert.assertEquals(Status.FAILED, failed)

        val error = failedSE.verifyContent(TestUtils.loadSig(assetsProvider, "signing/sig/error.sig"), data)
        Assert.assertEquals(Status.FAILED, error)
    }

    @Test
    @Throws(Exception::class)
    fun testVerifyErrorSESignatures() {
        // TODO: this test is broken
//        Status verified = mErrorSE.verifyContent(Util.loadSig("tests/signing/sig/verified.sig"), mData);
//        assertEquals(Status.ERROR, verified);

        val failed = errorSE.verifyContent(TestUtils.loadSig(assetsProvider, "signing/sig/failed.sig"), data)
        Assert.assertEquals(Status.FAILED, failed)

        val error = errorSE.verifyContent(TestUtils.loadSig(assetsProvider, "signing/sig/error.sig"), data)
        Assert.assertEquals(Status.ERROR, error)
    }
}
