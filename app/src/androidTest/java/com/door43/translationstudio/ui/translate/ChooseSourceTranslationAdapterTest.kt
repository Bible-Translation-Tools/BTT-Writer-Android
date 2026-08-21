package com.door43.translationstudio.ui.translate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.RCItem
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Verifies the adapter tolerates stale positions coming from asynchronous callbacks
 * (download finished, container deleted, update check finished).
 *
 * The list is re-sorted on every selection change, search keystroke, download and delete,
 * so a position captured when a row was bound may point at a section header - or past the
 * end of the list - by the time the callback fires.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class ChooseSourceTranslationAdapterTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var typography: Typography

    private lateinit var adapter: ChooseSourceTranslationAdapter

    @Before
    fun setUp() {
        hiltRule.inject()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            adapter = ChooseSourceTranslationAdapter(
                InstrumentationRegistry.getInstrumentation().targetContext,
                typography
            )
            adapter.setItems(testItems())
        }
    }

    /**
     * Reproduces the crash reported as
     * NullPointerException: Attempt to read from field 'boolean RCItem.downloaded' on a null
     * object reference in ChooseSourceTranslationAdapter.getItemViewType(int)
     *
     * A download completes and the adapter is told to mark the item downloaded using the
     * position captured before the list was re-sorted. That position now holds a section
     * header, whose containerSlug is null, so a null slug is pushed into the selected list
     * and the next sort inserts a null element into the sorted data.
     */
    @Test
    fun markItemDownloadedWithStalePositionOnHeader() = onMainSync {
        adapter.applySearch("zzzz") // only the three section headers survive
        assertEquals("Only section headers should remain", 3, adapter.count)

        adapter.markItemDownloaded(0) // stale position - row 0 is the "selected" header

        assertNoNullItems()
        // would throw NullPointerException before the fix
        for (i in 0 until adapter.count) adapter.getItemViewType(i)
    }

    /**
     * Same as above but through the long press delete flow, which pushes the null slug
     * into the downloadable list instead.
     */
    @Test
    fun markItemDeletedWithStalePositionOnHeader() = onMainSync {
        adapter.applySearch("zzzz")
        assertEquals("Only section headers should remain", 3, adapter.count)

        adapter.markItemDeleted(0)

        assertNoNullItems()
        for (i in 0 until adapter.count) adapter.getItemViewType(i)
    }

    /**
     * The update check result carries a position too, and its live data is re-delivered
     * after a configuration change, when the list may be shorter than it used to be.
     */
    @Test
    fun setItemHasUpdatesWithOutOfRangePosition() = onMainSync {
        adapter.applySearch("zzzz")

        adapter.setItemHasUpdates(adapter.count + 5, true) // would throw before the fix

        assertNoNullItems()
    }

    /**
     * Toggling a stale position that now points at a header must not corrupt the list.
     */
    @Test
    fun toggleSelectionWithStalePositionOnHeader() = onMainSync {
        adapter.applySearch("zzzz")

        adapter.toggleSelection(0)
        adapter.toggleSelection(adapter.count + 5)

        assertNoNullItems()
        for (i in 0 until adapter.count) adapter.getItemViewType(i)
    }

    /**
     * A header must never be reported as selectable, and selecting real items must
     * keep working after the stale position calls above.
     */
    @Test
    fun realItemsStillSelectableAfterStalePositionCalls() = onMainSync {
        adapter.markItemDownloaded(0)
        adapter.markItemDeleted(0)

        val position = firstSelectablePosition()
        assertNotNull("There should be a selectable item", position)
        adapter.toggleSelection(position!!)

        assertNoNullItems()
        assertEquals("Item should be selected", true, adapter.getItem(
            firstSelectablePosition()!!
        ).selected)
    }

    /**
     * The search filter applies to every section, including the selected one,
     * but hidden selections must still be reported back to the caller.
     */
    @Test
    fun searchFiltersSelectedItemsWithoutLosingThem() = onMainSync {
        val position = firstSelectablePosition()!!
        val slug = adapter.getItem(position).containerSlug
        adapter.toggleSelection(position)
        assertEquals("Item should be selected", listOf(slug), adapter.selectedSlugs)

        adapter.applySearch("zzzz")

        assertEquals("Selected item should be filtered out", 3, adapter.count)
        assertEquals("Selection should be kept", listOf(slug), adapter.selectedSlugs)

        adapter.applySearch("")

        assertEquals("Selected item should be listed again", slug, adapter.getItem(1).containerSlug)
        assertEquals("Selection should be kept", listOf(slug), adapter.selectedSlugs)
    }

    private fun firstSelectablePosition(): Int? {
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)
            if (item != null && item.containerSlug != null && item.downloaded) return i
        }
        return null
    }

    private fun assertNoNullItems() {
        for (i in 0 until adapter.count) {
            assertNotNull("Sorted data should not contain null items", adapter.getItem(i))
        }
        assertFalse(
            "Out of range positions should not be selectable",
            adapter.isSelectableItem(adapter.count + 5)
        )
    }

    private fun onMainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun testItems(): List<RCItem> {
        return listOf(
            rcItem("en", "English", downloaded = true, selected = false),
            rcItem("es-419", "Español Latin America", downloaded = true, selected = false),
            rcItem("fr", "français", downloaded = false, selected = false)
        )
    }

    private fun rcItem(
        languageSlug: String,
        languageName: String,
        downloaded: Boolean,
        selected: Boolean
    ): RCItem {
        val translation = Translation(
            Language(languageSlug, languageName, "ltr"),
            Project("gen", "Genesis", 1),
            Resource("ulb", "Unlocked Literal Bible", "book", "all", "3", "5")
        )
        return RCItem(
            "$languageName - ${translation.resource.name}",
            translation,
            selected,
            downloaded
        )
    }
}
