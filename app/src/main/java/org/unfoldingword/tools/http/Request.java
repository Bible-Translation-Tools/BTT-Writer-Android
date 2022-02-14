package org.unfoldingword.tools.http;

/* Source: https://github.com/unfoldingWord-dev/android-http/tree/2.4.2 */

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Represents a network request
 */
public abstract class Request {
    private final URL url;
    private final String requestMethod;
    private String token;
    private String username;
    private String password;
    private String contentType = null;
    private int responseCode = -1;
    private String responseMessage = null;
    private int ttl = 30000;
    private OnProgressListener progressListener = null;

    /**
     * Prepare a new network request
     * @param url The url that will receive the request
     * @param requestMethod the method of request e.g. POST, GET, PUT, etc.
     */
    public Request(URL url, String requestMethod) {
        this.url = url;
        this.requestMethod = requestMethod.toUpperCase();
    }

    /**
     * Sets the token used for authenticating the post request
     * Tokens take precedence over credentials
     * Token authentication.
     * @param token the authentication token
     */
    public void setAuthentication(String token) {
        this.token = token;
    }

    /**
     * Sets the connection write and read timeout
     * @param ttl the time allowed before the connection times out
     */
    public void setTimeout(int ttl) {
        this.ttl = ttl;
    }

    /**
     * Sets the listener to receive progress updates
     * @param listener a listener that will receive progress events
     */
    public void setProgressListener(OnProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Sets the credentials used for authenticating the report
     * Basic authentication.
     * @param username the username to be authenticated as
     * @param password the password to authenticate with
     */
    public void setAuthentication(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Generates and returns the auth information if available
     * @return the formatted authentication request property
     */
    protected String getAuth() {
        if(this.token != null) {
            return "token " + this.token;
        } else if(this.username != null && this.password != null){
            String credentials = this.username + ":" + this.password;
            try {
                return "Basic " + Base64.encodeToString(credentials.getBytes("UTF-8"), Base64.NO_WRAP);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Sets the content type to be used in the request
     * @param contentType the content type of the request
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Creates a new connection object
     * @return a connection object
     * @throws IOException
     */
    protected HttpURLConnection openConnection() throws IOException {
        HttpURLConnection conn;
        if(url.getProtocol().equals("https")) {
            conn = (HttpsURLConnection)url.openConnection();
        } else {
            conn = (HttpURLConnection)url.openConnection();
        }
        String auth = getAuth();
        if(auth != null) {
            conn.setRequestProperty("Authorization", auth);
        }
        if(contentType != null) {
            conn.setRequestProperty("Content-Type", contentType);
        }
        conn.setRequestMethod(requestMethod);
        conn.setConnectTimeout(ttl);
        conn.setReadTimeout(ttl);

        try {
            onConnected(conn);
        } catch (IOException e) {
            throw e;
        } finally {
            responseCode = conn.getResponseCode();
            responseMessage = conn.getResponseMessage();
        }

        return conn;
    }

    /**
     * Submits data to the connection.
     * Such as in a POST or PUT request.
     * @param connection the connection that will receive the data
     * @param data the data to be sent
     * @throws IOException
     */
    protected void writeData(HttpURLConnection connection, String data) throws IOException {
        connection.setDoOutput(true);
        DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
        dos.writeBytes(data);
        dos.flush();
        dos.close();
    }

    /**
     * Downloads the response to a file
     * @param destination the file where the response will be downloaded to
     * @throws IOException
     */
    public final void download(File destination) throws IOException {
        HttpURLConnection connection = openConnection();

        int responseSize = connection.getContentLength();

        destination.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(destination);

        int updateInterval = 1048 * 50; // send an update each time some bytes have been downloaded
        int updateQueue = 0;
        int bytesRead = 0;

        InputStream in = null;
        try {
            in = new BufferedInputStream(connection.getInputStream());
            byte[] buffer = new byte[4096];
            int n = 0;
            while ((n = in.read(buffer)) != -1) {
                bytesRead += n;
                updateQueue += n;
                out.write(buffer, 0, n);

                // send updates
                if (updateQueue >= updateInterval) {
                    updateQueue = 0;
                    publishProgress(responseSize, bytesRead);
                }
            }
            publishProgress(responseSize, bytesRead);
        } catch (Exception e) {
            if(in != null) in.close();
            out.close();
            connection.disconnect();
            if(destination.exists()) destination.delete();
            throw e;
        }

        if(in != null) in.close();
        out.close();
        connection.disconnect();
    }

    /**
     * Reads the response as a string
     * @return the response string
     * @throws IOException
     */
    public final String read() throws IOException {
        HttpURLConnection connection = openConnection();

        int responseSize = connection.getContentLength();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int updateInterval = 1048 * 50; // send an update each time some bytes have been downloaded
        int updateQueue = 0;
        int bytesRead = 0;

        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(connection.getInputStream());
            int n = 0;
            while ((n = in.read()) != -1) {
                out.write((byte) n);

                // send updates
                if (updateQueue >= updateInterval) {
                    updateQueue = 0;
                    publishProgress(responseSize, bytesRead);
                }

            }
            publishProgress(responseSize, bytesRead);
        } catch (Exception e) {
            throw e;
        } finally {
            if(in != null) in.close();
            out.close();
            connection.disconnect();
        }

        return out.toString("UTF-8");
    }

    /**
     * Sends notifications to the progress listener
     * @param totalBytes the total size of the payload
     * @param bytesRead the number of bytes read
     */
    private void publishProgress(long totalBytes, long bytesRead) {
        if(progressListener == null) return;
        if(totalBytes <= 0 || bytesRead <= 0) {
            progressListener.onIndeterminate();
        } else {
            progressListener.onProgress(totalBytes, bytesRead);
        }

    }

    /**
     * Returns the response code for this request
     * @return the request response code
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Returns the error message for this request
     * @return an error message
     */
    public String getResponseMessage() {
        return responseMessage;
    }

    /**
     * Allows subclasses to perform operations afer the connection has been opened.
     * For example: writing data to the connection.
     *
     * @throws IOException
     */
    protected abstract void onConnected(HttpURLConnection conn) throws IOException;

    public interface OnProgressListener {
        /**
         * Receives progress events
         * @param max the total number of items being processed
         * @param progress the number of items that have been successfully processed
         */
        void onProgress(long max, long progress);

        /**
         * Receives a notice that the progresss is indeterminate
         */
        void onIndeterminate();
    }
}
