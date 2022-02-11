package org.unfoldingword.tools.http;

/* Source: https://github.com/unfoldingWord-dev/android-http/tree/2.4.2 */

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Implements a put request
 */
public class PutRequest extends Request {
    private final String data;

    /**
     * Creates a new put request
     * @param url the url receiving the put request
     * @param data the put data
     */
    public PutRequest(URL url, String data) {
        super(url, "PUT");
        this.data = data;
    }

    @Override
    protected void onConnected(HttpURLConnection conn) throws IOException {
        writeData(conn, data);
    }
}
