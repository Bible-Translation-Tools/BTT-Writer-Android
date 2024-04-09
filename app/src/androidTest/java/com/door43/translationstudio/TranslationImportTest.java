package com.door43.translationstudio;


import org.junit.Before;
import org.junit.Test;

public class TranslationImportTest {

    @Before
    protected void setUp() throws Exception {

//        if(!App.isLoaded()) {
//             load everything
//            Util.runTask(new LoadTargetLanguagesTask());
//            Util.runTask(new LoadProjectsTask());
//            Util.runTask(new IndexProjectsTask(App.projectManager().getProjectSlugs()));
//            App.setLoaded(true);
//        } else {
//            Project[] projects = App.projectManager().getProjectSlugs();
//            for(Project p:projects) {
//                FileUtilities.deleteQuietly(new File(ProjectManager.getRepositoryPath(p, p.getSelectedSourceLanguage())));
//                p.flush();
//            }
//        }
    }

    /**
     * import legacy dokuwiki with a single language translation
     */
    @Test
    public void testLegacyDokuwikiImport() throws Exception {
//        File asset = App.context().getAssetAsFile("tests/exports/1.0_deutsch.txt");
//        assertTrue(Sharing.importDokuWiki(asset));

        // TODO: verify content imported correctly
    }

    /**
     * import legacy dokuwiki with multiple language translations
     */
    @Test
    public void testLegacyDokuwikiMultipleImport() throws Exception {
//        File asset = App.context().getAssetAsFile("tests/exports/1.0_afaraf_deutsch.txt");
//        assertTrue(Sharing.importDokuWiki(asset));

        // TODO: verify content imported correctly
    }

    /**
     * import dokuwiki
     */
    @Test
    public void testDokuwikiImport() throws Exception {
//        File asset = App.context().getAssetAsFile("tests/exports/2.0.0_uw-obs-de_dokuwiki.zip");
//        assertTrue(Sharing.importDokuWikiArchive(asset));

        // TODO: verify content imported correctly
    }

    /**
     * import legacy translation studio project archive through the dokuwiki archive import.
     * The archive should be redirected to the proper prepareLegacyArchiveImport method
     * @throws Exception
     */
    @Test
    public void testLegacyProjectRedirectsFromDokuwikiArchiveImport() throws Exception {
//        File asset = App.context().getAssetAsFile("tests/exports/2.0.0_uw-obs-de.zip");
//        assertTrue(Sharing.importDokuWikiArchive(asset));

        // TODO: verify content imported correctly
    }

    /**
     * import legacy translation studio project archive
     * @throws Exception
     */
    @Test
    public void testLegacyProjectImport() throws Exception {
//        File asset = App.context().getAssetAsFile("tests/exports/2.0.0_uw-obs-de.zip");
//        assertTrue(Sharing.prepareLegacyArchiveImport(asset));

        // TODO: verify content imported correctly
    }

    /**
     * import translation studio project archive
     * @throws Exception
     */
    @Test
    public void testProjectImport() throws Exception {
//        File dokuwiki = App.context().getAssetAsFile("tests/exports/2.0.3_uw-obs-de.tstudio");
//        ProjectImport[] projects = Sharing.prepareArchiveImport(dokuwiki);
//        assertTrue(projects.length > 0);
//        for(ProjectImport p:projects) {
//            assertTrue(Sharing.importProject(p));
//        }

        // TODO: verify content imported correctly
    }
}