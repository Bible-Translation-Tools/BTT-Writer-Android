package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.usecases.UpdateAll
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Catalog
import org.unfoldingword.door43client.models.Category
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Versification
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UpdateAllTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var updateAll: UpdateAll
    @Inject lateinit var library: Door43Client

    private val bookSlug = "mat"
    private val bookName = "Mateo"
    private val bookDescr = "Test project"
    private val srcLangSlug = "test"
    private val srcLangName = "Test"
    private val chunksUrl = "http://test.com/chunks.json"
    private val catSlug = "bible-nt"
    private val catName = "New Testament"
    private val ulbName = "Test Literal Bible"
    private val ulbVer = "2"
    private val ulbLink = "http://test.com/source.json"
    private val tnName = "Test Translation Notes"
    private val tnVer = "3"
    private val tnLink = "http://test.com/notes.json"
    private val tqName = "Test Translation Questions"
    private val tqVer = "4"
    private val tqLink = "http://test.com/questions.json"
    private val twName = "Test Translation Words"
    private val twVer = "5"
    private val twLink = "http://test.com/words.json"
    private val targetLangSlug = "test-target"
    private val targetLangName = "Test Target"
    private val tempLangSlug = "test-temp"
    private val tempLangName = "Test Temp"

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @Test
    fun testUpdateAll() {
        every { library.updateSources(any(), any()) } returns updateSources()
        every { library.updateCatalogs(any()) } returns updateCatalogs()

        val result = updateAll.execute()

        assertTrue("UpdateAll should succeed", result.success)

        val sourceLanguages = library.index.getSourceLanguages(bookSlug)

        assertNotNull(
            "Source language should not be null",
            sourceLanguages.singleOrNull { it.slug == srcLangSlug && it.name == srcLangName }
        )

        val project = library.index.getProject(srcLangSlug, bookSlug)

        assertNotNull("Project should not be null", project)
        assertEquals("Project slug should be $bookSlug", bookSlug, project.slug)
        assertEquals("Project name should be $bookName", bookName, project.name)
        assertEquals("Project description should be $bookDescr", bookDescr, project.description)
        assertEquals("Project chunksUrl should be $chunksUrl", chunksUrl, project.chunksUrl)
        assertEquals("Project languageSlug should be $srcLangSlug", srcLangSlug, project.languageSlug)

        val category = library.index.getCategory(srcLangSlug, catSlug)
        assertNotNull("Category should not be null", category)
        assertEquals("Category slug should be $catSlug", catSlug, category.slug)
        assertEquals("Category name should be $catName", catName, category.name)

        val resources = library.index.getResources(srcLangSlug, bookSlug)
        assertTrue("Resources should not be empty", resources.isNotEmpty())

        val ulbResource = resources.singleOrNull { it.slug == "ulb" }
        assertNotNull("ULB resource should not be null", ulbResource)
        assertEquals("ULB resource name should be $ulbName", ulbName, ulbResource!!.name)
        assertEquals("ULB resource type should be book", "book", ulbResource.type)
        assertEquals("ULB resource version should be $ulbVer", ulbVer, ulbResource.version)
        assertEquals("ULB resource url should be $ulbLink", ulbLink, ulbResource.formats.first().url)

        val tnResource = resources.singleOrNull { it.slug == "tn" }
        assertNotNull("TN resource should not be null", tnResource)
        assertEquals("TN resource name should be $tnName", tnName, tnResource!!.name)
        assertEquals("TN resource type should be help", "help", tnResource.type)
        assertEquals("TN resource version should be $tnVer", tnVer, tnResource.version)
        assertEquals("TN resource url should be $tnLink", tnLink, tnResource.formats.first().url)

        val tqResource = resources.singleOrNull { it.slug == "tq" }
        assertNotNull("TQ resource should not be null", tqResource)
        assertEquals("TQ resource name should be $tqName", tqName, tqResource!!.name)
        assertEquals("TQ resource type should be help", "help", tqResource.type)
        assertEquals("TQ resource version should be $tqVer", tqVer, tqResource.version)
        assertEquals("TQ resource url should be $tqLink", tqLink, tqResource.formats.first().url)

        val twProject = library.index.getProject(srcLangSlug, "bible")
        assertNotNull("TW project should not be null", twProject)
        assertEquals("TW project slug should be bible", "bible", twProject!!.slug)
        assertEquals("TW project name should be $twName", twName, twProject.name)
        assertEquals("TW project languageSlug should be $srcLangSlug", srcLangSlug, project.languageSlug)

        val twResource = library.index.getResource(srcLangSlug, "bible", "tw")
        assertNotNull("TW resource should not be null", twResource)
        assertEquals("TW resource name should be $twName", twName, twResource!!.name)
        assertEquals("TW resource type should be dict", "dict", twResource.type)
        assertEquals("TW resource version should be $twVer", twVer, twResource.version)
        assertEquals("TW resource url should be $twLink", twLink, twResource.formats.first().url)

        val targetLanguage = library.index.getTargetLanguage(targetLangSlug)
        assertNotNull("Target language should not be null", targetLanguage)
        assertEquals("Target language slug should be $targetLangSlug", targetLangSlug, targetLanguage.slug)
        assertEquals("Target language name should be $targetLangName", targetLangName, targetLanguage.name)

        val tempLanguage = library.index.getTargetLanguage(tempLangSlug)
        assertNotNull("Temp language should not be null", tempLanguage)
        assertEquals("Temp language slug should be $tempLangSlug", tempLangSlug, tempLanguage.slug)
        assertEquals("Temp language name should be $tempLangName", tempLangName, tempLanguage.name)
    }

    private fun updateSources() {
        val srcLang = SourceLanguage(srcLangSlug, srcLangName, "ltr")
        val srcLangId = library.index.addSourceLanguage(srcLang)
        val versification = Versification("en-US", "American English")
        library.index.addVersification(versification, srcLangId)

        val project = Project(bookSlug, bookName, 41)
        project.description = bookDescr
        project.chunksUrl = chunksUrl
        val categories = listOf(Category(catSlug, catName))
        val projectId = library.index.addProject(project, categories, srcLangId)

        val format = Resource.Format(
            ResourceContainer.version,
            ContainerTools.typeToMime("book"),
            1667717348,
            ulbLink,
            false
        )
        val resource = Resource(
            "ulb",
            ulbName,
            "book",
            "all",
            "3",
            ulbVer
        )
        resource.addFormat(format)
        library.index.addResource(resource, projectId)

        val tnResource = Resource(
            "tn",
            tnName,
            "help",
            "gl",
            "3",
            tnVer
        )
        val tnFormat = Resource.Format(
            ResourceContainer.version,
            ContainerTools.typeToMime("help"),
            1667717349,
            tnLink,
            false
        )
        tnResource.addFormat(tnFormat)
        library.index.addResource(tnResource, projectId)

        val tqResource = Resource(
            "tq",
            tqName,
            "help",
            "gl",
            "3",
            tqVer
        )
        val tqFormat = Resource.Format(
            ResourceContainer.version,
            ContainerTools.typeToMime("help"),
            1667717350,
            tqLink,
            false
        )
        tqResource.addFormat(tqFormat)
        library.index.addResource(tqResource, projectId)

        val wordsProject = Project("bible", twName, 100)
        val wordsProjectId = library.index.addProject(wordsProject, null, srcLangId)
        val twResource = Resource(
            "tw",
            twName,
            "dict",
            "gl",
            "3",
            twVer
        )
        val twFormat = Resource.Format(
            ResourceContainer.version,
            ContainerTools.typeToMime("dict"),
            1667717350,
            twLink,
            false
        )
        twResource.addFormat(twFormat)
        library.index.addResource(twResource, wordsProjectId)
    }

    private fun updateCatalogs() {
        val langCatalog = Catalog("langnames", "http://test.com/langnames.json", 0)
        library.index.addCatalog(langCatalog)

        val questionnaireCatalog = Catalog("new-language-questions", "http://test.com/questionnaire.json", 0)
        library.index.addCatalog(questionnaireCatalog)

        val tempLangsCatalog = Catalog("temp-langnames", "http://test.com/temp-langs.json", 0)
        library.index.addCatalog(tempLangsCatalog)

        val approvedLangsCatalog = Catalog("approved-temp-langnames", "http://test.com/apprv-langs.json", 0)
        library.index.addCatalog(approvedLangsCatalog)

        val targetLanguage = TargetLanguage(
            targetLangSlug,
            targetLangName,
            targetLangName,
            "ltr",
            "Test",
            false
        )
        library.index.addTargetLanguage(targetLanguage)

        val tempLanguage = TargetLanguage(
            tempLangSlug,
            tempLangName,
            tempLangName,
            "ltr",
            "Test",
            true
        )
        library.index.addTempTargetLanguage(tempLanguage)
    }
}