package com.door43.translationstudio.ui.translate

import android.content.ContentValues
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.FileHistory
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import org.unfoldingword.resourcecontainer.ResourceContainer


/**
 * Represents a single row in the translation list
 */
abstract class ListItem(
    @JvmField val chapterSlug: String,
    @JvmField val chunkSlug: String,
    @JvmField val source: ResourceContainer,
    @JvmField val target: TargetTranslation
) {
    @JvmField var renderedSourceText: CharSequence? = null
    @JvmField var renderedTargetText: CharSequence? = null
    @JvmField var isEditing: Boolean = false
    @JvmField var isDisabled: Boolean = false

    val sourceText: String
        get() = getSourceText(chapterSlug, chunkSlug)

    abstract fun getSourceText(chapterSlug: String, chunkSlug: String?): String

    private var _targetText: String? = null
    var targetText: String
        get() = _targetText ?: getTargetText(chapterSlug, chunkSlug)
        set(value) { _targetText = value }

    abstract fun getTargetText(chapterSlug: String, chunkSlug: String?): String

    /**
     * Returns the title of the list item
     * @return
     */
    val targetTitle: String
        get() {
            if (isProjectTitle) {
                return removeConflicts(target.targetLanguage.name)
            } else if (isChapter) {
                val ptTitle = removeConflicts(pt.title).trim()
                return if (ptTitle.isNotEmpty()) {
                    ptTitle + " - " + target.targetLanguage.name
                } else {
                    removeConflicts(source.project.name).trim() + " - " + target.targetLanguage.name
                }
            } else {
                // use project title
                var title = removeConflicts(pt.title).trim()
                if (title.isEmpty()) {
                    title = removeConflicts(source.project.name).trim()
                }
                title += " " + chapterSlug.toInt()

                val verseSpan = Frame.parseVerseTitle(sourceText, sourceTranslationFormat)
                title += if (verseSpan.isEmpty()) {
                    ":" + chunkSlug.toInt()
                } else {
                    ":$verseSpan"
                }
                return title + " - " + target.targetLanguage.name
            }
        }

    private var _hasMergeConflicts: Boolean? = null
    var hasMergeConflicts: Boolean
        get() = _hasMergeConflicts ?: run {
            _hasMergeConflicts = MergeConflictsHandler.isMergeConflicted(targetText)
            _hasMergeConflicts!!
        }
        set(value) { _hasMergeConflicts = value }

    val sourceTranslationFormat: TranslationFormat
        get() = TranslationFormat.parse(source.contentMimeType)

    val targetTranslationFormat: TranslationFormat
        get() = target.format

    val pt: ProjectTranslation
        get() = target.projectTranslation
    val ct: ChapterTranslation
        get() = target.getChapterTranslation(chapterSlug)
    val ft: FrameTranslation?
        get() = target.getFrameTranslation(
            chapterSlug,
            chunkSlug,
            targetTranslationFormat
        )

    private var _isComplete: Boolean? = null
    var isComplete: Boolean
        get() = _isComplete ?: run {
            _isComplete = when (chapterSlug) {
                "front" -> {
                    // project stuff
                    if (chunkSlug == "title") {
                        pt.isTitleFinished
                    } else false
                }
                "back" -> false
                else -> {
                    // chapter stuff
                    when (chunkSlug) {
                        "title" -> ct.isTitleFinished
                        "reference" -> ct.isReferenceFinished
                        else -> ft?.isFinished == true
                    }
                }
            }
            _isComplete!!
        }
        set(value) { _isComplete = value }

    private var _fileHistory: FileHistory? = null
    /**
     * Loads the file history or returns it from the cache
     * @return
     */
    val fileHistory: FileHistory?
        get() = _fileHistory ?: run {
            _fileHistory = when {
                isChapterReference -> target.getChapterReferenceHistory(ct)
                isChapterTitle -> target.getChapterTitleHistory(ct)
                isProjectTitle -> target.projectTitleHistory
                isChunk -> target.getFrameHistory(ft)
                else -> null
            }
            _fileHistory
        }

    private var _chapterTitle: String? = null
    val chapterTitle: String
        get() = _chapterTitle ?: run {
            _chapterTitle = source.readChunk(chapterSlug, "title").trim()
            if (_chapterTitle.isNullOrEmpty()) {
                _chapterTitle = source.readChunk("front", "title").trim()
                if (chapterSlug != "front") _chapterTitle += " " + chapterSlug.toInt()
            }
            _chapterTitle ?: ""
        }

    val isChunk: Boolean
        get() = !isChapter && !isProjectTitle

    val isChapter: Boolean
        get() = isChapterReference || isChapterTitle

    val isProjectTitle: Boolean
        get() = chapterSlug == "front" && chunkSlug == "title"

    val isChapterTitle: Boolean
        get() = chapterSlug != "front" && chapterSlug != "back" && chunkSlug == "title"

    val isChapterReference: Boolean
        get() = chapterSlug != "front" && chapterSlug != "back" && chunkSlug == "reference"

    val tabs: List<ContentValues>
        get() = fetchTabs()

    abstract fun fetchTabs(): List<ContentValues>

    val chunkConfig: Map<String, List<String>>?
        /**
         * Returns the config options for a chunk
         * @return
         */
        get() {
            val config: Map<*, *>?
            if (source.config == null || !source.config.containsKey("content") || source.config["content"] !is Map<*, *>) {
                return HashMap()
            } else {
                config = source.config
            }

            // look up config for chunk
            if (config != null && config.containsKey("content") && config["content"] is Map<*, *>) {
                val contentConfig: Map<*, *>? = config["content"] as? Map<*, *>
                if (contentConfig != null && contentConfig.containsKey(chapterSlug)) {
                    val chapterConfig: Map<*, *>? = contentConfig[chapterSlug] as? Map<*, *>?
                    if (chapterConfig != null && chapterConfig.containsKey(chunkSlug)) {
                        return chapterConfig[chunkSlug] as? Map<String, List<String>>
                    }
                }
            }
            return HashMap()
        }

    /**
     * Removes merge conflicts in text (uses first option)
     * @param text
     * @return
     */
    private fun removeConflicts(text: String): String {
        if (MergeConflictsHandler.isMergeConflicted(text)) {
            var unConflictedText = MergeConflictsHandler.getMergeConflictItemsHead(text)
            if (unConflictedText == null) {
                unConflictedText = ""
            }
            return unConflictedText.toString()
        }
        return text
    }

    /**
     * Clears the loaded translation data
     */
    fun reset() {
        this.renderedSourceText = null
        this.renderedTargetText = null
        this.hasMergeConflicts = false
    }

    fun <T: ListItem>toType(
        factory: (
            String,
            String,
            ResourceContainer,
            TargetTranslation,
            (String, String?) -> String,
            (String, String?) -> String,
            () -> List<ContentValues>
        ) -> T
    ): T {
        val base = this
        return factory(
            base.chapterSlug,
            base.chunkSlug,
            base.source,
            base.target,
            base::getSourceText,
            base::getTargetText,
            base::fetchTabs
        ).apply {
            hasMergeConflicts = base.hasMergeConflicts
            renderedSourceText = base.renderedSourceText
            renderedTargetText = base.renderedTargetText
            isEditing = base.isEditing
        }
    }
}

class ReadListItem(
    chapterSlug: String,
    chunkSlug: String,
    source: ResourceContainer,
    target: TargetTranslation,
    private val getSourceTextFunc: (String, String?) -> String,
    private val getTargetTextFunc: (String, String?) -> String,
    private val getTabsFunc: () -> List<ContentValues>,
) : ListItem(chapterSlug, chunkSlug, source, target) {

    override fun getSourceText(chapterSlug: String, chunkSlug: String?): String {
        return getSourceTextFunc(chapterSlug, null)
    }

    override fun getTargetText(chapterSlug: String, chunkSlug: String?): String {
        return getTargetTextFunc(chapterSlug, null)
    }

    override fun fetchTabs(): List<ContentValues> {
        return getTabsFunc()
    }
}

class ChunkListItem(
    chapterSlug: String,
    chunkSlug: String,
    source: ResourceContainer,
    target: TargetTranslation,
    private val getSourceTextFunc: (String, String?) -> String,
    private val getTargetTextFunc: (String, String?) -> String,
    private val getTabsFunc: () -> List<ContentValues>
) : ListItem(chapterSlug, chunkSlug, source, target) {
    @JvmField var isTargetCardOpen = false

    override fun getSourceText(chapterSlug: String, chunkSlug: String?): String {
        return getSourceTextFunc(chapterSlug, chunkSlug)
    }

    override fun getTargetText(chapterSlug: String, chunkSlug: String?): String {
        return getTargetTextFunc(chapterSlug, chunkSlug)
    }

    override fun fetchTabs(): List<ContentValues> {
        return getTabsFunc()
    }
}

/**
 * Represents a single item in the review list
 */
class ReviewListItem(
    chapterSlug: String,
    chunkSlug: String,
    source: ResourceContainer,
    target: TargetTranslation,
    private val getSourceTextFunc: (String, String?) -> String,
    private val getTargetTextFunc: (String, String?) -> String,
    private val getTabsFunc: () -> List<ContentValues>
) : ListItem(chapterSlug, chunkSlug, source, target) {
    @JvmField
    var hasSearchText: Boolean = false
    @JvmField
    var mergeItems: List<CharSequence> = emptyList()
    @JvmField
    var mergeItemSelected: Int = -1
    @JvmField
    var selectItemNum: Int = -1
    @JvmField
    var refreshSearchHighlightSource: Boolean = false
    @JvmField
    var refreshSearchHighlightTarget: Boolean = false
    @JvmField
    var hasMissingVerses: Boolean = false
    @JvmField
    var resourcesOpened: Boolean = false

    override fun getSourceText(chapterSlug: String, chunkSlug: String?): String {
        return getSourceTextFunc(chapterSlug, chunkSlug)
    }

    override fun getTargetText(chapterSlug: String, chunkSlug: String?): String {
        return getTargetTextFunc(chapterSlug, chunkSlug)
    }

    override fun fetchTabs(): List<ContentValues> {
        return getTabsFunc()
    }
}
