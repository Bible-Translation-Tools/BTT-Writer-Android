package com.door43.translationstudio.ui.dialogs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.fragment.app.DialogFragment;

import com.door43.translationstudio.App;
import com.door43.translationstudio.ui.LoginDoor43Activity;
import com.door43.translationstudio.R;
import com.door43.translationstudio.ui.SettingsActivity;

/**
 * This dialog provides options for the user to login into or create a Door43 account
 * and connect it to their profile.
 * This should be used anywhere a Door43 account is required but does not exist.
 */
public class Door43LoginDialog extends DialogFragment {
    public static final String TAG = "door43_login_options_dialog";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = inflater.inflate(R.layout.dialog_door43_login, container, false);

        v.findViewById(R.id.register_door43).setOnClickListener(v13 -> {
            String defaultAccountCreateUrl = App.context().getResources().getString(
                    R.string.pref_default_create_account_url);
            String accountCreateUrl = App.getUserPreferences().getString(
                    SettingsActivity.KEY_PREF_CREATE_ACCOUNT_URL,
                    defaultAccountCreateUrl);
            Uri uri = Uri.parse(accountCreateUrl);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            dismiss();
        });
        v.findViewById(R.id.login_door43).setOnClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), LoginDoor43Activity.class);
            startActivity(intent);
            dismiss();
        });
        v.findViewById(R.id.cancel_button).setOnClickListener(v12 -> dismiss());

        return v;
    }
}
