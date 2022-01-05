package org.unfoldingword.door43client;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.door43client.models.Category;
import org.unfoldingword.door43client.models.CategoryEntry;
import org.unfoldingword.door43client.models.ChunkMarker;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.door43client.models.Question;
import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.door43client.models.Versification;
import org.unfoldingword.door43client.models.Catalog;
import org.unfoldingword.door43client.models.Questionnaire;
import org.unfoldingword.resourcecontainer.ContainerTools;
import org.unfoldingword.resourcecontainer.Language;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.resourcecontainer.Resource;
import org.unfoldingword.resourcecontainer.ResourceContainer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Manages the indexed library content.
 */
class Library implements Index {

    private final SQLiteHelper sqliteHelper;
    private final SQLiteDatabase db;

    /**
     * Instantiates a new library
     * @param sqliteHelper
     */
    public Library(SQLiteHelper sqliteHelper) throws IOException {
        this.sqliteHelper = sqliteHelper;
        this.db = sqliteHelper.getWritableDatabase();
        if(this.db.getVersion() == 0) throw new IOException("Invalid database version." +
                "You probably manually generted the database and forgot to set the " +
                "\"User Version\" to " + SQLiteHelper.DATABASE_VERSION);
    }

    /**
     * Temporary ends the transaction to let other threads run
     */
    public void  yieldSafely() {
        db.yieldIfContendedSafely();
    }

    /**
     * Used to open a transaction
     */
    public void beginTransaction() {
        db.beginTransactionNonExclusive();
    }

    /**
     * Used to close a transaction
     * @param success set to false if the transaction should fail and the changes rolled back.
     */
    public void endTransaction(boolean success) {
        if(success) {
            db.setTransactionSuccessful();
        }
        db.endTransaction();
    }

    /**
     * Closes the database
     */
    public void closeDatabase() {
        sqliteHelper.close();
    }

    /**
     * Ensures a value is not null or empty
     * @param value
     * @throws Exception
     */
    private void validateNotEmpty(String value) throws Exception {
        if(value == null || value.trim().isEmpty()) throw new Exception("Invalid parameter value");
    }

    /**
     * Converts null strings to empty strings
     * @param value
     * @return
     */
    private String deNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * Attempts to insert a row.
     *
     * There is a bug in the SQLiteDatabase API that prevents us from using insertWithOnConflict + CONFLICT_IGNORE
     * https://code.google.com/p/android/issues/detail?id=13045
     * @param table
     * @param values
     * @param uniqueColumns
     * @return the id of the inserted row or the id of the existing row.
     */
    synchronized private InsertResult insertOrIgnore(String table, ContentValues values, String[] uniqueColumns) {
        // try to insert
        Exception error = null;
        try {
            long id = db.insertOrThrow(table, null, values);
            return new InsertResult(id, true);
        } catch (SQLException e) {
            error = e;
        }
        long id = -1;

        WhereClause where = WhereClause.prepare(values, uniqueColumns);

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select id from " + table + " where " + where.statement, where.arguments);
            if (cursor.moveToFirst()) {
                id = cursor.getLong(0);
                cursor.close();
            } else {
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if(cursor != null) cursor.close();
        }

        // TRICKY: print the insert stacktrace only if the select failed.
        if(error != null && id == -1) error.printStackTrace();
        return new InsertResult(id, false);
    }

    /**
     * A utility to perform insert+update operations.
     * Insert failures are ignored.
     * Update failures are thrown.
     *
     * @param table
     * @param values
     * @param uniqueColumns an array of unique columns on this table. This should be a subset of the values.
     * @return the id of the inserted/updated row
     */
    synchronized private InsertResult insertOrUpdate(String table, ContentValues values, String[] uniqueColumns) throws Exception {
        // insert
        InsertResult result = insertOrIgnore(table, values, uniqueColumns);
        if(!result.inserted) {
            WhereClause where = WhereClause.prepare(values, uniqueColumns);

            // clean values
            for(String key:uniqueColumns) {
                values.remove(key);
            }

            // update
            int numRows = db.updateWithOnConflict(table, values, where.statement, where.arguments, SQLiteDatabase.CONFLICT_ROLLBACK);
            if(numRows == 0) {
                throw new Exception("Failed to update the row in " + table);
            } else {
                // retrieve updated row id
                Cursor cursor = db.rawQuery("select id from " + table + " where " + where.statement, where.arguments);
                if(cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    cursor.close();
                    return new InsertResult(id, false);
                } else {
                    cursor.close();
                    throw new Exception("Failed to find the row in " + table);
                }
            }
        } else {
            return result;
        }
    }

    /**
     * Inserts or updates a source language in the library.
     *
     * @param language
     * @return the id of the source language row
     * @throws Exception
     */
    public long addSourceLanguage(SourceLanguage language) throws Exception {
        validateNotEmpty(language.slug);
        validateNotEmpty(language.name);
        validateNotEmpty(language.direction);

        ContentValues values = new ContentValues();
        values.put("slug", language.slug);
        values.put("name", language.name);
        values.put("direction", language.direction);

        return insertOrUpdate("source_language", values, new String[]{"slug"}).id;
    }

    /**
     * Inserts or updates a target language in the library.
     *
     * Note: the result is boolean since you don't need the row id. See getTargetLanguages for more information
     *
     * @param language
     * @return
     * @throws Exception
     */
    public boolean addTargetLanguage(TargetLanguage language) throws Exception {
        validateNotEmpty(language.slug);
        validateNotEmpty(language.name);
        validateNotEmpty(language.direction);

        ContentValues values = new ContentValues();
        values.put("slug", language.slug);
        values.put("name", language.name);
        values.put("direction", language.direction);
        values.put("anglicized_name", deNull(language.anglicizedName));
        values.put("region", deNull(language.region));
        values.put("is_gateway_language", language.isGatewayLanguage ? 1: 0);

        long id = insertOrUpdate("target_language", values, new String[]{"slug"}).id;
        return id > 0;
    }

    public boolean addTempTargetLanguage(TargetLanguage language) throws Exception {
        validateNotEmpty(language.slug);
        validateNotEmpty(language.name);
        validateNotEmpty(language.direction);

        ContentValues values = new ContentValues();
        values.put("slug", language.slug);
        values.put("name", language.name);
        values.put("direction", language.direction);
        values.put("anglicized_name", deNull(language.anglicizedName));
        values.put("region", deNull(language.region));
        values.put("is_gateway_language", language.isGatewayLanguage ? 1 : 0);

        long id = insertOrUpdate("temp_target_language", values, new String[]{"slug"}).id;
        return id > 0;
    }

    /**
     * Updates the target language assigned to a temporary target language
     * @param tempTargetLanguageSlug the temporary target language that is being assigned a target language
     * @param targetLanguageSlug the assigned target language
     * @return indicates if the approved language was successfully set
     */
    public boolean setApprovedTargetLanguage(String tempTargetLanguageSlug, String targetLanguageSlug) throws Exception {
        validateNotEmpty(tempTargetLanguageSlug);
        validateNotEmpty(targetLanguageSlug);

        ContentValues contentValues = new ContentValues();
        contentValues.put("approved_target_language_slug", targetLanguageSlug);

        int rowsAffected = db.updateWithOnConflict("temp_target_language", contentValues,
                "slug=?", new String[]{tempTargetLanguageSlug}, SQLiteDatabase.CONFLICT_IGNORE );

        return rowsAffected > 0;
    }

    /**
     * Inserts or updates a project in the library
     *
     * @param project
     * @param categories this is the category branch that the project will attach to
     * @param sourceLanguageId the parent source language row id
     * @return the id of the project row
     * @throws Exception
     */
    public long addProject(Project project, List<Category> categories, long sourceLanguageId) throws Exception {
        validateNotEmpty(project.slug);
        validateNotEmpty(project.name);

        // add categories
        long parentCategoryId = 0;
        if(categories != null) {
            // build categories
            for(Category category:categories) {
                validateNotEmpty(category.slug);
                validateNotEmpty(category.name);

                ContentValues insertValues = new ContentValues();
                insertValues.put("slug", category.slug);
                insertValues.put("parent_id", parentCategoryId);

                long id = insertOrIgnore("category", insertValues, new String[]{"slug", "parent_id"}).id;
                if(id > 0) {
                    parentCategoryId = id;
                } else {
                    throw new Exception("Invalid category");
                }

                ContentValues updateValues = new ContentValues();
                updateValues.put("source_language_id", sourceLanguageId);
                updateValues.put("category_id", parentCategoryId);
                updateValues.put("name", category.name);

                insertOrUpdate("category_name", updateValues, new String[]{"source_language_id", "category_id"});
            }
        }
        // add project
        ContentValues updateProject = new ContentValues();
        updateProject.put("slug", project.slug);
        updateProject.put("name", project.name);
        updateProject.put("desc", deNull(project.description));
        updateProject.put("icon", deNull(project.icon));
        updateProject.put("sort", project.sort);
        updateProject.put("chunks_url", deNull(project.chunksUrl));
        updateProject.put("source_language_id", sourceLanguageId);
        updateProject.put("category_id", parentCategoryId);

        return insertOrUpdate("project", updateProject, new String[]{"slug", "source_language_id"}).id;
    }

    /**
     * Inserts or updates a versification in the library.
     *
     * @param versification
     * @param sourceLanguageId the parent source language row id
     * @return the id of the versification or -1
     * @throws Exception
     */
    public long addVersification(Versification versification, long sourceLanguageId) throws Exception{
        validateNotEmpty(versification.slug);
        validateNotEmpty(versification.name);

        ContentValues values = new ContentValues();
        values.put("slug", versification.slug);

        long versificationId = insertOrIgnore("versification", values, new String[]{"slug"}).id;
        if(versificationId > 0) {
            ContentValues cv = new ContentValues();
            cv.put("source_language_id", sourceLanguageId);
            cv.put("versification_id", versificationId);
            cv.put("name", versification.name);
            insertOrUpdate("versification_name", cv, new String[]{"source_language_id", "versification_id"});
        } else {
            throw new Exception("Invalid versification");
        }
        return versificationId;
    }

    /**
     * Inserts a chunk marker in the library.
     *
     * @param chunk
     * @param projectSlug the project that this marker exists in
     * @param versificationId the versification this chunk is a member of
     * @return the id of the chunk marker
     * @throws Exception
     */
    public long addChunkMarker(ChunkMarker chunk, String projectSlug, long versificationId) throws Exception {
        validateNotEmpty(chunk.chapter);
        validateNotEmpty(chunk.verse);
        validateNotEmpty(projectSlug);

        ContentValues chunkValues = new ContentValues();
        chunkValues.put("chapter", chunk.chapter);
        chunkValues.put("verse", chunk.verse);
        chunkValues.put("project_slug", projectSlug);
        chunkValues.put("versification_id", versificationId);

        long id = insertOrIgnore("chunk_marker", chunkValues, new String[]{"project_slug", "versification_id", "chapter", "verse"}).id;
        if(id == -1) {
            throw new Exception("Invalid Chunk Marker");
        }
        return id;
    }

    /**
     * Inserts or updates a catalog in the library.
     *
     * @param catalog
     * @return the id of the catalog
     * @throws Exception
     */
    public long addCatalog(Catalog catalog) throws Exception{
        validateNotEmpty(catalog.slug);
        validateNotEmpty(catalog.url);

        ContentValues values = new ContentValues();
        values.put("slug", catalog.slug);
        values.put("url", catalog.url);
        values.put("modified_at", catalog.modifiedAt);

        return insertOrUpdate("catalog", values, new String[]{"slug"}).id;
    }

    /**
     * Inserts or updates a resource in the library.
     *
     * @param resource the resource being indexed
     * @param projectId the parent project row id
     * @return the id of the resource row
     * @throws Exception
     */
    public long addResource(Resource resource, long projectId) throws Exception {
        validateNotEmpty(resource.slug);
        validateNotEmpty(resource.name);
        validateNotEmpty(resource.type);
        validateNotEmpty(resource.formats.size() > 0 ? "good" : null);
        validateNotEmpty(resource.translateMode);
        validateNotEmpty(resource.checkingLevel);
        validateNotEmpty(resource.version);

        ContentValues values = new ContentValues();
        values.put("slug", resource.slug);
        values.put("name", resource.name);
        values.put("type", resource.type);
        values.put("translate_mode", resource.translateMode);
        values.put("checking_level", resource.checkingLevel);
        values.put("comments", deNull(resource.comments));
        values.put("pub_date", deNull(resource.pubDate));
        values.put("license", deNull(resource.license));
        values.put("version", resource.version);
        values.put("project_id", projectId);

        long resourceId = insertOrUpdate("resource", values, new String[]{"slug", "project_id"}).id;

        // add formats
        for(Resource.Format format : resource.formats) {
            validateNotEmpty(format.mimeType);
            ContentValues formatValues = new ContentValues();
            formatValues.put("package_version", format.packageVersion);
            formatValues.put("mime_type", format.mimeType);
            formatValues.put("imported", format.imported ? 1 : 0);
            formatValues.put("modified_at", format.modifiedAt);
            formatValues.put("url", deNull(format.url));
            formatValues.put("resource_id", resourceId);

            insertOrUpdate("resource_format", formatValues, new String[]{"mime_type", "resource_id"});
        }

        //add legacy data
        if(resource._legacyData.containsKey(API.LEGACY_WORDS_ASSIGNMENTS_URL)
                && resource._legacyData.get(API.LEGACY_WORDS_ASSIGNMENTS_URL) != null
                && !resource._legacyData.get(API.LEGACY_WORDS_ASSIGNMENTS_URL).equals("")) {
            ContentValues legacyValues = new ContentValues();
            legacyValues.put("translation_words_assignments_url", (String)resource._legacyData.get(API.LEGACY_WORDS_ASSIGNMENTS_URL));
            legacyValues.put("resource_id", resourceId);
            insertOrUpdate("legacy_resource_info", legacyValues, new String[]{"resource_id"});
        }
        return resourceId;
    }

    /**
     *Inserts or updates a questionnaire in the library.
     *
     * @param questionnaire the questionnaire to add
     * @return the id of the questionnaire row
     * @throws Exception
     */
    public long addQuestionnaire(Questionnaire questionnaire) throws Exception {
        validateNotEmpty(questionnaire.languageSlug);
        validateNotEmpty(questionnaire.languageName);
        validateNotEmpty(questionnaire.languageDirection);

        ContentValues values = new ContentValues();
        values.put("language_slug", questionnaire.languageSlug);
        values.put("language_name", questionnaire.languageName);
        values.put("language_direction", questionnaire.languageDirection);
        values.put("td_id", questionnaire.tdId);

        long id = insertOrUpdate("questionnaire", values, new String[]{"td_id", "language_slug"}).id;

        // add data fields
        for(String field:questionnaire.dataFields.keySet()) {
            ContentValues fieldValues = new ContentValues();
            fieldValues.put("questionnaire_id", id);
            fieldValues.put("field", field);
            fieldValues.put("question_td_id", (long)questionnaire.dataFields.get(field));
            insertOrUpdate("questionnaire_data_field", fieldValues, new String[]{"field", "questionnaire_id"});
        }

        return id;
    }

    /**
     * Inserts or updates a question in the library.
     *
     * @param question the questionnaire to add
     * @param questionnaireId the parent questionnaire row id
     * @return the id of the question row
     * @throws Exception
     */
    public long addQuestion(Question question, long questionnaireId) throws Exception {
        validateNotEmpty(question.text);
        validateNotEmpty(question.inputType == null ? "" : "ok");

        ContentValues values = new ContentValues();
        values.put("text", question.text);
        values.put("help", deNull(question.help));
        values.put("is_required", question.isRequired ? 1 : 0);
        values.put("input_type", question.inputType.toString());
        values.put("sort", question.sort);
        values.put("depends_on", question.dependsOn);
        values.put("td_id", question.tdId);
        values.put("questionnaire_id", questionnaireId);

        return insertOrUpdate("question", values, new String[]{"td_id", "questionnaire_id"}).id;
    }

    public List<HashMap> listSourceLanguagesLastModified() {
        Cursor cursor = db.rawQuery("select sl.slug, max(rf.modified_at) as modified_at from resource_format as rf"
                + " left join resource  as r on r.id=rf.resource_id"
                + " left join project as p on p.id=r.project_id"
                + " left join source_language as sl on sl.id=p.source_language_id"
                + " where rf.mime_type like(\"" + ResourceContainer.baseMimeType + "+%\")"
                + " group by sl.slug", null);
        cursor.moveToFirst();
        List<HashMap> langsLastModifiedList = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            int modifiedAt = reader.getInt("modified_at");

            HashMap sourceLanguageMap = new HashMap();
            sourceLanguageMap.put(slug, modifiedAt);
            langsLastModifiedList.add(sourceLanguageMap);
        }
        cursor.close();
        return langsLastModifiedList;
    }

    public Map<String, Integer> listProjectsLastModified(String languageSlug) {
        Cursor cursor = null;
        if(languageSlug != null || languageSlug != ""){
            cursor = db.rawQuery("select p.slug, max(rf.modified_at) as modified_at from resource_format as rf"
                + " left join resource  as r on r.id=rf.resource_id"
                + " left join project as p on p.id=r.project_id"
                + " left join source_language as sl on sl.id=p.source_language_id"
                + " where rf.mime_type like(\"" + ResourceContainer.baseMimeType + "+%\") and sl.slug=?"
                + " group by p.slug", new String[]{languageSlug});
        } else {
            cursor = db.rawQuery("select p.slug, max(rf.modified_at) as modified_at from resource_format as rf"
                + " left join resource  as r on r.id=rf.resource_id"
                + " left join project as p on p.id=r.project_id"
                + " left join source_language as sl on sl.id=p.source_language_id"
                + " where rf.mime_type like(\"" + ResourceContainer.baseMimeType + "+%\") and sl.slug=?"
                + " group by p.slug", null);
        }
        Map<String, Integer> projectsLastModifiedList = new HashMap();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);
            projectsLastModifiedList.put(reader.getString("slug"), reader.getInt("modified_at"));
            cursor.moveToNext();
        }
        cursor.close();
        return projectsLastModifiedList;
    }

    /**
     * Returns meta data about a project without any localized information such as the title or description
     * @param projectSlug the slug of the project who's meta will be returned
     * @return the project meta data
     */
    public JSONObject getProjectMeta(String projectSlug) {
        Cursor cursor = db.rawQuery("select * from project where slug=? limit 1", new String[]{projectSlug});
        JSONObject meta = null;
        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);
            try {
                meta = new JSONObject();
                meta.put("slug", reader.getString("slug"));
                meta.put("icon", reader.getString("icon"));
                meta.put("sort", reader.getString("sort"));
                meta.put("chunks_url", reader.getString("chunks_url"));
                meta.put("category_id", reader.getString("category_id"));
            } catch (JSONException e) {
                e.printStackTrace();
                meta = null;
            }
        }
        cursor.close();
        return meta;
    }

    public Translation getTranslation(String containerSlug) {
        try {
            String[] slugs = ContainerTools.explodeSlug(containerSlug);
            SourceLanguage l = getSourceLanguage(slugs[0]);
            Project p = getProject(slugs[0], slugs[1]);
            Resource r = getResource(slugs[0], slugs[1], slugs[2]);
            return new Translation(l, p, r);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Translation> findTranslations(String languageSlug, String projectSlug, String resourceSlug, String resourceType, String translateMode, int minCheckingLevel, int maxCheckingLevel) {
        String conditionMaxChecking = "";

        if(languageSlug == null || languageSlug.isEmpty()) languageSlug = "%";
        if(projectSlug == null || projectSlug.isEmpty()) projectSlug = "%";
        if(resourceSlug == null || resourceSlug.isEmpty()) resourceSlug = "%";
        if(resourceType == null || resourceType.isEmpty()) resourceType = "%";
        if(translateMode == null || translateMode.isEmpty()) translateMode = "%";

        if(maxCheckingLevel >= 0) conditionMaxChecking = " and r.checking_level <= " + maxCheckingLevel;

        List<Translation> translations = new ArrayList<>();
        Cursor cursor = db.rawQuery("select l.slug as language_slug, l.name as language_name, l.direction," +
                " p.slug as project_slug, p.name as project_name, p.desc, p.icon, p.sort, p.chunks_url," +
                " r.id as resource_id, r.slug as resource_slug, r.name as resource_name, r.type, r.translate_mode, r.checking_level, r.comments, r.pub_date, r.license, r.version," +
                " lri.translation_words_assignments_url" +
                " from source_language as l" +
                " left join project as p on p.source_language_id=l.id" +
                " left join (" +
                "   select r.*, count(rf.id) as num_imported from resource as r" +
                "   left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'" +
                "   group by r.id" +
                " ) as r on r.project_id=p.id" +
                " left join legacy_resource_info as lri on lri.resource_id=r.id" +
                " where l.slug like(?) and p.slug like(?) and r.slug like(?)" +
                " and (" +
                "   (" +
                "     r.checking_level >= " + minCheckingLevel + "" +
                      conditionMaxChecking +
                "     and r.translate_mode like(?)" +
                "   )" +
                "   or r.num_imported > 0" +
                " )" +
                " and r.type like(?)",
                new String[]{languageSlug, projectSlug, resourceSlug, translateMode, resourceType});

        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            Translation t = buildTranslation(new CursorReader(cursor));
            translations.add(t);
            cursor.moveToNext();
        }
        cursor.close();
        return translations;
    }

    public List<Translation> getImportedTranslations() {
        List<Translation> translations = new ArrayList<>();
        Cursor cursor = db.rawQuery("select l.slug as language_slug, l.name as language_name, l.direction," +
                        " p.slug as project_slug, p.name as project_name, p.desc, p.icon, p.sort, p.chunks_url," +
                        " r.id as resource_id, r.slug as resource_slug, r.name as resource_name, r.type, r.translate_mode, r.checking_level, r.comments, r.pub_date, r.license, r.version," +
                        " lri.translation_words_assignments_url" +
                        " from source_language as l" +
                        " left join project as p on p.source_language_id=l.id" +
                        " left join (" +
                        "   select r.*, count(rf.id) as num_imported from resource as r" +
                        "   left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'" +
                        "   group by r.id" +
                        " ) as r on r.project_id=p.id" +
                        " left join legacy_resource_info as lri on lri.resource_id=r.id" +
                        " where r.num_imported > 0",
                new String[]{});

        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            Translation t = buildTranslation(new CursorReader(cursor));
            translations.add(t);
            cursor.moveToNext();
        }
        cursor.close();
        return translations;
    }

    /**
     * Utility to build a new translation object from a db cursor reader
     * @param reader the reader
     * @return
     */
    private Translation buildTranslation(CursorReader reader) {
        Language l = new Language(reader.getString("language_slug"), reader.getString("language_name"), reader.getString("direction"));

        Project p = new Project(reader.getString("project_slug"), reader.getString("project_name"), reader.getInt("sort"));
        p.description = reader.getString("desc");
        p.icon = reader.getString("icon");
        p.chunksUrl = reader.getString("chunks_url");
        p.languageSlug = reader.getString("language_slug");

        Resource r = new Resource(reader.getString("resource_slug"), reader.getString("resource_name"),
                reader.getString("type"), reader.getString("translate_mode"), reader.getString("checking_level"), reader.getString("version"));
        r.comments = reader.getString("comments");
        r.pubDate = reader.getString("pub_date");
        r.license = reader.getString("license");
        r._legacyData.put(API.LEGACY_WORDS_ASSIGNMENTS_URL, reader.getString("translation_words_assignments_url"));

        // load formats and add to resource
        Cursor formatCursor = db.rawQuery("select * from resource_format where resource_id=" + reader.getLong("resource_id"), null);
        formatCursor.moveToFirst();
        while(!formatCursor.isAfterLast()) {
            CursorReader formatReader = new CursorReader(formatCursor);

            String packageVersion = formatReader.getString("package_version");
            String mimeType = formatReader.getString("mime_type");
            int modifiedAt = formatReader.getInt("modified_at");
            String url = formatReader.getString("url");
            boolean imported = formatReader.getBoolean("imported");

            Resource.Format format = new Resource.Format(packageVersion, mimeType, modifiedAt, url, imported);
            r.addFormat(format);
            formatCursor.moveToNext();
        }
        formatCursor.close();
        return new Translation(l, p, r);
    }

    public SourceLanguage getSourceLanguage(String sourceLanguageSlug) {
        Cursor cursor = db.rawQuery("select * from source_language where slug=? limit 1", new String[]{sourceLanguageSlug});
        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);

            String name = reader.getString("name");
            String direction = reader.getString("direction");

            SourceLanguage sourceLanguage = new SourceLanguage(sourceLanguageSlug, name, direction);
            cursor.close();
            return sourceLanguage;
        } else {
            cursor.close();
            return null;
        }
    }

    public List<SourceLanguage> getSourceLanguages() {
        Cursor cursor = db.rawQuery("select * from source_language order by slug asc", null);

        List<SourceLanguage> sourceLanguages = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");
            String direction = reader.getString("direction");

            SourceLanguage sourceLanguage = new SourceLanguage(slug, name, direction);
            sourceLanguages.add(sourceLanguage);
            cursor.moveToNext();
        }
        cursor.close();
        return sourceLanguages;
    }

    public List<SourceLanguage> getSourceLanguages(String projectSlug) {
        Cursor cursor = db.rawQuery("select * from source_language where id in (" +
                " select source_language_id from project" +
                " where slug=?" +
                " group by source_language_id" +
                ") order by slug asc", new String[]{projectSlug});

        List<SourceLanguage> sourceLanguages = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");
            String direction = reader.getString("direction");

            SourceLanguage sourceLanguage = new SourceLanguage(slug, name, direction);
            sourceLanguages.add(sourceLanguage);
            cursor.moveToNext();
        }
        cursor.close();
        return sourceLanguages;
    }

    public TargetLanguage getTargetLanguage(String targetLangaugeSlug) {
        TargetLanguage targetLanguage = null;
        Cursor cursor = db.rawQuery("select * from (" +
                "  select slug, name, anglicized_name, direction, region, is_gateway_language from target_language" +
                "  union" +
                "  select slug, name, anglicized_name, direction, region, is_gateway_language from temp_target_language" +
                "  where approved_target_language_slug is null" +
                ") where slug=? limit 1", new String[]{targetLangaugeSlug});

        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);

            String name = reader.getString("name");
            String anglicized = reader.getString("anglicized_name");
            String direction = reader.getString("direction");
            String region = reader.getString("region");
            boolean isGateWay = reader.getBoolean("is_gateway_language");

            targetLanguage = new TargetLanguage(targetLangaugeSlug, name, anglicized, direction, region, isGateWay);
        }
        cursor.close();

        return targetLanguage;
    }

    public List<TargetLanguage> findTargetLanguage(final String namequery) {
        List<TargetLanguage> targetLanguages = new ArrayList<>();
        Cursor cursor = db.rawQuery("select * from (" +
                "  select slug, name, anglicized_name, direction, region, is_gateway_language from target_language" +
                "  union" +
                "  select slug, name, anglicized_name, direction, region, is_gateway_language from temp_target_language" +
                "  where approved_target_language_slug is null" +
                ") where lower(name) like ?" +
                " order by slug asc, name asc", new String[]{"%" + namequery.toLowerCase() + "%"});

        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");
            String anglicized = reader.getString("anglicized_name");
            String direction = reader.getString("direction");
            String region = reader.getString("region");
            boolean isGateWay = reader.getBoolean("is_gateway_language");

            TargetLanguage targetLanguage = new TargetLanguage(slug, name, anglicized, direction, region, isGateWay);
            targetLanguages.add(targetLanguage);
            cursor.moveToNext();
        }
        cursor.close();

        Collections.sort(targetLanguages, new Comparator<TargetLanguage>() {
            @Override
            public int compare(TargetLanguage lhs, TargetLanguage rhs) {
                String lhId = lhs.slug;
                String rhId = rhs.slug;
                // give priority to matches with the id
                if(lhId.toLowerCase().startsWith(namequery.toLowerCase())) {
                    lhId = "!!" + lhId;
                }
                if(rhId.toLowerCase().startsWith(namequery.toLowerCase())) {
                    rhId = "!!" + rhId;
                }
                if(lhs.name.toLowerCase().startsWith(namequery.toLowerCase())) {
                    lhId = "!" + lhId;
                }
                if(rhs.name.toLowerCase().startsWith(namequery.toLowerCase())) {
                    rhId = "!" + rhId;
                }
                return lhId.compareToIgnoreCase(rhId);
            }
        });

        return targetLanguages;
    }

    public List<TargetLanguage> getTargetLanguages() {
        Cursor cursor = db.rawQuery("select * from (" +
                "  select slug, name, anglicized_name, direction, region, is_gateway_language from target_language" +
                "  union" +
                "  select slug, name, anglicized_name, direction, region, is_gateway_language from temp_target_language" +
                "  where approved_target_language_slug is null" +
                ") order by slug asc, name asc", null);

        List<TargetLanguage> languages = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");
            String angName = reader.getString("anglicized_name");
            String dir = reader.getString("direction");
            String region = reader.getString("region");
            boolean isGate = reader.getBoolean("is_gateway_language");

            TargetLanguage newLang = new TargetLanguage(slug, name, angName, dir, region, isGate);
            languages.add(newLang);
            cursor.moveToNext();
        }
        cursor.close();
        return languages;
    }

    public TargetLanguage getApprovedTargetLanguage(String tempTargetLanguageSlug) {
        TargetLanguage language = null;

        Cursor cursor = db.rawQuery("select tl.* from target_language as tl" +
                " left join temp_target_language as ttl on ttl.approved_target_language_slug=tl.slug" +
                " where ttl.slug=?", new String[]{tempTargetLanguageSlug});

        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);

            String approvedSlug = reader.getString("slug");
            String name = reader.getString("name");
            String angName = reader.getString("anglicized_name");
            String dir = reader.getString("direction");
            String region = reader.getString("region");
            boolean isGate = reader.getBoolean("is_gateway_language");

            language = new TargetLanguage(approvedSlug, name, angName, dir, region, isGate);
            cursor.close();
        }
        return language;
    }

    public Project getProject(String sourceLanguageSlug, String projectSlug) {
        return getProject(sourceLanguageSlug, projectSlug, false);
    }

    public Project getProject(String sourceLanguageSlug, String projectSlug, boolean enableDefaultLanguage) {
        Project project = null;
        Cursor cursor = db.rawQuery("select p.*, sl.slug as source_language_slug from project as p" +
                " left join source_language as sl on sl.id=p.source_language_id" +
                " where p.slug=? and sl.slug LIKE (?)" +
                " limit 1", new String[]{projectSlug, sourceLanguageSlug});

        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");
            String desc = reader.getString("desc");
            String icon = reader.getString("icon");
            int sort = reader.getInt("sort");
            String chunksUrl = reader.getString("chunks_url");

            project = new Project(slug, name, sort);
            project.description = desc;
            project.icon = icon;
            project.chunksUrl = chunksUrl;
            project.languageSlug = reader.getString("source_language_slug");
        }
        cursor.close();

        // attempt to fetch the default language
        if(project == null && enableDefaultLanguage) project = getProject("en", projectSlug, false);
        if(project == null && enableDefaultLanguage) project = getProject("%", projectSlug, false);

        return project;
    }

    public List<Project> getProjects(String sourceLanguageSlug) {
        return getProjects(sourceLanguageSlug, false);
    }

    public List<Project> getProjects(String sourceLanguageSlug, boolean enableDefaultLanguage) {
        List<Project> projects = new ArrayList<>();
        Cursor cursor;
        if(enableDefaultLanguage) {
            // TRICKY: this should work on android but is not working on the tests
            cursor = db.rawQuery("select p.*, sl.slug as source_language_slug," +
                " max(case sl.slug when ? then 3 when ? then 2 else 1 end) as weight" +
                " from project as p" +
                " left join source_language as sl on sl.id=p.source_language_id" +
                " group by p.slug" +
                " order by p.sort asc", new String[]{sourceLanguageSlug, "en"});

            // another alternative that "should" work
//            cursor = db.rawQuery("select p.*, sl.slug as source_language_slug, max(weight) from (" +
//                    "  select *, 3 as weight from project where source_language_id in (" +
//                    "    select id from source_language where slug=?" +
//                    "  )" +
//                    "  union" +
//                    "  select *, 2 as weight from project where source_language_id in (" +
//                    "    select id from source_language where slug=?" +
//                    "  )" +
//                    "  union" +
//                    "  select *, 1 as weight from project" +
//                    " ) as p" +
//                    " left join source_language as sl on sl.id=p.source_language_id" +
//                    " group by p.slug" +
//                    " order by p.sort asc", new String[]{sourceLanguageSlug, "en"});

            // another alternative that "should" work
//            cursor = db.rawQuery("select p.*, sl.slug as source_language_slug from project as p" +
//                    " left join source_language as sl on sl.id=p.source_language_id" +
//                    " where p.id in (" +
//                    "   select id from (" +
//                    "     select max(weight), id from (" +
//                    "       select *, 3 as weight from project where source_language_id in (" +
//                    "         select id from source_language where slug=?" +
//                    "       )" +
//                    "       union" +
//                    "       select *, 2 as weight from project where source_language_id in (" +
//                    "         select id from source_language where slug=?" +
//                    "       )" +
//                    "       union" +
//                    "       select *, 1 as weight from project" +
//                    "     )" +
//                    "     group by slug" +
//                    "   )" +
//                    " )" +
//                    " order by p.sort asc", new String[]{sourceLanguageSlug, "en"});
        } else {
            cursor = db.rawQuery("select * from project" +
                    " where source_language_id in (select id from source_language where slug=?)" +
                    " order by sort asc", new String[]{sourceLanguageSlug});
        }

        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");
            int sort = reader.getInt("sort");

            Project project = new Project(slug, name, sort);
            project.description = reader.getString("desc");
            project.icon = reader.getString("icon");
            project.chunksUrl = reader.getString("chunks_url");
            if(enableDefaultLanguage) {
                project.languageSlug = reader.getString("source_language_slug");
            } else {
                project.languageSlug = sourceLanguageSlug;
            }

            projects.add(project);
            cursor.moveToNext();
        }
        cursor.close();
        return projects;
    }

    // TODO: 9/28/16 or findProjectSourceLanguages(String projectSlug) so we can find languages that have this project
    // TODO: 9/28/16 potentially add getTranslationProgress
    // TODO: 9/28/16 allow filtering by checking level (resource containers, projects, resources).. maybe we could do with just resource containers.

    // we can use this in place of
    public List<CategoryEntry> getProjectCategories(long parentCategoryId, String languageSlug, String translateMode) {
        Cursor categoryCursor;
        String[] preferredSlug = {languageSlug, "en", "%"};
        List<CategoryEntry> projectCategories = new ArrayList<>();
        if(translateMode == null) translateMode = "";

        // load categories
        if(!translateMode.isEmpty()) {
            categoryCursor = db.rawQuery("select \'category\' as type, c.slug as name, \'\' as source_language_slug," +
                    " c.id, c.slug, c.parent_id, count(p.id) as num, max(p.sort) as csort from category as c" +
                    " left join (" +
                    "  select p.id, p.category_id, p.sort, count(r.id) as num from project as p" +
                    "  left join (" +
                    "   select r.*, count(rf.id) as num_imported from resource as r" +
                    "   left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'" +
                    "   group by r.id" +
                    "  ) as r on r.project_id=p.id and (r.translate_mode like (?) or r.num_imported > 0)" +
                    "  group by p.slug" +
                    " ) p on p.category_id=c.id and p.num > 0" +
                    " where parent_id=" + parentCategoryId + " and num > 0 " +
                    " group by c.slug" +
                    " order by csort", new String[]{translateMode});
        } else {
            // TODO: left join projects were so we can max the sort so we can order the categories.
            categoryCursor = db.rawQuery("select \'category\' as type, category.slug as name, \'\'" +
                    " as source_language_slug, *" +
                    " from category" +
                    " where parent_id=" + parentCategoryId +
                    " group by category.slug", null);
        }

        //find best name
        categoryCursor.moveToFirst();
        while(!categoryCursor.isAfterLast()) {
            String catSlug = categoryCursor.getString(categoryCursor.getColumnIndex("slug"));
            int catId = categoryCursor.getInt(categoryCursor.getColumnIndex("id"));

            for(String slug : preferredSlug) {
                Cursor cursor = db.rawQuery("select sl.slug as source_language_slug, cn.name as name" +
                        " from category_name as cn" +
                        " left join source_language as sl on sl.id=cn.source_language_id" +
                        " where sl.slug like(?) and cn.category_id=" + catId, new String[]{slug});

                if(cursor.moveToFirst()) {
                    CursorReader reader = new CursorReader(cursor);

                    String catName = reader.getString("name");
                    String catSourceLanguageSlug = reader.getString("source_language_slug");

                    CategoryEntry categoryEntry = new CategoryEntry(CategoryEntry.Type.CATEGORY, catId, catSlug, catName, catSourceLanguageSlug, parentCategoryId);
                    projectCategories.add(categoryEntry);
                    cursor.close();
                    break;
                }
                cursor.close();
            }
            categoryCursor.moveToNext();
        }
        categoryCursor.close();

        // load projects
        Cursor projectCursor = db.rawQuery("select * from (" +
                " select \'project\' as type, \'\' as source_language_slug," +
                " p.id, p.slug, p.sort, p.name, count(r.id) as num from project as p" +
                " left join (" +
                "  select r.*, count(rf.id) as num_imported from resource as r" +
                "  left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'" +
                "  group by r.id" +
                " ) as r on r.project_id=p.id and (r.translate_mode like (?) or r.num_imported > 0)" +
                " where p.category_id=" + parentCategoryId + " group by p.slug" +
                " order by p.sort asc)" + (!translateMode.isEmpty() ? " where num > 0" : ""),
                new String[]{(!translateMode.isEmpty() ? translateMode : "%")});

        //find best name
        projectCursor.moveToFirst();
        while(!projectCursor.isAfterLast()) {
            String projectSlug = projectCursor.getString(projectCursor.getColumnIndex("slug"));
            long projectId = projectCursor.getLong(projectCursor.getColumnIndex("id"));
            for(String slug : preferredSlug) {
                Cursor cursor = db.rawQuery("select sl.slug as source_language_slug, p.name as name" +
                        " from project as p" +
                        " left join source_language as sl on sl.id=p.source_language_id" +
                        " where sl.slug like(?) and p.slug=? order by sl.slug asc", new String[]{slug, projectSlug});
                if(cursor.moveToFirst()) {
                    CursorReader reader = new CursorReader(cursor);

                    String projectName = reader.getString("name");
                    String projSourceLangSlug = reader.getString("source_language_slug");

                    CategoryEntry categoryEntry = new CategoryEntry(CategoryEntry.Type.PROJECT, projectId, projectSlug, projectName, projSourceLangSlug, parentCategoryId);
                    projectCategories.add(categoryEntry);
                    cursor.close();
                    break;
                }
                cursor.close();
            }
            projectCursor.moveToNext();
        }
        projectCursor.close();

        return projectCategories;
    }

    @Nullable
    public Resource getResource(String sourceLanguageSlug, String projectSlug, String resourceSlug) {
        Resource resource = null;
        Cursor cursor = db.rawQuery("select r.id, r.name, r.translate_mode, r.type, r.checking_level," +
                " r.comments, r.pub_date, r.license, r.version," +
                " lri.translation_words_assignments_url from resource as r" +
                " left join legacy_resource_info as lri on lri.resource_id=r.id" +
                " where r.slug=? and r.project_id in (" +
                "  select id from project where slug=? and source_language_id in (" +
                "  select id from source_language where slug=?)" +
                " ) limit 1", new String[]{resourceSlug, projectSlug, sourceLanguageSlug});

        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);

            long resourceId = reader.getLong("id");
            String name = reader.getString("name");
            String translateMode = reader.getString("translate_mode");
            String type = reader.getString("type");
            String checkingLevel = reader.getString("checking_level");
            String comments = reader.getString("comments");
            String pubDate = reader.getString("pub_date");
            String license = reader.getString("license");
            String version = reader.getString("version");
            String wordsAssignmentsUrl = reader.getString("translation_words_assignments_url");

            resource = new Resource(resourceSlug, name, type, translateMode, checkingLevel, version);
            resource._legacyData.put(API.LEGACY_WORDS_ASSIGNMENTS_URL, wordsAssignmentsUrl);
            resource.comments = comments;
            resource.pubDate = pubDate;
            resource.license = license;

            // load formats and add to resource
            Cursor formatCursor = db.rawQuery("select * from resource_format where resource_id=" + resourceId, null);
            formatCursor.moveToFirst();
            while(!formatCursor.isAfterLast()) {
                CursorReader formatReader = new CursorReader(formatCursor);

                String packageVersion = formatReader.getString("package_version");
                String mimeType = formatReader.getString("mime_type");
                int modifiedAt = formatReader.getInt("modified_at");
                String url = formatReader.getString("url");
                boolean imported = formatReader.getBoolean("imported");

                Resource.Format format = new Resource.Format(packageVersion, mimeType, modifiedAt, url, imported);
                resource.addFormat(format);
                formatCursor.moveToNext();
            }
            formatCursor.close();
        }
        cursor.close();
        return resource;
    }

    public List<Resource> getResources(String languageSlug, String projectSlug) {
        List<Resource> resources = new ArrayList<>();
        Cursor resourceCursor = null;
        if(languageSlug != null && !languageSlug.isEmpty()) {
            resourceCursor = db.rawQuery("select r.*, lri.translation_words_assignments_url from resource as r" +
                    " left join legacy_resource_info as lri on lri.resource_id=r.id" +
                    " where r.project_id in (" +
                    "  select id from project where slug=? and source_language_id in (" +
                    "   select id from source_language where slug=?)" +
                    " )" +
                    " order by r.slug asc", new String[]{projectSlug, languageSlug});
        } else {
            resourceCursor = db.rawQuery("select sl.slug as source_language_slug, r.*, lri.translation_words_assignments_url from resource as r" +
                    " left join legacy_resource_info as lri on lri.resource_id=r.id" +
                    " left join project as p on p.id=r.project_id" +
                    " left join (" +
                    "  select id, slug from source_language" +
                    " ) as sl on sl.id=p.source_language_id" +
                    " where p.slug=? order by r.slug asc", new String[]{projectSlug});
        }

        resourceCursor.moveToFirst();
        while(!resourceCursor.isAfterLast()) {
            CursorReader reader = new CursorReader(resourceCursor);

            long resourceId = reader.getLong("id");
            String slug = reader.getString("slug");
            String name = reader.getString("name");
            String translateMode = reader.getString("translate_mode");
            String type = reader.getString("type");
            String checkingLevel = reader.getString("checking_level");
            String comments = reader.getString("comments");
            String pubDate = reader.getString("pub_date");
            String license = reader.getString("license");
            String version = reader.getString("version");
            String wordsAssignmentsUrl = reader.getString("translation_words_assignments_url");

            HashMap status = new HashMap();
            status.put("translateMode", translateMode);
            status.put("checkingLevel", checkingLevel);
            status.put("comments", comments);
            status.put("pub_date", pubDate);
            status.put("license", license);
            status.put("version", version);

            Resource resource = new Resource(slug, name, type, translateMode, checkingLevel, version);
            resource._legacyData.put(API.LEGACY_WORDS_ASSIGNMENTS_URL, wordsAssignmentsUrl);
            resource.comments = comments;
            resource.pubDate = pubDate;
            resource.license = license;

            // load formats and add to resource
            Cursor formatCursor = db.rawQuery("select * from resource_format where resource_id=" + resourceId, null);
            formatCursor.moveToFirst();
            while(!formatCursor.isAfterLast()) {
                CursorReader formatReader = new CursorReader(formatCursor);

                String packageVersion = formatReader.getString("package_version");
                String mimeType = formatReader.getString("mime_type");
                int modifiedAt = formatReader.getInt("modified_at");
                String url = formatReader.getString("url");
                boolean imported = formatReader.getBoolean("imported");

                Resource.Format format = new Resource.Format(packageVersion, mimeType, modifiedAt, url, imported);
                resource.addFormat(format);
                formatCursor.moveToNext();
            }
            formatCursor.close();

            resources.add(resource);
            resourceCursor.moveToNext();
        }
        resourceCursor.close();
        return resources;
    }

    public Catalog getCatalog(String catalogSlug) {
        Catalog catalog = null;
        Cursor cursor = db.rawQuery("select id, url, modified_at from catalog where slug=?", new String[]{catalogSlug});
        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);

            String url = reader.getString("url");
            int modifiedAt = reader.getInt("modified_at");

            catalog = new Catalog(catalogSlug, url, modifiedAt);
        }
        cursor.close();
        return catalog;
    }

    public List<Catalog> getCatalogs() {
        Cursor cursor = db.rawQuery("select * from catalog", null);

        List<Catalog> catalogs = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String url = reader.getString("url");
            int modifiedAt = reader.getInt("modified_at");

            Catalog catalog = new Catalog(slug, url, modifiedAt);
            catalogs.add(catalog);
            cursor.moveToNext();
        }
        cursor.close();
        return catalogs;
    }

    public Versification getVersification(String sourceLanguageSlug, String versificationSlug) {
        Versification versification = null;
        Cursor cursor = db.rawQuery("select v.id, v.slug, vn.name from versification_name as vn" +
                " left join versification as v on v.id=vn.versification_id" +
                " left join source_language as sl on sl.id=vn.source_language_id" +
                " where sl.slug=? and v.slug=?", new String[]{sourceLanguageSlug, versificationSlug});

        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");

            versification = new Versification(slug, name);
            versification.rowId = reader.getLong("id");
        }
        cursor.close();
        return versification;
    }

    public List<Versification> getVersifications(String sourceLanguageSlug) {
        Cursor cursor = db.rawQuery("select vn.name, v.slug, v.id from versification_name as vn" +
                " left join versification as v on v.id=vn.versification_id" +
                " left join source_language as sl on sl.id=vn.source_language_id" +
                " where sl.slug=?", new String[]{sourceLanguageSlug});

        List<Versification> versifications = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String slug = reader.getString("slug");
            String name = reader.getString("name");

            Versification versification = new Versification(slug, name);
            versification.rowId = reader.getLong("id");
            versifications.add(versification);
            cursor.moveToNext();
        }
        cursor.close();
        return versifications;
    }

    public List<ChunkMarker> getChunkMarkers(String projectSlug, String versificationSlug) {
        Cursor cursor = db.rawQuery("select cm.id, cm.chapter, cm.verse from chunk_marker as cm" +
                " left join versification as v on v.id=cm.versification_id" +
                " where v.slug=? and cm.project_slug=?", new String[]{versificationSlug, projectSlug});

        List<ChunkMarker> chunkMarkers = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String chapter = reader.getString("chapter");
            String verse = reader.getString("verse");

            ChunkMarker chunkMarker = new ChunkMarker(chapter, verse);
            chunkMarkers.add(chunkMarker);
            cursor.moveToNext();
        }
        cursor.close();
        return chunkMarkers;
    }

    public Questionnaire getQuestionnaire(long tdId) {
        Questionnaire questionnaire = null;
        Cursor cursor = db.rawQuery("select * from questionnaire" +
                " where td_id=" + tdId, null);
        cursor.moveToFirst();
        if(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            // load data fields
            Cursor dataFieldsCursor = db.rawQuery("select field, question_td_id from questionnaire_data_field" +
                    " where questionnaire_id=" + reader.getLong("id"), null);
            Map<String, Long> dataFields = new HashMap<>();
            dataFieldsCursor.moveToFirst();
            while(!dataFieldsCursor.isAfterLast()) {
                CursorReader dataFieldReader = new CursorReader(dataFieldsCursor);
                dataFields.put(dataFieldReader.getString("field"), dataFieldReader.getLong("question_td_id"));
                dataFieldsCursor.moveToNext();
            }
            dataFieldsCursor.close();;

            questionnaire = new Questionnaire(reader.getString("language_slug"),
                    reader.getString("language_name"),
                    reader.getString("language_direction"),
                    reader.getLong("td_id"),
                    dataFields);
        }
        cursor.close();
        return questionnaire;
    }

    public List<Questionnaire> getQuestionnaires() {
        Cursor cursor = db.rawQuery("select * from questionnaire", null);

        List<Questionnaire> questionnaires = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            long id = reader.getLong("id");
            String slug = reader.getString("language_slug");
            String name = reader.getString("language_name");
            String direction = reader.getString("language_direction");
            long tdId = reader.getLong("td_id");

            // load data fields
            Cursor dataFieldsCursor = db.rawQuery("select field, question_td_id from questionnaire_data_field" +
                    " where questionnaire_id=" + id, null);
            Map<String, Long> dataFields = new HashMap<>();
            dataFieldsCursor.moveToFirst();
            while(!dataFieldsCursor.isAfterLast()) {
                CursorReader dataFieldReader = new CursorReader(dataFieldsCursor);
                dataFields.put(dataFieldReader.getString("field"), dataFieldReader.getLong("question_td_id"));
                dataFieldsCursor.moveToNext();
            }
            dataFieldsCursor.close();;

            Questionnaire questionnaire = new Questionnaire(slug, name, direction, tdId, dataFields);
            questionnaires.add(questionnaire);
            cursor.moveToNext();
        }
        cursor.close();
        return questionnaires;
    }

    public List<Question> getQuestions(long questionnaireTDId) {
        Cursor cursor = db.rawQuery("select * from question where questionnaire_id in (" +
                " select id from questionnaire where td_id=" + questionnaireTDId + ")" +
                " order by sort asc", null);

        List<Question> questions = new ArrayList<>();
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            CursorReader reader = new CursorReader(cursor);

            String text = reader.getString("text");
            String help = reader.getString("help");
            boolean isRequired = reader.getBoolean("is_required");
            String inputType = reader.getString("input_type");
            int sort = reader.getInt("sort");
            long dependsOn = reader.getInt("depends_on");
            long tdId = reader.getInt("td_id");

            Question question = new Question(text, help, isRequired, Question.InputType.get(inputType), sort, dependsOn, tdId);
            questions.add(question);
            cursor.moveToNext();
        }
        cursor.close();
        return questions;
    }

    public Category getCategory(String languageSlug, String slug) {
        Cursor cursor = db.rawQuery("select cn.name, c.slug from category as c" +
                " left join category_name as cn on cn.category_id=c.id" +
                " left join source_language as sl on sl.id=cn.source_language_id" +
                " where c.slug=? and sl.slug=?", new String[]{slug, languageSlug});
        Category cat = null;
        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);
            cat = new Category(reader.getString("slug"), reader.getString("name"));
        }
        cursor.close();
        return cat;
    }

    /**
     * Returns the parent category of the given category
     * @param languageSlug the language of the parent category name
     * @param childCategorySlug the slug of the child category
     * @return the parent category of null if there is no parent
     */
    private Category getParentCategory(String languageSlug, String childCategorySlug) {
        Cursor cursor = db.rawQuery("select pcn.name, pc.slug from category as pc" +
                " left join category as cc on cc.parent_id=pc.id" +
                " left join category_name as pcn on pcn.category_id=pc.id" +
                " left join source_language as sl on sl.id=pcn.source_language_id" +
                " where cc.slug=? and sl.slug=?", new String[]{childCategorySlug, languageSlug});
        Category cat = null;
        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);
            cat = new Category(reader.getString("slug"), reader.getString("name"));
        }
        cursor.close();
        return cat;
    }

    public List<Category> getCategories(String languageSlug, String projectSlug) {
        List<Category> categories = new ArrayList<>();
        Cursor cursor = db.rawQuery("select c.slug, cn.name from category as c" +
                " left join category_name as cn on cn.category_id=c.id" +
                " left join source_language as sl on sl.id=cn.source_language_id" +
                " left join project as p on p.source_language_id=sl.id and p.category_id=c.id" +
                " where p.slug=? and sl.slug=?", new String[]{projectSlug, languageSlug});
        if(cursor.moveToFirst()) {
            CursorReader reader = new CursorReader(cursor);
            Category cat = new Category(reader.getString("slug"), reader.getString("name"));
            categories.add(cat);
            cursor.close();

            // find the rest of the categories
            String previousSlug = cat.slug;
            Category nextCat = null;
            do {
                nextCat = getParentCategory(languageSlug, previousSlug);
                if(nextCat != null) {
                    previousSlug = nextCat.slug;
                    categories.add(0, nextCat);
                }
            } while (nextCat != null);
        } else {
            cursor.close();
        }

        return categories;
    }

    /**
     * Removes all target language data
     */
    public void clearTargetLanguages() {
        truncateTable("target_language");
        vacuum();
    }

    /**
     * Removes all the temp target language data
     */
    public void clearTempLanguages() {
        truncateTable("temp_target_language");
        vacuum();
    }

    /**
     * Removes all questionnaire data
     */
    public void clearNewLanguageQuestions() {
        truncateTable("questionnaire_data_field");
        truncateTable("question");
        truncateTable("questionnaire");
        vacuum();
    }

    /**
     * Clears out all assigned target languages.
     * This does not actually truncate anything just sets all the approved slugs to null.
     */
    public void clearApprovedTempLanguages() {
        db.rawQuery("update temp_target_language set approved_target_language_slug=null", null);
    }

    /**
     * A tool to remove all data from a table.
     * Use this with caution.
     * @param table the table that will lose all it's data.
     */
    protected void truncateTable(String table) {
        db.rawQuery("delete from ".concat(table), null);
    }

    /**
     * Cleans the database.
     * This eliminates free pages, aligns table data to be contiguous,
     * and otherwise cleans up the database file structure.
     */
    protected void vacuum() {
        try {
            db.rawQuery("vacuum", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Represents the result from and insertOrIgnore or insertOrUpdate
     */
    private static class InsertResult {
        public final boolean inserted;
        public final long id;

        /**
         *
         * @param id the id of the record
         * @param inserted set to true if the record was a new insert and false if it is an existing one
         */
        public InsertResult(long id, boolean inserted) {
            this.id = id;
            this.inserted = inserted;
        }
    }

    /**
     * A helper class to make reading from a cursor easier.
     */
    private static class CursorReader {
        private final Cursor cursor;

        public CursorReader(Cursor cursor) {
            this.cursor =cursor;
        }

        public String getString(String key)  {
            return this.cursor.getString(this.cursor.getColumnIndexOrThrow(key));
        }

        public long getLong(String key) {
            return this.cursor.getLong(this.cursor.getColumnIndexOrThrow(key));
        }

        public int getInt(String key) {
            return this.cursor.getInt(this.cursor.getColumnIndexOrThrow(key));
        }

        public boolean getBoolean(String key) {
            return this.cursor.getInt(this.cursor.getColumnIndexOrThrow(key)) > 0;
        }
    }
}