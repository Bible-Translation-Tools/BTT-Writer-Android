package com.door43.translationstudio.tasks.io;

import android.util.Base64;

import org.unfoldingword.gogsclient.Response;
import org.unfoldingword.gogsclient.User;
import org.unfoldingword.tools.logger.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class Request {
    private int readTimeout = 5000;
    private int connectionTimeout = 5000;
    private final String baseUrl;
    private Response lastResponse = null;

    public Request(String apiUrl) {
        this.baseUrl = apiUrl.replaceAll("/+$", "") + "/";
    }

    /**
     * Performs a request against the api
     * @param partialUrl the api command
     * @param user the user authenticating this request. Requires token or username and pasword
     * @param postData if not null the request will POST the data otherwise it will be a GET request
     * @param requestMethod if null the request method will default to POST or GET
     * @return
     */
    public Response request(String partialUrl, User user, String postData, String requestMethod) {
        int responseCode = 0;
        String responseData = null;
        Exception exception = null;
        try {
            URL url = new URL(this.baseUrl + partialUrl.replaceAll("^/+", ""));
            HttpURLConnection conn;
            if(url.getProtocol().equals("https")) {
                conn = (HttpsURLConnection)url.openConnection();
            } else {
                conn = (HttpURLConnection)url.openConnection();
            }
            if(user != null) {
                String auth = encodeUserAuth(user);
                if(auth != null) {
                    conn.addRequestProperty("Authorization", auth);
                }
            }
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setReadTimeout(this.readTimeout);
            conn.setConnectTimeout(this.connectionTimeout);

            // custom request method
            if(requestMethod != null) {
                conn.setRequestMethod(requestMethod.toUpperCase());
            }

            if(postData != null) {
                // post
                if(requestMethod == null) {
                    conn.setRequestMethod("POST");
                }
                conn.setDoOutput(true);
                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes(postData);
                dos.flush();
                dos.close();
            }

            responseCode = conn.getResponseCode();

            if(isRequestMethodReadable(conn.getRequestMethod())) {
                // read response
                BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int current;
                while ((current = bis.read()) != -1) {
                    baos.write((byte) current);
                }
                responseData = baos.toString("UTF-8");
            }
        } catch (Exception e) {
            exception = e;
        }
        this.lastResponse = new Response(responseCode, responseData, exception);
        return this.lastResponse;
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
