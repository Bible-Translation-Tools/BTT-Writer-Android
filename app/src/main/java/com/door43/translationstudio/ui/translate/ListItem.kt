package com.door43.translationstudio.ui.translate

import android.content.ContentValues
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.FileHistory
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.SlugSorter
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

    val sourceText: String
        get() = source.readChunk(chapterSlug, chunkSlug)

    private var _targetText: String? = null
    var targetText: String
        get() = _targetText ?: when (chapterSlug) {
            "front" -> {
                // project stuff
                if (chunkSlug == "title") {
                    pt.title
                } else ""
            }
            "back" -> ""
            else -> {
                // chapter stuff
                when (chunkSlug) {
                    "title" -> ct.title
                    "reference" -> ct.reference
                    else -> ft?.body ?: ""
                }
            }
        }
        set(value) { _targetText = value }

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
                    ":" + chunkSlug?.toInt()
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

    abstract val tabs: () -> List<ContentValues>

    val chunkConfig: Map<String, List<String>>?
        /**
         * Returns the config options for a chunk
         * @return
         */
        get() {
            var config: Map<*, *>?
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

    /**
     * Loads the translation text from the disk.
     * This will not do anything if the sourceText is already loaded
     *
     * @param sourceContainer
     * @param targetTranslation TODO: this will become a resource container eventually
     */
    fun load(sourceContainer: ResourceContainer, targetTranslation: TargetTranslation) {
        /*if (this.sourceText == null) {
            this.renderedTargetText = null
            this.renderedSourceText = null
            if (this.sourceText == null) {
                this.sourceText = sourceContainer.readChunk(chapterSlug, chunkSlug)
            }
            this.sourceTranslationFormat = TranslationFormat.parse(sourceContainer.contentMimeType)
            this.targetTranslationFormat = targetTranslation.format
            loadTarget(targetTranslation)
        }*/
    }

    /**
     * used for reloading target translation to get any changes from file
     * @param targetTranslation
     */
    fun loadTarget(targetTranslation: TargetTranslation) {
        // TODO: 10/1/16 this will be simplified once we migrate target translations to resource containers
        //this.target = targetTranslation
        /*if (chapterSlug == "front") {
            // project stuff
            if (chunkSlug == "title") {
                this.isComplete = pt?.isTitleFinished == true
            }
        } else if (chapterSlug == "back") {
            // back matter
        } else {
            // chapter stuff
            if (chunkSlug == "title") {
                this.isComplete = ct?.isTitleFinished == true
            } else if (chunkSlug == "reference") {
                this.isComplete = ct?.isReferenceFinished == true
            } else {
                this.isComplete = ft?.isFinished == true
            }
        }*/
        //this.hasMergeConflicts = MergeConflictsHandler.isMergeConflicted(this.targetText)
    }

    fun <T: ListItem>toType(
        factory: (
            String,
            String,
            ResourceContainer,
            TargetTranslation,
            () -> List<ContentValues>
        ) -> T
    ): T {
        val base = this
        return factory(
            base.chapterSlug,
            base.chunkSlug,
            base.source,
            base.target,
            base.tabs
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
    override val tabs: () -> List<ContentValues>
) : ListItem(chapterSlug, chunkSlug, source, target) {

    val sourceChapterBody: String
        get() = run {
            var chapterBody = ""
            val sorter = SlugSorter()
            val chunks = sorter.sort(source.chunks(chapterSlug))
            for (chunk in chunks) {
                if(!chunk.equals("title")) {
                    chapterBody += source.readChunk(chapterSlug, chunk);
                }
            }
            chapterBody
        }

    val targetChapterBody: String
        get() = run {
            var chapterBody = ""
            val sorter = SlugSorter()
            val chunks = sorter.sort(source.chunks(chapterSlug))
            for (chunk in chunks) {
                val translation = target.getFrameTranslation(chapterSlug, chunk, target.format)
                chapterBody += " " + translation.body
            }
            chapterBody
        }
}

class ChunkListItem(
    chapterSlug: String,
    chunkSlug: String,
    source: ResourceContainer,
    target: TargetTranslation,
    override val tabs: () -> List<ContentValues>
) : ListItem(chapterSlug, chunkSlug, source, target) {
    @JvmField var isTargetCardOpen = false
}

/**
 * Represents a single item in the review list
 */
class ReviewListItem(
    chapterSlug: String,
    chunkSlug: String,
    source: ResourceContainer,
    target: TargetTranslation,
    override val tabs: () -> List<ContentValues>
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
}
