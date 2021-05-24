package com.door43.translationstudio.tasks;

import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.tasks.io.OkHttpRequest;
import com.door43.translationstudio.tasks.io.Request;
import com.door43.translationstudio.tasks.io.RequestAPI;
import com.door43.translationstudio.ui.SettingsActivity;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import org.unfoldingword.gogsclient.GogsAPI;
import org.unfoldingword.gogsclient.Response;
import org.unfoldingword.gogsclient.Token;
import org.unfoldingword.gogsclient.User;

import java.util.List;

/**
 * Created by joel on 4/15/16.
 */
public class LoginDoor43Task extends ManagedTask {
    public static final String TASK_ID = "login_door43";
    private final String password;
    private final String username;
    private final String tokenName;
    private final String fullName;
    private User user = null;
    private String apiUrl = App.getUserString(SettingsActivity.KEY_PREF_GOGS_API, R.string.pref_default_gogs_api);

    /**
     * Logs into a door43 account
     * @param username
     * @param password
     */
    public LoginDoor43Task(String username, String password) {
        this.username = username;
        this.password = password;
        this.tokenName = getTokenStub();
        this.fullName = null;
    }

    /**
     * Logs into a door43 account. If no full_name exists on the account the provided one will be added
     * @param username
     * @param password
     * @param fullName the name to add to the account if one does not areadly exist
     */
    public LoginDoor43Task(String username, String password, String fullName) {
        this.username = username;
        this.password = password;
        this.tokenName = getTokenStub();
        this.fullName = fullName;
    }


    @Override
    public void start() {
        GogsAPI api = new GogsAPI(this.apiUrl);
        User authUser = new User(this.username, this.password);

        // get user
        this.user = api.getUser(authUser, authUser);
        if(this.user != null) {
            RequestAPI customRequester = new OkHttpRequest(this.apiUrl);
            int tokenId = getTokenId(tokenName, authUser, customRequester);
            if (tokenId != -1) {
                // Delete (if exists) matching token for this device on server
                deleteToken(tokenId, authUser, customRequester);
            }

            // Create a new token
            Token t = new Token(tokenName);
            this.user.token = api.createToken(t, authUser);

            // validate access token
            if (this.user.token == null) {
                this.user = null;
                Response response = api.getLastResponse();
                Logger.w(LoginDoor43Task.class.getName(), "gogs api responded with " + response.code + ": " + response.toString(), response.exception);
                return;
            }

            // set missing full_name
            if(this.user.fullName == null || this.user.fullName.isEmpty()
                    && (this.fullName != null && !this.fullName.isEmpty())) {
                this.user.fullName = this.fullName;
                User updatedUser = api.editUser(this.user, authUser);
                if(updatedUser == null) {
                    Response response = api.getLastResponse();
                    Logger.w(LoginDoor43Task.class.getName(), "The full_name could not be updated gogs api responded with " + response.code + ": " + response.toString(), response.exception);
                }
            }
        }
    }

    /**
     * Returns the logged in user
     * @return
     */
    public User getUser() {
        return user;
    }

    public static String getTokenStub() {
        String defaultTokenName = App.context().getResources().getString(R.string.gogs_token_name);
        String androidId = Settings.Secure.getString(App.context().getContentResolver(), Settings.Secure.ANDROID_ID);
        String nickname = Settings.Secure.getString(App.context().getContentResolver(), "bluetooth_name");
        if (nickname == null || nickname.isEmpty()) {
            nickname = Build.MODEL;
        }
        String tokenSuffix = String.format("%s_%s__%s", Build.MANUFACTURER, nickname, androidId);
        return defaultTokenName + "__" + tokenSuffix;
    }

    private int getTokenId(String tokenName, User userAuth, RequestAPI requester) {
        int tokenId = -1;
        String urlPath = String.format("/users/%s/tokens", userAuth.getUsername());
        Response tokenResponse = requester.get(urlPath, userAuth);

        if(tokenResponse.code == 200) {
            try {
                JSONArray data = new JSONArray(tokenResponse.data);
                for(int i = 0; i < data.length(); i ++) {
                    String tkName = data.getJSONObject(i).getString("name");

                    if(tkName.equals(tokenName)) {
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

    private void deleteToken(int tokenId, User userAuth, RequestAPI requester) {
        String urlPath = String.format("/users/%s/tokens/%s", userAuth.getUsername(), tokenId);
        Response response = requester.delete(urlPath, userAuth);

        if(response.code != 204) {
            Logger.w(LoginDoor43Task.class.getName(), "delete access token - gogs api responded with code " + response.code, response.exception);
        }
    }
}
