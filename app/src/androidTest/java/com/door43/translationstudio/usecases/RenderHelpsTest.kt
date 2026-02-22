package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.translate.ReviewListItem
import com.door43.translationstudio.ui.translate.TranslationHelp
import com.door43.usecases.ImportProjects
import com.door43.usecases.RenderHelps
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Link

@RunWith(AndroidJUnit4::class)
@IntegrationTest
class RenderHelpsTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val importProjects: ImportProjects by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val library: Door43Client by inject()
    private val translator: Translator by inject()
    private val profile: Profile by inject()
    private val renderHelps: RenderHelps by inject()

    @Before
    fun setUp() {
        // Koin is already initialized via KoinTestApplication
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
