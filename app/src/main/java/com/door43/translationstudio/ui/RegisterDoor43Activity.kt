package com.door43.translationstudio.ui

import android.app.ProgressDialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.databinding.ActivityRegisterDoor43Binding
import com.door43.translationstudio.tasks.RegisterDoor43Task
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.TaskManager
import javax.inject.Inject

@AndroidEntryPoint
class RegisterDoor43Activity : AppCompatActivity(), ManagedTask.OnFinishedListener {
    @Inject
    lateinit var profile: Profile

    private lateinit var binding: ActivityRegisterDoor43Binding
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterDoor43Binding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            account.showPasswordButton.setOnClickListener {
                val inputType = if (account.showPasswordButton.isChecked) {
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                }

                account.password.inputType = InputType.TYPE_CLASS_TEXT or inputType
                account.password.setSelection(account.password.text.length)
                account.password2.inputType = InputType.TYPE_CLASS_TEXT or inputType
                account.password2.setSelection(account.password2.text.length)
            }
            account.cancelButton.setOnClickListener { finish() }
            account.okButton.setOnClickListener {
                closeKeyboard(this@RegisterDoor43Activity)

                val fullName = account.fullName.text.toString().trim()
                val username = account.username.text.toString()
                val email = account.email.text.toString().trim()
                val password = account.password.text.toString()
                val password2 = account.password2.text.toString()

                if (fullName.isNotEmpty() &&
                    username.trim().isNotEmpty() &&
                    email.trim().isNotEmpty() &&
                    password.trim().isNotEmpty()
                ) {
                    if ((password == password2)) {
                        ProfileActivity.showPrivacyNotice(
                            this@RegisterDoor43Activity
                        ) { _, _ ->
                            val task =
                                RegisterDoor43Task(username, password, fullName, email)
                            showProgressDialog()
                            task.addOnFinishedListener(this@RegisterDoor43Activity)
                            TaskManager.addTask(task, RegisterDoor43Task.TASK_ID)
                        }
                    } else {
                        val snack = Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.passwords_mismatch,
                            Snackbar.LENGTH_SHORT
                        )
                        ViewUtil.setSnackBarTextColor(
                            snack,
                            resources.getColor(R.color.light_primary_text)
                        )
                        snack.show()
                    }
                } else {
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.complete_required_fields,
                        Snackbar.LENGTH_SHORT
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                }
            }

            account.privacyNotice.setOnClickListener {
                ProfileActivity.showPrivacyNotice(
                    this@RegisterDoor43Activity,
                    null
                )
            }
        }

        val task = TaskManager.getTask(RegisterDoor43Task.TASK_ID) as? RegisterDoor43Task
        if (task != null) {
            showProgressDialog()
            task.addOnFinishedListener(this)
        }
    }

    private fun showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(this).apply {
                setTitle(resources.getString(R.string.logging_in))
                setMessage(resources.getString(R.string.please_wait))
                isIndeterminate = true
                show()
            }
        }
    }

    override fun onTaskFinished(task: ManagedTask) {
        TaskManager.clearTask(task)

        progressDialog?.dismiss()

        val userTask = task as? RegisterDoor43Task

        val user = userTask?.user
        if (user != null) {
            // save gogs user to profile
            profile.login(user.fullName, user)
            finish()
        } else {
            val error = if (userTask?.error == null) {
                resources.getString(R.string.registration_failed)
            } else {
                task.error
            }
            // registration failed
            val hand = Handler(Looper.getMainLooper())
            hand.post {
                AlertDialog.Builder(this@RegisterDoor43Activity, R.style.AppTheme_Dialog)
                    .setTitle(R.string.error)
                    .setMessage(error)
                    .setPositiveButton(R.string.label_ok, null)
                    .show()
            }
        }
    }
}
