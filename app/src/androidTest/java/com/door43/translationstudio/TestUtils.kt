package com.door43.translationstudio;

import com.door43.data.AssetsProvider;
import com.door43.util.FileUtilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * Created by joel on 2/25/2015.
 */
public class Util {
    /**
     * Utility to load a signature
     * @param assetProvider
     * @param sig
     * @return Signature string from asset
     * @throws Exception
     */
    public static String loadSig(AssetsProvider assetProvider, String sig) throws Exception {
        InputStream sigStream = assetProvider.open(sig);
        String sigJson = FileUtilities.readStreamToString(sigStream);
        JSONArray json = new JSONArray(sigJson);
        JSONObject sigObj = json.getJSONObject(0);
        return sigObj.getString("sig");
    }
}
