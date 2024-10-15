package com.door43.translationstudio.ui.publish

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.tasks.ValidationTask
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.TaskManager
import java.security.InvalidParameterException
import javax.inject.Inject

/**
 * Created by joel on 9/20/2015.
 */
@AndroidEntryPoint
class ValidationFragment : PublishStepFragment(), ManagedTask.OnFinishedListener,
    ValidationAdapter.OnClickListener {
    private var mLoadingLayout: LinearLayout? = null
    private var mRecyclerView: RecyclerView? = null
    private var mValidationAdapter: ValidationAdapter? = null

    @Inject
    lateinit var typography: Typography

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_publish_validation_list, container, false)

        val args = arguments
        val targetTranslationId = args!!.getString(PublishActivity.EXTRA_TARGET_TRANSLATION_ID)
        val sourceTranslationId = args.getString(ARG_SOURCE_TRANSLATION_ID)
        if (targetTranslationId == null) {
            throw InvalidParameterException("a valid target translation id is required")
        }
        if (sourceTranslationId == null) {
            throw InvalidParameterException("a valid source translation id is required")
        }

        mRecyclerView = rootView.findViewById<View>(R.id.recycler_view) as RecyclerView
        val linearLayoutManager = LinearLayoutManager(activity)
        mRecyclerView!!.layoutManager = linearLayoutManager
        mRecyclerView!!.itemAnimator = DefaultItemAnimator()
        mValidationAdapter = ValidationAdapter(typography!!)
        mValidationAdapter!!.setOnClickListener(this)
        mRecyclerView!!.adapter = mValidationAdapter
        mLoadingLayout = rootView.findViewById<View>(R.id.loading_layout) as LinearLayout

        // display loading view
        mRecyclerView!!.visibility = View.GONE
        mLoadingLayout!!.visibility = View.VISIBLE

        // start task to validate items
        var task = TaskManager.getTask(ValidationTask.TASK_ID) as ValidationTask
        if (task != null) {
            task.addOnFinishedListener(this)
        } else {
            // start new task
            task = ValidationTask(activity, targetTranslationId, sourceTranslationId)
            task.addOnFinishedListener(this)
            TaskManager.addTask(task)
        }

        return rootView
    }

    override fun onTaskFinished(task: ManagedTask) {
        TaskManager.clearTask(task)
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            mValidationAdapter!!.setValidations((task as ValidationTask).validations.toList())
            mRecyclerView!!.visibility = View.VISIBLE
            mLoadingLayout!!.visibility = View.GONE
            // TODO: animate
        }
    }

    override fun onClickReview(targetTranslationId: String?, chapterId: String?, frameId: String?) {
        openReview(targetTranslationId, chapterId, frameId)
    }

    override fun onClickNext() {
        listener.nextStep()
    }

    override fun onDestroy() {
        mValidationAdapter!!.setOnClickListener(null)
        super.onDestroy()
    }
}
