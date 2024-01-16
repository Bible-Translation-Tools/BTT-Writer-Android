package org.unfoldingword.tools.http;

/* Source: https://github.com/unfoldingWord-dev/android-http/tree/2.4.2 */

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Implements a get request
 */
public class GetRequest extends Request {

    public GetRequest(URL url) {
        super(url, "GET");
    }

    @Override
    protected void onConnected(HttpURLConnection conn) throws IOException {

    }
}
