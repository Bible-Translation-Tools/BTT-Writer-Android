package com.door43.translationstudio;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.door43.util.signing.Crypto;
import com.door43.util.signing.SigningEntity;
import com.door43.util.signing.Status;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.security.PublicKey;

/**
 * Created by joel on 2/25/2015.
 */
public class SigningTest {
    private PublicKey mCA;
    private SigningEntity mVerifiedSE;
    private byte[] mData;
    private SigningEntity mExpiredSE;
    private SigningEntity mErrorSE;
    private SigningEntity mFailedSE;
    private Context mContext;

    @Before
    protected void setUp() throws Exception {

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if(mCA == null) {
            InputStream caPubKey = mContext.getAssets().open("certs/ca.pub");
            mCA = Crypto.loadPublicECDSAKey(caPubKey);

            InputStream verifiedStream = mContext.getAssets().open("signing/si/verified.pem");
            mVerifiedSE = SigningEntity.generateFromIdentity(mCA, verifiedStream);

            InputStream failedStream = mContext.getAssets().open("signing/si/failed.pem");
            mFailedSE = SigningEntity.generateFromIdentity(mCA, failedStream);

            InputStream errorStream = mContext.getAssets().open("signing/si/error.pem");
            mErrorSE = SigningEntity.generateFromIdentity(mCA, errorStream);

            InputStream expiredStream = mContext.getAssets().open("signing/si/expired.pem");
            mExpiredSE = SigningEntity.generateFromIdentity(mCA, expiredStream);

            InputStream dataStream = mContext.getAssets().open("signing/data.json");
            mData = Crypto.readInputStreamToBytes(dataStream);
        }
    }

    @Test
    public void testLoadPublicECDSAKey() throws Exception {
        Assert.assertNotNull(mCA);
    }

    @Test
    public void testLoadSigningIdentity() throws Exception {
        Assert.assertNotNull(mVerifiedSE);
    }

    @Test
    public void testVerifySigningEntity() throws Exception {
        Assert.assertEquals(Status.VERIFIED, mVerifiedSE.status());
        Assert.assertEquals(Status.FAILED, mFailedSE.status());
//        assertEquals(Status.EXPIRED, mExpiredSE.status());
        // TODO: we need to get an expired SI for testing.
        Assert.assertEquals(Status.ERROR, mErrorSE.status());
    }

    @Test
    public void testVerifyValidSESignatures() throws Exception {
        // TODO: this test is broken
//        Status verified = mVerifiedSE.verifyContent(Util.loadSig("tests/signing/sig/verified.sig"), mData);
//        assertEquals(Status.VERIFIED, verified);

        Status failed = mVerifiedSE.verifyContent(Util.loadSig(mContext, "signing/sig/failed.sig"), mData);
        Assert.assertEquals(Status.FAILED, failed);

        Status error = mVerifiedSE.verifyContent(Util.loadSig(mContext, "signing/sig/error.sig"), mData);
        Assert.assertEquals(Status.ERROR, error);

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
    public void testVerifyFailedSESignatures() throws Exception {
        Status verified = mFailedSE.verifyContent(Util.loadSig(mContext, "signing/sig/verified.sig"), mData);
        Assert.assertEquals(Status.FAILED, verified);

        Status failed = mFailedSE.verifyContent(Util.loadSig(mContext, "signing/sig/failed.sig"), mData);
        Assert.assertEquals(Status.FAILED, failed);

        Status error = mFailedSE.verifyContent(Util.loadSig(mContext, "signing/sig/error.sig"), mData);
        Assert.assertEquals(Status.FAILED, error);
    }

    @Test
    public void testVerifyErrorSESignatures() throws Exception {
        // TODO: this test is broken
//        Status verified = mErrorSE.verifyContent(Util.loadSig("tests/signing/sig/verified.sig"), mData);
//        assertEquals(Status.ERROR, verified);

        Status failed = mErrorSE.verifyContent(Util.loadSig(mContext, "signing/sig/failed.sig"), mData);
        Assert.assertEquals(Status.FAILED, failed);

        Status error = mErrorSE.verifyContent(Util.loadSig(mContext, "signing/sig/error.sig"), mData);
        Assert.assertEquals(Status.ERROR, error);
    }
}
