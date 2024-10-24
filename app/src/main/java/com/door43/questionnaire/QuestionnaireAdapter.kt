package com.door43.questionnaire

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentQuestionnaireBooleanQuestionBinding
import com.door43.translationstudio.databinding.FragmentQuestionnaireTextQuestionBinding
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import org.unfoldingword.door43client.models.Question

/**
 * Handles the rendering of the questions in a questionnaire
 */
class QuestionnaireAdapter : RecyclerView.Adapter<QuestionnaireAdapter.ViewHolder>() {
    private lateinit var context: Context
    private var page: QuestionnairePage? = null
    private val viewHolders: MutableList<ViewHolder> = ArrayList()
    private var onEventListener: OnEventListener? = null
    private var lastPosition = -1
    private var animateRight = true

    /**
     * Sets the questionnaire page that should be displayed
     * @param page
     * @param animateRight indicates the direction of the question animations
     */
    fun setPage(page: QuestionnairePage?, animateRight: Boolean) {
        this.page = page
        lastPosition = -1
        this.animateRight = animateRight
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val vh: ViewHolder
        when (viewType) {
            TYPE_BOOLEAN -> {
                val booleanBinding = FragmentQuestionnaireBooleanQuestionBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                vh = BooleanViewHolder(booleanBinding)
            }
            else -> {
                val stringBinding = FragmentQuestionnaireTextQuestionBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                vh = StringViewHolder(stringBinding)
            }
        }
        viewHolders.add(vh)
        return vh
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.currentPosition = position
        setAnimation(holder.card, position)
        when (holder.itemViewType) {
            TYPE_BOOLEAN -> onBindBooleanQuestion(holder as BooleanViewHolder, position)
            TYPE_STRING -> onBindStringQuestion(holder as StringViewHolder, position)
            else -> onBindStringQuestion(holder as StringViewHolder, position)
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.clearAnimation()
    }

    /**
     * Animates a view the first time it appears on the screen
     * @param viewToAnimate
     * @param position
     */
    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            val animation = if (animateRight) {
                AnimationUtils.loadAnimation(context, R.anim.slide_in_right)
            } else {
                AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left)
            }
            viewToAnimate.startAnimation(animation)
            lastPosition = position
        }
    }

    override fun getItemCount(): Int {
        return page?.numQuestions ?: 0
    }

    override fun getItemViewType(position: Int): Int {
        if (page?.getQuestion(position)?.inputType == Question.InputType.Boolean) {
            return TYPE_BOOLEAN
        } else if (page?.getQuestion(position)?.inputType == Question.InputType.String) {
            return TYPE_STRING
        }
        return -1
    }

    /**
     * Handles binding to boolean questions
     * @param holder
     * @param position
     */
    private fun onBindBooleanQuestion(holder: BooleanViewHolder, position: Int) {
        page?.getQuestion(position)?.let { question ->
            holder.binding.label.text = question.text
            holder.binding.label.hint = question.help
            holder.binding.radioGroup.setOnCheckedChangeListener(null)

            holder.setRequired(question.isRequired)

            val answerString = getQuestionAnswer(question)
            val answer = answerString.toBoolean()

            holder.binding.radioGroup.clearCheck()

            // provide answer if given
            if (answerString.isNotEmpty()) {
                holder.binding.radioButtonYes.isChecked = answer
                holder.binding.radioButtonNo.isChecked = !answer
            }

            if (isQuestionEnabled(question)) {
                holder.enable()
            } else {
                holder.disable()
            }

            val context = holder.binding.root.context

            holder.binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.radio_button_yes) {
                    saveAnswer(context, question, "true")
                } else {
                    saveAnswer(context, question, "false")
                }
                reload()
            }
        }
    }

    /**
     * Handles binding to string questions
     * @param holder
     * @param position
     */
    private fun onBindStringQuestion(holder: StringViewHolder, position: Int) {
        page?.getQuestion(position)?.let { question ->
            holder.binding.label.text = question.text
            holder.binding.editText.hint = question.help
            holder.binding.editText.removeTextChangedListener(holder.textWatcher)

            holder.setRequired(question.isRequired)

            val answer = getQuestionAnswer(question)
            holder.binding.editText.setText(answer)

            if (isQuestionEnabled(question)) {
                holder.enable()
            } else {
                holder.disable()
            }

            val context = holder.binding.root.context

            holder.textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val a = getQuestionAnswer(question)
                    val wasAnswered = a.isNotEmpty()
                    val isAnswered = s.isNotEmpty()
                    saveAnswer(context, question, s.toString())
                    if (wasAnswered != isAnswered) {
                        reload()
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            }
            holder.binding.editText.addTextChangedListener(holder.textWatcher)
        }
    }

    /**
     * Saves the answer
     * @param question
     * @param answer
     */
    private fun saveAnswer(context: Context, question: Question?, answer: String): Boolean {
        if (onEventListener != null) {
            onEventListener!!.onAnswerChanged(question, answer)
            return true
        } else {
            (context as? Activity)?.let {
                val snack = Snackbar.make(
                    it.findViewById(android.R.id.content),
                    context.resources.getString(R.string.answer_not_saved),
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(
                    snack,
                    it.resources.getColor(R.color.light_primary_text)
                )
                snack.show()
            }
            return false
        }
    }

    /**
     * Re-evaluates the enabled state of each question
     */
    private fun reload() {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            for (vh in viewHolders) {
                val question = page?.getQuestion(vh.currentPosition)
                if (isQuestionEnabled(question)) {
                    vh.enable()
                } else {
                    vh.disable()
                }
            }
        }
    }

    /**
     * Checks if the question dependencies are satisfied for it to be enabled
     * @param question
     * @return
     */
    private fun isQuestionEnabled(question: Question?): Boolean {
        return if (question != null) {
            // Workaround for questions with cycled dependency
            if (question.tdId == question.dependsOn) return true
            val dependsOnAnswer = question.dependsOn > 0
            if (dependsOnAnswer) {
                val parentQuestion = page!!.getQuestionById(question.dependsOn)
                !isAnswerAffirmative(parentQuestion) || isQuestionEnabled(parentQuestion)
            } else true
        } else false
    }

    /**
     * Checks if the question has been answered in the affirmative.
     * This means it was either yes for boolean questions or text was entered for string questions
     * @param question
     * @return
     */
    private fun isAnswerAffirmative(question: Question?): Boolean {
        if (question != null) {
            val answer = getQuestionAnswer(question)
            if (answer.isNotEmpty()) {
                return if (question.inputType == Question.InputType.Boolean) {
                    answer.toBoolean()
                } else {
                    true
                }
            }
        }
        return false
    }

    /**
     * Returns the recorded answer to a question
     * @param question
     * @return
     */
    private fun getQuestionAnswer(question: Question?): String {
        return question?.let { onEventListener?.onGetAnswer(question) } ?: ""
    }

    /**
     * Sets the listener that will be called when certain ui events happen
     * @param onEventListener
     */
    fun setOnEventListener(onEventListener: OnEventListener?) {
        this.onEventListener = onEventListener
    }

    /**
     * An abstract view holder for questions
     */
    abstract class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        abstract val card: CardView
        abstract val requiredView: TextView
        var currentPosition: Int = -1

        abstract fun enable()
        abstract fun disable()

        fun clearAnimation() {
            card.clearAnimation()
        }

        fun setRequired(required: Boolean) {
            if (required) {
                requiredView.visibility = View.VISIBLE
            } else {
                requiredView.visibility = View.GONE
            }
        }
    }

    /**
     * A view holder for boolean questions
     */
    class BooleanViewHolder(
        val binding: FragmentQuestionnaireBooleanQuestionBinding
    ) : ViewHolder(binding.root) {
        override val card: CardView
            get() = binding.card

        override val requiredView: TextView
            get() = binding.required

        private val context = binding.root.context.applicationContext

        init {
            card.setOnClickListener { v ->
                val focusedView = (v.parent as View).findFocus()
                if (focusedView != null) {
                    focusedView.clearFocus()
                    val imm = context.getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager
                    imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
                }
            }
        }

        override fun enable() {
            with(binding) {
                label.setTextColor(context.resources.getColor(R.color.dark_primary_text))
                radioButtonNo.isEnabled = true
                radioButtonYes.isEnabled = true
            }
            requiredView.setTextColor(context.resources.getColor(R.color.red))
        }

        override fun disable() {
            with(binding) {
                label.setTextColor(context.resources.getColor(R.color.dark_disabled_text))
                radioButtonNo.isEnabled = false
                radioButtonYes.isEnabled = false
            }
            requiredView.setTextColor(context.resources.getColor(R.color.light_red))
        }
    }

    /**
     * A view holder for string questions
     */
    class StringViewHolder(
        val binding: FragmentQuestionnaireTextQuestionBinding
    ) : ViewHolder(binding.root) {
        var textWatcher: TextWatcher? = null

        override val card: CardView
            get() = binding.card

        override val requiredView: TextView
            get() = binding.required

        private val context = binding.root.context.applicationContext

        init {
            card.setOnClickListener { v ->
                if (binding.editText.isFocusable) {
                    val focusedView = (v.parent as View).findFocus()
                    focusedView?.clearFocus()
                    binding.editText.requestFocus()
                    val imm = context.getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager
                    imm.showSoftInput(binding.editText, InputMethodManager.SHOW_FORCED)
                }
            }
        }

        override fun enable() {
            with(binding) {
                label.setTextColor(context.resources.getColor(R.color.dark_primary_text))
                label.isFocusable = true
                label.isFocusableInTouchMode = true
                editText.isEnabled = true
                editText.setTextColor(context.resources.getColor(R.color.dark_primary_text))
                editText.setHintTextColor(context.resources.getColor(R.color.transparent))
                editText.isFocusable = true
                editText.isFocusableInTouchMode = true
            }
            requiredView.setTextColor(context.resources.getColor(R.color.red))
        }

        override fun disable() {
            with(binding) {
                label.setTextColor(context.resources.getColor(R.color.dark_disabled_text))
                label.isFocusable = false
                label.isFocusableInTouchMode = false
                editText.isEnabled = false
                editText.setTextColor(context.resources.getColor(R.color.dark_disabled_text))
                editText.setHintTextColor(context.resources.getColor(R.color.half_transparent))
                editText.isFocusable = false
                editText.isFocusableInTouchMode = false
            }
            requiredView.setTextColor(context.resources.getColor(R.color.light_red))
        }
    }

    /**
     * The interface by which answers are sent/received
     */
    interface OnEventListener {
        fun onGetAnswer(question: Question?): String?
        fun onAnswerChanged(question: Question?, answer: String?)
    }

    companion object {
        val TAG: String = QuestionnaireAdapter::class.java.simpleName
        private const val TYPE_BOOLEAN = 1
        private const val TYPE_STRING = 2
    }
}
