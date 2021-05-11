package com.door43.translationstudio.tasks;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.tasks.io.OkHttpRequest;
import com.door43.translationstudio.ui.SettingsActivity;

import org.json.JSONArray;
import org.json.JSONException;


import org.unfoldingword.gogsclient.Token;
import org.unfoldingword.gogsclient.User;
import org.unfoldingword.tools.logger.Logger;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import java.io.IOException;

import okhttp3.Response;

public class LogoutTask extends ManagedTask {
    public static final String TASK_ID = "logout_task";
    private User user;
    private int tokenId = -1;
    private String tokenName;
    private String tokenSha1;

    // logout with token id
//    public LogoutTask (User user, int tokenId) {
//        this.user = user;
//        this.tokenId = tokenId;
//        this.tokenName = user.token.getName();
//        this.tokenSha1 = user.token.toString();
//    }

    // logout with token name
    public LogoutTask (User user) {
        this.user = user;
        this.tokenName = user.token.getName();
        this.tokenSha1 = user.token.toString();
    }

    @Override
    public void start() {
//        Request request = new Request(App.getUserString(SettingsActivity.KEY_PREF_GOGS_API, R.string.pref_default_gogs_api));
        String apiServer = App.getUserString(SettingsActivity.KEY_PREF_GOGS_API, R.string.pref_default_gogs_api);
        OkHttpRequest request = new OkHttpRequest(apiServer);

        user.password = tokenSha1;
        user.token = null;

        String requestPath = String.format("users/%s/tokens", user.getUsername());
        Response tokenResponse = request.get(requestPath, user);
        if(tokenResponse.code() == 200) {
            try {
                JSONArray data = new JSONArray(tokenResponse.body().string());
                for(int i = 0; i < data.length(); i ++) {
                    String tkName = data.getJSONObject(i).getString("name");

                    if(tkName.equals(this.tokenName)) {
                        tokenId = data.getJSONObject(i).getInt("id");
                        break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                Logger.w(LogoutTask.class.getName(),"Response exception", e);
            }
        }

        if (tokenId == -1) return;

        String path = String.format("users/%s/tokens/%s", user.getUsername(), tokenId);
        Response response = request.delete(path, user);

        if(response.code() != 204) {
            Logger.w(LoginDoor43Task.class.getName(), "delete token - gogs api responded with code " + response.code() + " - " + response.toString());
            Logger.w(
                    LoginDoor43Task.class.getName()+"."+"logOut",
                    "User.username: " + user.getUsername() + "\n" +
                            "User.password/token: " + user.password + "\n" +
                            "Token name: " + user.token.getName() + "\n" +
                            "Url: " + App.getUserString(SettingsActivity.KEY_PREF_GOGS_API, R.string.pref_default_gogs_api) + path
            );
        }
    }
}
