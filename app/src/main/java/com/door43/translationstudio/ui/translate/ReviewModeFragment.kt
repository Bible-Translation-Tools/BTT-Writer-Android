package com.door43.translationstudio.ui.translate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Util
import com.door43.translationstudio.databinding.FragmentResourcesExampleItemBinding
import com.door43.translationstudio.databinding.FragmentResourcesNoteBinding
import com.door43.translationstudio.databinding.FragmentResourcesQuestionBinding
import com.door43.translationstudio.databinding.FragmentResourcesWordBinding
import com.door43.translationstudio.databinding.FragmentWordsIndexListBinding
import com.door43.translationstudio.format
import com.door43.translationstudio.formatSub
import com.door43.translationstudio.formatTitle
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_ENABLE_TM_LINKS
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_TM_URL
import com.door43.translationstudio.ui.spannables.ArticleLinkSpan
import com.door43.translationstudio.ui.spannables.PassageLinkSpan
import com.door43.translationstudio.ui.spannables.ShortReferenceSpan
import com.door43.translationstudio.ui.spannables.TranslationWordLinkSpan
import com.door43.translationstudio.ui.translate.review.ReviewHolder
import com.door43.util.StringUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.sufficientlysecure.htmltextview.LocalLinkMovementMethod
import org.unfoldingword.resourcecontainer.Link
import org.unfoldingword.tools.taskmanager.ManagedTask
import java.util.Arrays
import java.util.Collections
import java.util.regex.Pattern

/**
 * Created by joel on 9/8/2015.
 */
class ReviewModeFragment : ViewModeFragment(),
    ReviewModeAdapter.OnRenderHelpsListener,
    ReviewModeAdapter.OnShowToastListener {

    private val prefRepository: IPreferenceRepository by inject()

    companion object {
        private const val STATE_RESOURCES_OPEN = "state_resources_open"
        private const val STATE_RESOURCES_DRAWER_OPEN = "state_resources_drawer_open"
        private const val STATE_WORD_ID = "state_word_id"
        private const val STATE_HELP_TITLE = "state_help_title"
        private const val STATE_HELP_BODY = "state_help_body"
        private const val STATE_HELP_TYPE = "state_help_type"
    }

    private var resourcesOpen = false
    private var resourcesDrawerOpen = false
    private var translationWordId: String? = null

    private var enableTmLinks = false

    private var translationQuestion: TranslationHelp? = null
    private var translationNote: TranslationHelp? = null

    private var resourcesOpened = false
    private var enableMergeConflictsFilter = false

    override fun generateAdapter(): ViewModeAdapter<*> {
        return ReviewModeAdapter(
            resourcesOpen,
            enableMergeConflictsFilter,
            typography,
            assetsProvider,
            renderingProvider
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val args = arguments
        if (args != null) {
            enableMergeConflictsFilter = args.getBoolean(
                TargetTranslationActivity.STATE_FILTER_MERGE_CONFLICTS,
                false
            )
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (savedInstanceState != null) {
            resourcesOpen = savedInstanceState.getBoolean(STATE_RESOURCES_OPEN, false)
            resourcesDrawerOpen = savedInstanceState.getBoolean(STATE_RESOURCES_DRAWER_OPEN, false)

            if (savedInstanceState.containsKey(STATE_WORD_ID)) {
                translationWordId = savedInstanceState.getString(STATE_WORD_ID)
            } else if (savedInstanceState.containsKey(STATE_HELP_TYPE)) {
                val type = savedInstanceState.getString(STATE_HELP_TYPE)
                val help = TranslationHelp(
                    savedInstanceState.getString(STATE_HELP_TITLE) ?: "",
                    savedInstanceState.getString(STATE_HELP_BODY) ?: ""
                )
                if (type == "tn") {
                    translationNote = help
                } else if (type == "tq") {
                    translationQuestion = help
                }
            }
        }

        val adapter = getAdapter() as? ReviewModeAdapter
        adapter?.setOnRenderHelpsListener(this)
        adapter?.setOnItemActionListener(this)

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun setupObservers() {
        super.setupObservers()

        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                launch {
//                    viewModel.state
//                        .map { it.renderHelpsResult }
//                        .distinctUntilChanged()
//                        .collect { result ->
//                            if (result != null) {
//                                renderHelpsResult(result.item, result.helps)
//                            }
//                        }
//                }
//            }
        }
    }

    override fun onTaskFinished(task: ManagedTask) {
        super.onTaskFinished(task)
    }

    override fun onResume() {
        super.onResume()
        enableTmLinks = prefRepository.getDefaultPref(
            KEY_PREF_ENABLE_TM_LINKS,
            false,
            Boolean::class.javaObjectType
        )
    }

    override fun onPrepareView(rootView: View) {
        binding.closeResourcesDrawerBtn.setOnClickListener { closeResourcesDrawer() }

        // open the drawer on rotate
        if (resourcesDrawerOpen && resourcesOpen) {
            val viewTreeObserver = rootView.viewTreeObserver
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val sample = getViewHolderSample() as? ReviewHolder
                        if (sample != null) {
                            if (translationNote != null) {
                                onTranslationNoteClick(
                                    translationNote!!,
                                    sample.getResourceCardWidth()
                                )
                            } else if (translationWordId != null) {
                                viewModel.state.value.resourceContainer?.slug?.let { slug ->
                                    onTranslationWordClick(
                                        slug,
                                        translationWordId!!,
                                        sample.getResourceCardWidth()
                                    )
                                }
                            } else if (translationQuestion != null) {
                                onTranslationQuestionClick(
                                    translationQuestion!!,
                                    sample.getResourceCardWidth()
                                )
                            }
                        }
                    }
                })
            }
        }
        closeResourcesDrawer()
    }

    override fun onRightSwipe(e1: MotionEvent, e2: MotionEvent) {
        if (resourcesDrawerOpen) {
            closeResourcesDrawer()
        } else {
            if (getAdapter() != null) {
                closeResources()
            }
        }
    }

    override fun onLeftSwipe(e1: MotionEvent, e2: MotionEvent) {
        if (getAdapter() != null) {
            openResources()
        }
    }

    /**
     * Checks if the resources are open
     */
    fun isResourcesOpen(): Boolean {
        return resourcesOpened
    }

    private fun openResourcesDrawer(width: Int) {
        resourcesDrawerOpen = true
        val params = binding.resourcesDrawerCard.layoutParams
        params.width = width
        binding.resourcesDrawerCard.layoutParams = params
        // TODO: animate in
    }

    private fun closeResourcesDrawer() {
        resourcesDrawerOpen = false
        val params = binding.resourcesDrawerCard.layoutParams
        params.width = 0
        binding.resourcesDrawerCard.layoutParams = params
        // TODO: animate
    }

    override fun onTranslationWordClick(resourceContainerSlug: String, chapterSlug: String, width: Int) {
        renderTranslationWord(resourceContainerSlug, chapterSlug)
        openResourcesDrawer(width)
    }

    override fun onTranslationManualClick(section: String, slug: String) {
        val baseUrl = prefRepository.getDefaultPref(
            KEY_PREF_TM_URL,
            getString(R.string.pref_default_tm_url),
            String::class.javaObjectType
        )
        val url = "$baseUrl?section=$section#$slug"

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }

    override fun onTranslationNoteClick(note: TranslationHelp, width: Int) {
        renderTranslationNote(note)
        openResourcesDrawer(width)
    }

    override fun onTranslationQuestionClick(question: TranslationHelp, width: Int) {
        renderTranslationQuestion(question)
        openResourcesDrawer(width)
    }

    override fun markAllChunksDone() {
        getAdapter()?.markAllChunksDone()
    }

    /**
     * Prepares the resources drawer with the translation words index
     */
    private fun renderTranslationWordsIndex(resourceContainerSlug: String) {
        binding.scrollingResourcesDrawerContent.visibility = View.GONE
        binding.resourcesDrawerContent.visibility = View.VISIBLE

        val wordsIndexListBinding = FragmentWordsIndexListBinding.inflate(requireActivity().layoutInflater)

        binding.resourcesDrawerContent.removeAllViews()
        binding.resourcesDrawerContent.addView(wordsIndexListBinding.list)
        val adapter = ArrayAdapter<String>(
            requireActivity(),
            R.layout.list_clickable_text
        )
        val rc = viewModel.getResourceContainer(resourceContainerSlug)
        if (rc != null) {
            val chapters = rc.chapters()
            val words = Arrays.asList(*chapters)
            Collections.sort(words)
            val titlePattern = Pattern.compile("#(.*)")
            for (slug in words) {
                // get title and add to adapter
                val match = titlePattern.matcher(rc.readChunk(slug, "01"))
                if (match.find()) {
                    adapter.add(match.group(1))
                } else {
                    adapter.add(slug)
                }
            }
            wordsIndexListBinding.list.adapter = adapter
            wordsIndexListBinding.list.setOnItemClickListener { _, _, position, _ ->
                val slug = words[position]
                renderTranslationWord(rc.slug, slug)
            }
        }
    }

    /**
     * Prepares the resources drawer with the translation word
     */
    @Suppress("UNCHECKED_CAST")
    private fun renderTranslationWord(resourceContainerSlug: String, chapterSlug: String) {
        translationWordId = chapterSlug
        val rc = viewModel.getResourceContainer(resourceContainerSlug)

        if (rc != null) {
            binding.resourcesDrawerContent.visibility = View.GONE
            binding.scrollingResourcesDrawerContent.visibility = View.VISIBLE
            binding.scrollingResourcesDrawerContent.scrollTo(0, 0)

            val wordBinding = FragmentResourcesWordBinding.inflate(requireActivity().layoutInflater)

            wordBinding.wordsIndex.setOnClickListener { renderTranslationWordsIndex(resourceContainerSlug) }
            val word = rc.readChunk(chapterSlug, "01")
            val pattern = Pattern.compile("#+([^\\n]+)\\n+([\\s\\S]*)")
            val match = pattern.matcher(word)
            var description = ""
            if (match.find()) {
                wordBinding.wordTitle.text = match.group(1)
                description = match.group(2) ?: ""
                // TODO: 10/12/16 load the description title. This should be read from the config maybe?
                wordBinding.descriptionTitle.text = "Description"
            }
            wordBinding.descriptionTitle.formatTitle(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                rc.language.slug,
                rc.language.direction
            )
            val renderer = renderingProvider.createHtmlRenderer(
                preprocessor = { span ->
                    var result = false
                    when (span) {
                        is ArticleLinkSpan -> {
                            val title = getString(R.string.tm_title, span.section, span.slug)
                            span.setTitle(title)
                            result = enableTmLinks
                        }
                        is PassageLinkSpan -> {
                            val chunk = rc.readChunk(span.chapterId, span.frameId)
                            val verseTitle = Frame.parseVerseTitle(
                                chunk,
                                TranslationFormat.parse(rc.contentMimeType)
                            )
                            val chapterId = try {
                                span.chapterId.toInt().toString()
                            } catch (e: Exception) {
                                span.chapterId
                            }
                            val title = "${rc.readChunk("front", "title")} $chapterId:$verseTitle"
                            span.setTitle(title)
                            result = chunk.isNotEmpty()
                        }
                        is TranslationWordLinkSpan -> {
                            val currentRC = getSelectedResourceContainer()
                            if (currentRC != null) {
                                val titlePattern = Pattern.compile("#(.*)")
                                val closestRc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw")

                                if (closestRc != null) {
                                    val closestWord = closestRc.readChunk(span.machineReadable.toString(), "01")
                                    if (closestWord.isNotEmpty()) {
                                        val linkMatch = titlePattern.matcher(closestWord.trim())
                                        var title = span.machineReadable.toString()
                                        if (linkMatch.find()) {
                                            title = linkMatch.group(1) ?: title
                                        }
                                        span.title = title
                                        result = true
                                    }
                                }
                            }
                        }
                    }
                    result
                }
            )

            // TODO: replace with Compose path (toAnnotatedHtml + convertHtml)
            //wordBinding.description.text = renderer.render(description)
            wordBinding.description.movementMethod = LocalLinkMovementMethod.getInstance()
            wordBinding.description.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                rc.language.slug,
                rc.language.direction
            )

            wordBinding.seeAlso.removeAllViews()
            wordBinding.seeAlsoTitle.visibility = View.GONE
            wordBinding.examples.removeAllViews()
            wordBinding.examplesTitle.visibility = View.GONE

            if (rc.config != null && rc.config.containsKey(chapterSlug)) {
                val chapterConfig = rc.config[chapterSlug] as Map<String, List<String>>
                if (chapterConfig.containsKey("see_also")) {
                    val titlePattern = Pattern.compile("#(.*)")
                    val relatedSlugs = chapterConfig["see_also"] ?: emptyList()
                    for (relatedSlug in relatedSlugs) {
                        // TODO: 10/12/16 the words need to have their title placed into a "title" file instead of being inline in the chunk
                        val relatedWord = rc.readChunk(relatedSlug, "01")
                        val linkMatch = titlePattern.matcher(relatedWord.trim())
                        var relatedTitle = relatedSlug
                        if (linkMatch.find()) {
                            relatedTitle = linkMatch.group(1) ?: relatedTitle
                        }
                        val button = Button(
                            ContextThemeWrapper(requireActivity(), R.style.Widget_Button_Tag),
                            null,
                            R.style.Widget_Button_Tag
                        )
                        button.text = relatedTitle
                        button.setOnClickListener {
                            onTranslationWordClick(
                                rc.slug, relatedSlug,
                                binding.resourcesDrawerCard.layoutParams.width
                            )
                        }
                        button.formatSub(
                            typography,
                            assetsProvider,
                            TranslationType.SOURCE,
                            rc.language.slug,
                            rc.language.direction
                        )
                        wordBinding.seeAlso.addView(button)
                    }
                    if (relatedSlugs.isNotEmpty()) {
                        wordBinding.seeAlsoTitle.visibility = View.VISIBLE
                    }
                }
                if (chapterConfig.containsKey("examples")) {
                    val exampleSlugs = chapterConfig["examples"] ?: emptyList()
                    for (exampleSlug in exampleSlugs) {
                        val slugs = exampleSlug.split("-")
                        if (slugs.size != 2) continue

                        val projectTitle = viewModel.state.value.resourceContainer?.readChunk("front", "title") ?: ""

                        // get verse title
                        var verseTitle = StringUtilities.formatNumber(slugs[1])
                        if (viewModel.state.value.resourceContainer?.contentMimeType == "text/usfm") {
                            verseTitle = Frame.parseVerseTitle(
                                viewModel.state.value.resourceContainer!!.readChunk(slugs[0], slugs[1]),
                                TranslationFormat.parse(viewModel.state.value.resourceContainer!!.contentMimeType)
                            )
                        }

                        val examplesBinding = FragmentResourcesExampleItemBinding.inflate(requireActivity().layoutInflater)

                        examplesBinding.reference.text = "${projectTitle.trim()} ${StringUtilities.formatNumber(slugs[0])}:$verseTitle"
                        examplesBinding.passage.setHtmlFromString(viewModel.state.value.resourceContainer?.readChunk(slugs[0], slugs[1]) ?: "", true)
                        examplesBinding.root.setOnClickListener { scrollToChunk(slugs[0], slugs[1]) }

                        examplesBinding.reference.formatSub(
                            typography,
                            assetsProvider,
                            TranslationType.SOURCE,
                            rc.language.slug,
                            rc.language.direction
                        )
                        examplesBinding.passage.formatSub(
                            typography,
                            assetsProvider,
                            TranslationType.SOURCE,
                            rc.language.slug,
                            rc.language.direction
                        )
                        wordBinding.examples.addView(examplesBinding.root)
                    }
                    if (exampleSlugs.isNotEmpty()) {
                        wordBinding.examplesTitle.visibility = View.VISIBLE
                    }
                }
            }
            wordBinding.seeAlsoTitle.formatTitle(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                rc.language.slug,
                rc.language.direction
            )
            wordBinding.examplesTitle.formatTitle(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                rc.language.slug,
                rc.language.direction
            )

            binding.scrollingResourcesDrawerContent.removeAllViews()
            binding.scrollingResourcesDrawerContent.addView(wordBinding.root)
        }
    }

    /**
     * Prepares the resources drawer with the translation note
     */
    private fun renderTranslationNote(note: TranslationHelp) {
        translationNote = note
        translationWordId = null

        binding.resourcesDrawerContent.visibility = View.GONE
        binding.scrollingResourcesDrawerContent.visibility = View.VISIBLE
        binding.scrollingResourcesDrawerContent.scrollTo(0, 0)

        val noteBinding = FragmentResourcesNoteBinding.inflate(requireActivity().layoutInflater)

        val renderer = renderingProvider.createHtmlRenderer(
            preprocessor = { span ->
                var result = false
                when (span) {
                    is ArticleLinkSpan -> {
                        val title = getString(R.string.tm_title, span.section, span.slug)
                        span.setTitle(title)
                        result = enableTmLinks
                    }

                    is PassageLinkSpan -> {
                        // Not implemented in original Java code
                    }

                    is TranslationWordLinkSpan -> {
                        val currentRC = getSelectedResourceContainer()
                        if (currentRC != null) {
                            val titlePattern = Pattern.compile("#(.*)")
                            val rc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw")
                            if (rc != null) {
                                val word = rc.readChunk(span.machineReadable, "01")
                                if (word.isNotEmpty()) {
                                    val linkMatch = titlePattern.matcher(word.trim())
                                    var title = span.machineReadable
                                    if (linkMatch.find()) {
                                        title = linkMatch.group(1) ?: title
                                    }
                                    span.title = title
                                    result = true
                                }
                            }
                        }
                    }

                    is ShortReferenceSpan -> {
                        // Not implemented fully in original Java code
                    }
                }
                result
            }
        )

        noteBinding.title.text = note.title
        val sourceLanguage = viewModel.state.value.resourceContainer?.language
        if (sourceLanguage != null) {
            noteBinding.title.format(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                sourceLanguage.slug,
                sourceLanguage.direction
            )
            // TODO: replace with Compose path (toAnnotatedHtml + convertHtml)
            //noteBinding.description.text = renderer.render(note.body)
            noteBinding.description.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                sourceLanguage.slug,
                sourceLanguage.direction
            )
            noteBinding.description.movementMethod = LocalLinkMovementMethod.getInstance()
        }

        binding.scrollingResourcesDrawerContent.removeAllViews()
        binding.scrollingResourcesDrawerContent.addView(noteBinding.root)
    }

    /**
     * Prepares the resources drawer with the translation question
     */
    private fun renderTranslationQuestion(question: TranslationHelp) {
        translationWordId = null
        translationQuestion = question

        binding.resourcesDrawerContent.visibility = View.GONE
        binding.scrollingResourcesDrawerContent.visibility = View.VISIBLE
        binding.scrollingResourcesDrawerContent.scrollTo(0, 0)

        val questionBinding = FragmentResourcesQuestionBinding.inflate(requireActivity().layoutInflater)

        val sourceLanguage = viewModel.state.value.resourceContainer?.language
        if (sourceLanguage != null) {
            questionBinding.questionTitle.formatTitle(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                sourceLanguage.slug,
                sourceLanguage.direction
            )
            questionBinding.answerTitle.formatTitle(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                sourceLanguage.slug,
                sourceLanguage.direction
            )

            questionBinding.question.text = question.title
            questionBinding.question.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                sourceLanguage.slug,
                sourceLanguage.direction
            )
            questionBinding.answer.text = question.body
            questionBinding.answer.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                sourceLanguage.slug,
                sourceLanguage.direction
            )
        }

        binding.scrollingResourcesDrawerContent.removeAllViews()
        binding.scrollingResourcesDrawerContent.addView(questionBinding.root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderHelpsResult(item: ListItemOld, helps: Map<String, Any>) {
        // skip if resources are closed
        if (!isResourcesOpen()) return

        val notes = helps["notes"] as? List<TranslationHelp> ?: emptyList()
        val words = helps["words"] as? List<Link> ?: emptyList()
        val questions = helps["questions"] as? List<TranslationHelp> ?: emptyList()

        val adapter = getAdapter() as? ReviewModeAdapter ?: return
        val position = adapter.filteredItems.indexOf(item)

        val hand = Handler(Looper.getMainLooper())
        hand.post {
            val holder = getVisibleViewHolder(position) as? ReviewHolder
            if (holder != null) {
                holder.setResources(item.source.language, notes, questions, words)
                // TODO: 2/28/17 select the correct tab
            } else {
                adapter.notifyItemChanged(position)
            }
        }
    }

    override fun onRenderHelps(item: ListItemOld) {
        viewModel.renderHelps(item)
    }

    override fun onShowToast(message: String) {
        showToast(message)
    }

    override fun onShowToast(message: Int) {
        showToast(getString(message))
    }

    private fun showToast(message: String) {
        val snack = Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_SHORT
        )
        ViewUtil.setSnackBarTextColor(
            snack,
            ContextCompat.getColor(requireContext(), R.color.light_primary_text)
        )
        snack.show()
    }

    /**
     * opens the resources view
     */
    fun openResources() {
        if (!resourcesOpened) {
            resourcesOpened = true
            getAdapter()?.setResourcesOpened(true)
        }
    }

    /**
     * closes the resources view
     */
    fun closeResources() {
        if (resourcesOpened) {
            resourcesOpened = false
            getAdapter()?.setResourcesOpened(false)
            viewModel.cancelRenderJobs()
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean(STATE_RESOURCES_OPEN, isResourcesOpen())
        out.putBoolean(STATE_RESOURCES_DRAWER_OPEN, resourcesDrawerOpen)
        if (translationWordId != null) {
            out.putString(STATE_WORD_ID, translationWordId)
        } else {
            out.remove(STATE_WORD_ID)
        }
        if (translationNote != null) {
            out.putString(STATE_HELP_TITLE, translationNote!!.title)
            out.putString(STATE_HELP_BODY, translationNote!!.body)
            out.putString(STATE_HELP_TYPE, "tn")
        } else if (translationQuestion != null) {
            out.putString(STATE_HELP_TITLE, translationQuestion!!.title)
            out.putString(STATE_HELP_BODY, translationQuestion!!.body)
            out.putString(STATE_HELP_TYPE, "tq")
        } else {
            out.remove(STATE_HELP_TITLE)
            out.remove(STATE_HELP_BODY)
            out.remove(STATE_HELP_TYPE)
        }
        super.onSaveInstanceState(out)
    }

    override fun getVerseChunk(chapterSlug: String, verseSlug: String): String {
        return Util.mapVerseToChunk(viewModel.state.value.resourceContainer!!, chapterSlug, verseSlug)
    }

    override fun onStop() {
        super.onStop()
        if (resourcesDrawerOpen) {
            closeResourcesDrawer()
        }
    }
}