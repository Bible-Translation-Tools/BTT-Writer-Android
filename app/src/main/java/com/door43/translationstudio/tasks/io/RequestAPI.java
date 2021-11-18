package com.door43.translationstudio.tasks.io;

import org.unfoldingword.gogsclient.Response;
import org.unfoldingword.gogsclient.User;

public interface RequestAPI {
    public Response get(String path, User userAuth);
    public Response post(String path, User userAuth, String postData);
    public Response delete(String path, User userAuth);
    public Response put(String path, User userAuth,  String postData);
}
