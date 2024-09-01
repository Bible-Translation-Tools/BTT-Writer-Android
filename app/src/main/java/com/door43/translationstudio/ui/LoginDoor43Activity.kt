package com.door43.translationstudio.ui

import android.app.ProgressDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.databinding.ActivityLoginDoor43Binding
import com.door43.translationstudio.tasks.LoginDoor43Task
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.TaskManager
import javax.inject.Inject

@AndroidEntryPoint
class LoginDoor43Activity : AppCompatActivity(), ManagedTask.OnFinishedListener {
    @Inject
    lateinit var profile: Profile
    private lateinit var binding: ActivityLoginDoor43Binding
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginDoor43Binding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            account.cancelButton.setOnClickListener { finish() }
            account.okButton.setOnClickListener {
                closeKeyboard(this@LoginDoor43Activity)
                val username = account.username.text.toString()
                val password = account.password.text.toString()
                val fullName = profile.fullName
                val task = LoginDoor43Task(username, password, fullName)
                showProgressDialog()
                task.addOnFinishedListener(this@LoginDoor43Activity)
                TaskManager.addTask(task, LoginDoor43Task.TASK_ID)
            }
        }

        val task = TaskManager.getTask(LoginDoor43Task.TASK_ID) as? LoginDoor43Task
        if (task != null) {
            showProgressDialog()
            task.addOnFinishedListener(this)
        }
    }

    override fun onTaskFinished(task: ManagedTask) {
        TaskManager.clearTask(task)

        progressDialog?.dismiss()

        val user = (task as? LoginDoor43Task)?.user
        if (user != null) {
            // save gogs user to profile
            if (user.fullName.isNullOrEmpty()) {
                // TODO: 4/15/16 if the fullname has not been set we need to ask for it
                // this is our quick fix to get the full name for now
                user.fullName = user.username
            }
            profile.login(user.fullName, user)
            finish()
        } else {
            val networkAvailable = isNetworkAvailable

            // login failed
            val hand = Handler(Looper.getMainLooper())
            hand.post {
                val messageId =
                    if (networkAvailable) R.string.double_check_credentials else R.string.internet_not_available
                AlertDialog.Builder(this@LoginDoor43Activity, R.style.AppTheme_Dialog)
                    .setTitle(R.string.error)
                    .setMessage(messageId)
                    .setPositiveButton(R.string.label_ok, null)
                    .show()
            }
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
}
