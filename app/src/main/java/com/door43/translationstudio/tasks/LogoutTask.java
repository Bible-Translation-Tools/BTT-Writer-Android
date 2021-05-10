package com.door43.translationstudio.tasks;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.tasks.io.Request;
import com.door43.translationstudio.ui.SettingsActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.unfoldingword.gogsclient.Response;
import org.unfoldingword.gogsclient.Token;
import org.unfoldingword.gogsclient.User;
import org.unfoldingword.tools.logger.Logger;
import org.unfoldingword.tools.taskmanager.ManagedTask;

public class LogoutTask extends ManagedTask {
    public static final String TASK_ID = "logout_task";
    private User user;
    private String tokenId;
    private String token;

    // logout with token id
    public LogoutTask (User user, int tokenId) {
        this.user = user;
        this.tokenId = String.valueOf(tokenId);
        this.token = user.token.toString();
    }

    // logout with token name
    public LogoutTask (User user) {
        this.user = user;
        this.tokenId = user.token.getName();
        this.token = user.token.toString();
    }

    @Override
    public void start() {
//        Request request = new Request(App.getUserString(SettingsActivity.KEY_PREF_GOGS_API, R.string.pref_default_gogs_api));
        Request request = new Request("https://content.bibletranslationtools.org/api/v1");
        user.password = token;

        Response tokenResponse = request.request(String.format("/users/%s/tokens", user.getUsername()), user, null, "GET");
        if(tokenResponse.code == 200 && tokenResponse.data != null) {
            try {
                JSONArray data = new JSONArray(tokenResponse.data);
                for(int i = 0; i < data.length(); i ++) {
                    String tokenName = data.getJSONObject(i).getString("name");

                    if(tokenName.equals(tokenId)) {
                        tokenId = data.getJSONObject(i).getString("id");
                        break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String path = String.format("/users/%s/tokens/%s", user.getUsername(), tokenId);
        Response response = request.request(path, user, null, "DELETE");

        if(response.code != 204) {
            Logger.w(LoginDoor43Task.class.getName(), "delete token - gogs api responded with code " + response.code + " - " + response.toString(), response.exception);
            Logger.w(
                    LoginDoor43Task.class.getName()+"."+"logOut",
                    "User.username: " + user.getUsername() + "\n" +
                            "User.password/token: " + user.token.toString() + "\n" +
                            "Token name: " + user.token.getName() + "\n" +
                            "Url: " + App.getUserString(SettingsActivity.KEY_PREF_GOGS_API, R.string.pref_default_gogs_api) + path
            );
        }
    }
}
