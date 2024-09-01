package com.door43.translationstudio.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.door43.translationstudio.App.Companion.context
import com.door43.translationstudio.App.Companion.userPreferences
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.DialogDoor43LoginBinding
import com.door43.translationstudio.ui.LoginDoor43Activity
import com.door43.translationstudio.ui.SettingsActivity

/**
 * This dialog provides options for the user to login into or create a Door43 account
 * and connect it to their profile.
 * This should be used anywhere a Door43 account is required but does not exist.
 */
class Door43LoginDialog : DialogFragment() {

    private var _binding: DialogDoor43LoginBinding? = null
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogDoor43LoginBinding.inflate(inflater, container, false)

        with(binding) {
            registerDoor43.setOnClickListener {
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
                dismiss()
            }
            loginDoor43.setOnClickListener {
                val intent = Intent(activity, LoginDoor43Activity::class.java)
                startActivity(intent)
                dismiss()
            }
            cancelButton.setOnClickListener { dismiss() }
        }
        return binding.root
    }

    companion object {
        const val TAG: String = "door43_login_options_dialog"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
