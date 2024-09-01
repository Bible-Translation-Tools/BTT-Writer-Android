package com.door43.translationstudio.tasks;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.tasks.io.OkHttpRequest;
import com.door43.translationstudio.tasks.io.RequestAPI;
import com.door43.translationstudio.ui.SettingsActivity;

import org.json.JSONArray;
import org.json.JSONException;

import org.unfoldingword.gogsclient.Response;
import org.unfoldingword.gogsclient.User;
import org.unfoldingword.tools.logger.Logger;
import org.unfoldingword.tools.taskmanager.ManagedTask;

public class LogoutTask extends ManagedTask {
    public static final String TASK_ID = "logout_task";

    private User user;
    private String tokenName;
    private String tokenSha1;

    public LogoutTask (User user) {
        this.user = user;
        this.tokenName = (user != null && user.token != null) ? user.token.getName() : null;
        this.tokenSha1 = (user != null && user.token != null) ? user.token.toString() : null;
    }

    @Override
    public void start() {
        if (user == null) {
            // local user (non-server account)
            return;
        }

        // uses Basic authorization scheme, token should be null
        user.password = tokenSha1;
        user.token = null;

        String apiUrl = App.getUserString(SettingsActivity.KEY_PREF_GOGS_API, R.string.pref_default_gogs_api);
        RequestAPI requester = new OkHttpRequest(apiUrl);

        int tokenId = getTokenId(requester);
        if (tokenId < 0) {
            return;
        }

        deleteToken(tokenId, requester);
    }

    private int getTokenId(RequestAPI requester) {
        int tokenId = -1;
        String requestPath = String.format("/users/%s/tokens", user.getUsername());

        Response tokenResponse = requester.get(requestPath, user);
        if(tokenResponse.code == 200) {
            try {
                JSONArray data = new JSONArray(tokenResponse.data);
                for(int i = 0; i < data.length(); i ++) {
                    String tkName = data.getJSONObject(i).getString("name");

                    if(tkName.equals(this.tokenName)) {
                        tokenId = data.getJSONObject(i).getInt("id");
                        break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return tokenId;
    }

    private void deleteToken(int tokenId, RequestAPI requester) {
        String path = String.format("/users/%s/tokens/%s", user.getUsername(), tokenId);
        Response response = requester.delete(path, user);

        if(response.code != 200 || response.code != 204) {
            Logger.w(LogoutTask.class.getName(), "delete access token - gogs api responded with code " + response.code, response.exception);
        }
    }
}
