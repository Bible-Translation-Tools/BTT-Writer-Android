package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.translate.ReviewListItem
import com.door43.translationstudio.ui.translate.TranslationHelp
import com.door43.usecases.ImportProjects
import com.door43.usecases.RenderHelps
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Link
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RenderHelpsTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var renderHelps: RenderHelps

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @Test
    fun testRenderHelps() {
        val targetTranslation = importTargetTranslation("aa")

        assertNotNull("Target translation should not be null", targetTranslation)

        val rc = library.open("en_mrk_ulb")

        val item = ReviewListItem(
            "01",
            "01",
            rc,
            targetTranslation!!,
            { _: String, _: String? -> "" },
            { _: String, _: String? -> "" },
            { listOf() }
        )

        val result = renderHelps.execute(item)

        assertTrue("Helps should not be empty", result.helps.isNotEmpty())
        assertEquals("There should be 3 helps", 3, result.helps.size)
        assertTrue("There should be a notes help", result.helps.containsKey("notes"))
        assertEquals("There should be 10 notes", 10, (result.helps["notes"] as List<*>).size)
        assertTrue("There should be a questions help", result.helps.containsKey("questions"))
        assertEquals("There should be 8 questions", 8, (result.helps["questions"] as List<*>).size)
        assertTrue("There should be a words help", result.helps.containsKey("words"))
        assertEquals("There should be 9 words", 9, (result.helps["words"] as List<*>).size)

        val note = (result.helps["notes"] as List<*>).firstOrNull {
            (it as TranslationHelp).title == "Son of God"
        }
        assertNotNull(note)
        assertTrue((note!! as TranslationHelp).body.contains("Jesus"))

        val question = (result.helps["questions"] as List<*>).firstOrNull {
            (it as TranslationHelp).title.contains("before the sun rose?", ignoreCase = true)
        }
        assertNotNull(question)
        assertTrue((question!! as TranslationHelp).body.contains("before the sun rose", ignoreCase = true))

        val word = (result.helps["words"] as List<*>).firstOrNull {
            (it as Link).chapter == "goodnews"
        }
        assertNotNull(word)
        assertTrue((word!! as Link).title.contains("good news", ignoreCase = true))
    }

    private fun importTargetTranslation(lang: String): TargetTranslation? {
        return TestUtils.importTargetTranslation(
            library,
            appContext,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            lang,
            "usfm/mrk.usfm"
        )
    }
}