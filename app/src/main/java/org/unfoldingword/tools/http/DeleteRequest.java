package org.unfoldingword.tools.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Implements a delete request
 */
public class DeleteRequest extends Request {

    /**
     * Creates a new delete request
     * @param url the url that will receive the delete request
     */
    public DeleteRequest(URL url) {
        super(url, "DELETE");
    }

    @Override
    protected void onConnected(HttpURLConnection conn) throws IOException {

    }
}
