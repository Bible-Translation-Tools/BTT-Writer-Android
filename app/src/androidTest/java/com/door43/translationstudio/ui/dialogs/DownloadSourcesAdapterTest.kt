package com.door43.translationstudio.ui.dialogs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.dialogs.DownloadSourcesAdapter.FilterStep
import com.door43.translationstudio.ui.dialogs.DownloadSourcesAdapter.SelectionType
import com.door43.usecases.GetAvailableSources
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import javax.inject.Inject

/**
 * Verifies the download sources adapter tolerates stale positions and filter rows.
 *
 * The item list is rebuilt on every filter step, search and selection change, so a position
 * captured earlier - or a position held by the download result callback - may be out of range
 * or point at a filter row, which carries no resource container.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class DownloadSourcesAdapterTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var typography: Typography

    private lateinit var adapter: DownloadSourcesAdapter
    private lateinit var sources: List<Translation>

    @Before
    fun setUp() {
        hiltRule.inject()
        sources = listOf(
            translation("en", "English", "gen", "Genesis"),
            translation("en", "English", "mat", "Matthew"),
            translation("fr", "français", "gen", "Genèse")
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            adapter = DownloadSourcesAdapter(
                InstrumentationRegistry.getInstrumentation().targetContext,
                typography
            )
            adapter.setData(
                GetAvailableSources.Result(
                    sources,
                    mapOf("en" to listOf(0, 1), "fr" to listOf(2)),
                    mapOf("gen" to listOf(0, 2)),
                    mapOf("mat" to listOf(1)),
                    mapOf()
                )
            )
        }
    }

    /**
     * The download result callback used to look up positions and dereference the container slug
     * of every row. Filter rows have no container, so the lookup crashed with a
     * NullPointerException whenever the user had navigated back to a filter list.
     */
    @Test
    fun findPositionOnCategoryListDoesNotCrash() = onMainSync {
        showCategories()

        assertEquals("Categories should be listed", 2, adapter.count)
        assertEquals("Unknown slug should not be found", -1, adapter.findPosition(sourceSlug(0)))
        assertEquals("Null slug should not be found", -1, adapter.findPosition(null))
    }

    /**
     * The download result must still land on the item when the list was rebuilt meanwhile.
     */
    @Test
    fun markItemDownloadedBySlug() = onMainSync {
        showSources()

        val slug = sourceSlug(0)
        adapter.markItemDownloaded(slug)

        val item = adapter.getItemBySlug(slug)
        assertNotNull(item)
        assertTrue("Item should be downloaded", item.downloaded)
        assertTrue("Downloaded list should contain the slug", adapter.downloaded.contains(slug))
    }

    /**
     * A download that finishes while a filter list is shown must not be lost.
     */
    @Test
    fun markItemDownloadedBySlugWhileFilterListShown() = onMainSync {
        showCategories()

        val slug = sourceSlug(0)
        adapter.markItemDownloaded(slug)
        assertTrue("Downloaded list should contain the slug", adapter.downloaded.contains(slug))

        showSources()

        val item = adapter.getItemBySlug(slug)
        assertNotNull(item)
        assertTrue("Downloaded state should be restored", item.downloaded)
    }

    @Test
    fun markItemErrorBySlugWhileFilterListShown() = onMainSync {
        showCategories()

        val slug = sourceSlug(0)
        adapter.markItemError(slug, "download failed")

        showSources()

        val item = adapter.getItemBySlug(slug)
        assertNotNull(item)
        assertTrue("Error state should be restored", item.error)
        assertEquals("download failed", item.errorMessage)
    }

    /**
     * Out of range positions used to throw IndexOutOfBoundsException from items.get(position).
     */
    @Test
    fun outOfRangePositionsAreIgnored() = onMainSync {
        showSources()
        val count = adapter.count

        assertNull("Out of range item should be null", adapter.getItem(count + 5))
        assertNull("Negative position should be null", adapter.getItem(-1))

        adapter.toggleSelection(count + 5)
        adapter.select(count + 5)
        adapter.deselect(count + 5)
        adapter.markItemDownloaded(count + 5)
        adapter.markItemError(count + 5, "boom")

        assertEquals("Nothing should be selected", 0, adapter.selected.size)
        assertEquals("Nothing should be downloaded", 0, adapter.downloaded.size)
    }

    /**
     * Filter rows carry no resource container, so they must never enter the selection.
     */
    @Test
    fun filterRowsAreNotSelectable() = onMainSync {
        showCategories()

        adapter.toggleSelection(0)
        adapter.forceSelection(true, false)

        assertEquals("Filter rows should not be selected", 0, adapter.selected.size)
        assertFalse("Selection should not contain nulls", adapter.selected.contains(null))
    }

    @Test
    fun sourceRowsAreStillSelectable() = onMainSync {
        showSources()

        adapter.toggleSelection(0)

        val slug = adapter.getItem(0).containerSlug
        assertEquals("Item should be selected", listOf(slug), adapter.selected)

        adapter.toggleSelection(0)
        assertEquals("Item should be deselected", 0, adapter.selected.size)
    }

    /** shows the category list, whose rows carry no resource container */
    private fun showCategories() {
        adapter.setFilterSteps(
            listOf(
                languageStep("en"),
                FilterStep(SelectionType.book_type, "categories")
            ),
            null,
            true
        )
    }

    /** shows the english old testament sources */
    private fun showSources() {
        adapter.setFilterSteps(
            listOf(
                languageStep("en"),
                categoryStep(),
                FilterStep(SelectionType.source_filtered_by_language, "sources")
            ),
            null,
            true
        )
    }

    private fun languageStep(languageSlug: String): FilterStep {
        return FilterStep(SelectionType.language, "language").apply { filter = languageSlug }
    }

    private fun categoryStep(): FilterStep {
        return FilterStep(SelectionType.book_type, "category").apply {
            filter = R.string.old_testament_label.toString()
        }
    }

    private fun sourceSlug(index: Int): String = sources[index].resourceContainerSlug

    private fun onMainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun translation(
        languageSlug: String,
        languageName: String,
        projectSlug: String,
        projectName: String
    ): Translation {
        return Translation(
            Language(languageSlug, languageName, "ltr"),
            Project(projectSlug, projectName, 1),
            Resource("ulb", "Unlocked Literal Bible", "book", "all", "3", "5")
        )
    }
}
