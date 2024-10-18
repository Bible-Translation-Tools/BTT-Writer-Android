package com.door43.questionnaire

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.ActivityQuestionnaireBinding
import com.door43.translationstudio.ui.BaseActivity
import org.unfoldingword.tools.logger.Logger

/**
 * Activity for presenting a questionnaire to the user
 */
abstract class QuestionnaireActivity : BaseActivity(), QuestionnaireAdapter.OnEventListener {
    private var currentPage = 0
    private var questionnaireFinished = false
    private lateinit var pager: QuestionnairePager
    private val adapter by lazy { QuestionnaireAdapter() }

    private lateinit var binding: ActivityQuestionnaireBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestionnaireBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        pager = questionnaire ?: run {
            Logger.e(
                this.javaClass.name,
                "Cannot begin the questionnaire because no questionnaire was found."
            )
            return
        }

        if (savedInstanceState != null) {
            currentPage = savedInstanceState.getInt(STATE_PAGE, 0)
            questionnaireFinished = savedInstanceState.getBoolean(STATE_FINISHED, false)
        }

        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        with(binding) {
            recyclerView.layoutManager = LinearLayoutManager(this@QuestionnaireActivity)
            recyclerView.itemAnimator = DefaultItemAnimator()
            adapter.setOnEventListener(this@QuestionnaireActivity)
            recyclerView.adapter = adapter

            previousButton.setOnClickListener { goToPage(currentPage - 1) }
            nextButton.setOnClickListener(View.OnClickListener { // validate page completion
                val page = pager.getPage(currentPage)
                if (page != null) {
                    for (q in page.getQuestions()) {
                        val answer = onGetAnswer(q)
                        if (q.isRequired && answer.isNullOrEmpty()) {
                            AlertDialog.Builder(this@QuestionnaireActivity, R.style.AppTheme_Dialog)
                                .setTitle(R.string.missing_question_answer)
                                .setMessage(q.text)
                                .setPositiveButton(R.string.dismiss, null)
                                .show()
                            return@OnClickListener
                        }
                    }
                }
                if (onLeavePage(page)) {
                    nextPage()
                }
            })
            doneButton.setOnClickListener { onFinished() }
        }
    }

    override fun onResume() {
        super.onResume()
        goToPage(currentPage)
    }

    /**
     * Move to the next page
     */
    protected fun nextPage() {
        goToPage(currentPage + 1)
    }

    protected abstract val questionnaire: QuestionnairePager?

    /**
     * Called when the user navigates to the next page of questions
     */
    protected abstract fun onLeavePage(page: QuestionnairePage?): Boolean

    /**
     * Called when the questionnaire has been completed
     */
    protected abstract fun onFinished()

    /**
     * Moves the questionnaire back to the beginning
     */
    protected fun restartQuestionnaire() {
        currentPage = -1
        goToPage(currentPage)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PAGE, currentPage)
        outState.putBoolean(STATE_FINISHED, questionnaireFinished)
        super.onSaveInstanceState(outState)
    }

    /**
     * Moves to a specific question page
     * @param page
     */
    private fun goToPage(page: Int) {
        with(binding) {
            recyclerView.scrollToPosition(0)

            val animateRight = page > currentPage
            currentPage = if (page >= pager.size()) {
                pager.size() - 1
            } else if (page < 0) {
                0
            } else {
                page
            }

            val titleFormat = resources.getString(R.string.questionnaire_title)
            val title = String.format(titleFormat, currentPage + 1, pager.size())
            setTitle(title)
            adapter.setPage(pager.getPage(currentPage), animateRight)

            // update controls
            previousButton.visibility = View.GONE
            doneButton.visibility = View.GONE
            nextButton.visibility = View.GONE
            if (currentPage == 0 && pager.size() > 1) {
                nextButton.visibility = View.VISIBLE
            } else if (currentPage == pager.size() - 1) {
                previousButton.visibility = View.VISIBLE
                doneButton.visibility = View.VISIBLE
            } else {
                nextButton.visibility = View.VISIBLE
                previousButton.visibility = View.VISIBLE
            }

            closeKeyboard(this@QuestionnaireActivity)
        }
    }

    override fun onDestroy() {
        adapter.setOnEventListener(null)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            confirmExit()
        }
        return true
    }

    private fun confirmExit() {
        // display confirmation before closing the app
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.confirm)
            .setMessage(R.string.confirm_leave_questionnaire)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            confirmExit()
        }
    }

    companion object {
        const val EXTRA_MESSAGE: String = "message"

        private const val STATE_PAGE = "current_page"
        private const val STATE_FINISHED = "finished"
    }
}

