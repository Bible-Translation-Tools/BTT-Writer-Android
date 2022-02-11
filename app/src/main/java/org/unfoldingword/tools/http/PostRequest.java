package org.unfoldingword.tools.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Implements a post request
 */
public class PostRequest extends Request {
    private final String data;

    /**
     * Creates a new post request
     * @param url the url receiving the post request
     * @param data the post data
     */
    public PostRequest(URL url, String data) {
        super(url, "POST");
        this.data = data;
    }

    @Override
    protected void onConnected(HttpURLConnection conn) throws IOException {
        writeData(conn, data);
    }
}
