package com.door43.translationstudio.ui

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import com.door43.translationstudio.App.Companion.context
import com.door43.translationstudio.App.Companion.getProfile
import com.door43.translationstudio.App.Companion.userPreferences
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.databinding.ActivityProfileBinding
import com.door43.widget.ViewUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProfileActivity : BaseActivity() {
    @Inject lateinit var profile: Profile
    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            account.loginDoor43.setOnClickListener {
                val intent = Intent(this@ProfileActivity, LoginDoor43Activity::class.java)
                startActivity(intent)
            }
            account.registerDoor43.setOnClickListener {
                val defaultAccountCreateUrl = context()!!.resources.getString(
                    R.string.pref_default_create_account_url
                )
                val accountCreateUrl = userPreferences.getString(
                    SettingsActivity.KEY_PREF_CREATE_ACCOUNT_URL,
                    defaultAccountCreateUrl
                )
                val uri = Uri.parse(accountCreateUrl)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            }
            account.registerOffline.setOnClickListener {
                val intent = Intent(this@ProfileActivity, RegisterOfflineActivity::class.java)
                startActivity(intent)
            }
            account.cancelButton.setOnClickListener { finish() }
        }

        val moreButton = findViewById<ImageButton>(R.id.action_more)
        moreButton?.setOnClickListener { v: View? ->
            val moreMenu = PopupMenu(this@ProfileActivity, v)
            ViewUtil.forcePopupMenuIcons(moreMenu)
            moreMenu.menuInflater.inflate(R.menu.menu_profile, moreMenu.menu)
            moreMenu.setOnMenuItemClickListener { item: MenuItem ->
                val id = item.itemId
                if (id == R.id.action_settings) {
                    val intent = Intent(this@ProfileActivity, SettingsActivity::class.java)
                    startActivity(intent)
                    return@setOnMenuItemClickListener true
                } else {
                    return@setOnMenuItemClickListener false
                }
            }
            moreMenu.show()
        }
    }

    override fun onResume() {
        super.onResume()

        if (profile.loggedIn) {
            val intent = Intent(this, TermsOfUseActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        val currentUser: String
            get() {
                val currentUserProfile = getProfile()
                var userName: String? = null
                if (currentUserProfile.gogsUser != null) {
                    userName = currentUserProfile.gogsUser!!.username
                }
                if (userName == null) {
                    userName = currentUserProfile.fullName
                }

                if (userName == null) {
                    userName = ""
                }
                return userName
            }

        /**
         * Displays the privacy notice
         * @param listener if set the dialog will become a confirmation dialog
         */
        fun showPrivacyNotice(context: Activity, listener: DialogInterface.OnClickListener?) {
            val privacy = AlertDialog.Builder(
                context, R.style.AppTheme_Dialog
            )
                .setTitle(R.string.privacy_notice)
                .setIcon(R.drawable.ic_info_secondary_24dp)
                .setMessage(R.string.publishing_privacy_notice)

            if (listener != null) {
                privacy.setPositiveButton(R.string.label_continue, listener)
                privacy.setNegativeButton(R.string.title_cancel, null)
            } else {
                privacy.setPositiveButton(R.string.dismiss, null)
            }
            privacy.show()
        }
    }
}
