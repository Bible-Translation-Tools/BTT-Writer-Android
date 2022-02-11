package org.unfoldingword.tools.http;

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
