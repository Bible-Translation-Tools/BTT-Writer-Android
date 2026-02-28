package com.door43.translationstudio.tasks.io;
import org.unfoldingword.gogsclient.Response;
import org.unfoldingword.gogsclient.User;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import android.util.Base64;

import org.unfoldingword.tools.logger.Logger;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;


public class OkHttpRequest implements RequestAPI {
    private OkHttpClient client;
    private int readTimeout = 5000;
    private int connectionTimeout = 5000;
    private final String baseUrl;

    public OkHttpRequest(String apiUrl) {
        client = new OkHttpClient.Builder()
                    .connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                    .build();

        this.baseUrl = apiUrl.replaceAll("/+$", "");
    }

    /**
     * @param path the api path
     * @param userAuth the user authentication info. Requires account token or credentials
     * @return
     */
    @Override
    public Response get(String path, User userAuth) {
        int responseCode = 0;
        String responseData = null;
        Exception exception = null;

        String auth = encodeAuthHeader(userAuth);

        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", auth)
                .get()
                .build();

        try {
            okhttp3.Response response = client.newCall(request).execute();
            responseCode = response.code();
            responseData = response.body().string();
        } catch (IOException ex) {
            Logger.w(OkHttpRequest.class.getName(), "Request failed with an exception.", ex);
            exception = ex;
        }

        return new Response(responseCode, responseData, exception);
    }

    /**
     * @param path the api path
     * @param userAuth the user authentication info. Requires account token or credentials
     * @param postData data (body) to submit
     * @return
     */
    @Override
    public Response post(String path, User userAuth, String postData) {
        int responseCode = 0;
        String responseData = null;
        Exception exception = null;

        String auth = encodeAuthHeader(userAuth);

        RequestBody body = RequestBody.create(postData, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", auth)
                .post(body)
                .build();

        try {
            okhttp3.Response response = client.newCall(request).execute();
            responseCode = response.code();
            responseData = response.body().string();
        } catch (IOException ex) {
            Logger.w(OkHttpRequest.class.getName(), "Request failed with an exception.", ex);
            exception = ex;
        }

        return new Response(responseCode, responseData, exception);
    }

    /**
     * @param path the api path
     * @param userAuth the user authentication info. Requires account token or credentials
     * @return
     */
    @Override
    public Response delete(String path, User userAuth) {
        int responseCode = 0;
        Exception exception = null;

        String auth = encodeAuthHeader(userAuth);

        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", auth)
                .delete()
                .build();

        try {
            okhttp3.Response response = client.newCall(request).execute();
            responseCode = response.code();
        } catch (IOException ex) {
            Logger.w(OkHttpRequest.class.getName(), "Request failed with an exception.", ex);
            exception = ex;
        }

        return new Response(responseCode, null, exception);
    }

    /**
     * See post() method for more detail
     */
    @Override
    public Response put(String path, User userAuth, String postData) {
        int responseCode = 0;
        String responseData = null;
        Exception exception = null;

        String auth = encodeAuthHeader(userAuth);

        RequestBody body = RequestBody.create(postData, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", auth)
                .put(body)
                .build();

        try {
            okhttp3.Response response = client.newCall(request).execute();
            responseCode = response.code();
            responseData = response.body().string();
        } catch (IOException ex) {
            Logger.w(OkHttpRequest.class.getName(), "Request failed with an exception.", ex);
            exception = ex;
        }

        return new Response(responseCode, responseData, exception);
    }


    /**
     * Generates the authentication value from the use token or credentials
     */
    private String encodeAuthHeader(User user) {
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
}