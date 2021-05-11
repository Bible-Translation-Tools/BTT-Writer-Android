package com.door43.translationstudio.tasks.io;

import android.util.Base64;

import org.json.JSONObject;
import org.unfoldingword.gogsclient.User;
import org.unfoldingword.tools.logger.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.Request;

public class OkHttpRequest {
    private OkHttpClient client;
//    private int readTimeout = 5000;
//    private int connectionTimeout = 5000;
    private final String baseUrl;

    public OkHttpRequest(String apiUrl) {
        client = new OkHttpClient();
        this.baseUrl = apiUrl.replaceAll("/+$", "") + "/";
    }

    /**
     * Performs a request against the api
     * @param path the api path
     * @param user the user authenticating this request. Requires token or username and pasword
     * @return
     */
    public Response get(String path, User user) {

        String auth = encodeUserAuth(user);

        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", auth)
                .get()
                .build();

        try {
            Response response = client.newCall(request).execute();
            return response;
        } catch (IOException ex) {
            Logger.w(OkHttpRequest.class.getName(), "Request failed with an exception.", ex);
        }

        return null;
    }

    public Response delete(String path, User user) {

        String auth = encodeUserAuth(user);

        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", auth)
                .delete()
                .build();

        try {
            Response response = client.newCall(request).execute();
            return response;
        } catch (IOException ex) {
            Logger.w(OkHttpRequest.class.getName(), "Request failed with an exception.", ex);
        }

        return null;
    }


    /**
     * Generates the authentication parameter for the user
     * Preference will be given to the token if it exists
     * @param user
     * @return
     */
    private String encodeUserAuth(User user) {
        if(user != null) {
            if(user.token != null) {
                return "token " + user.token;
            } else if(user.getUsername() != null && !user.getUsername().isEmpty()
                    && user.getPassword() != null && !user.getPassword().isEmpty()) {
                String credentials = user.getUsername() + ":" + user.getPassword();
                try {
                    return "Basic " + Base64.encodeToString(credentials.getBytes("UTF-8"), Base64.NO_WRAP);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * Checks if the request method is one that will return content
     * @param method
     * @return
     */
    private boolean isRequestMethodReadable(String method) {
        switch(method.toUpperCase()) {
            case "DELETE":
            case "PUT":
                return false;
            default:
                return true;
        }
    }
}
