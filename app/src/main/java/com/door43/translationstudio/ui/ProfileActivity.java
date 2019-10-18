package com.door43.translationstudio.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.MenuItem;
import android.view.View;

import android.widget.ImageButton;
import android.widget.PopupMenu;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Profile;
import com.door43.widget.ViewUtil;

public class ProfileActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        View loginDoor43 = findViewById(R.id.login_door43);
        View registerDoor43 = findViewById(R.id.register_door43);
        View registerOffline = findViewById(R.id.register_offline);

        loginDoor43.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileActivity.this, LoginDoor43Activity.class);
                startActivity(intent);
            }
        });
        registerDoor43.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileActivity.this, RegisterDoor43Activity.class);
                startActivity(intent);
            }
        });
        registerOffline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileActivity.this, RegisterOfflineActivity.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ImageButton moreButton = (ImageButton)findViewById(R.id.action_more);
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu moreMenu = new PopupMenu(ProfileActivity.this, v);
                ViewUtil.forcePopupMenuIcons(moreMenu);
                moreMenu.getMenuInflater().inflate(R.menu.menu_profile, moreMenu.getMenu());
                moreMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_settings:
                                Intent intent = new Intent(ProfileActivity.this, SettingsActivity.class);
                                startActivity(intent);
                                return true;
                        }
                        return false;
                    }
                });
                moreMenu.show();
            }
        });



    }

    @Override
    public void onResume() {
        super.onResume();

        if (App.getProfile() != null) {
            Intent intent = new Intent(this, TermsOfUseActivity.class);
            startActivity(intent);
            finish();
        }
    }

    /**
     * Displays the privacy notice
     * @param listener if set the dialog will become a confirmation dialog
     */
    public static void showPrivacyNotice(Activity context, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder privacy = new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(R.string.privacy_notice)
                .setIcon(R.drawable.ic_info_black_24dp)
                .setMessage(R.string.publishing_privacy_notice);

        if(listener != null) {
            privacy.setPositiveButton(R.string.label_continue, listener);
            privacy.setNegativeButton(R.string.title_cancel, null);
        } else {
            privacy.setPositiveButton(R.string.dismiss, null);
        }
        privacy.show();
    }

    public static String getCurrentUser() {
        Profile currentUserProfile = App.getProfile();
        String userName = null;
        if(currentUserProfile.gogsUser != null) {
            userName = currentUserProfile.gogsUser.getUsername();
        }
        if(userName == null) {
            userName = currentUserProfile.getFullName();
        }

        if(userName == null) {
            userName = "";
        }
        return userName;
    }
}
