package org.unfoldingword.door43client

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.models.Catalog
import org.unfoldingword.door43client.models.Category
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.ChunkMarker
import org.unfoldingword.door43client.models.Question
import org.unfoldingword.door43client.models.Questionnaire
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.door43client.models.Versification
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.io.IOException

/**
 * Manages the indexed library content.
 */
internal class Library @Throws(IOException::class) constructor(
    private val sqliteHelper: SQLiteHelper
) : Index {

    private val db: SQLiteDatabase = sqliteHelper.writableDatabase

    init {
        if (db.version == 0) {
            throw IOException(
                "Invalid database version. You probably manually generated the database " +
                        "and forgot to set the \"User Version\" to ${SQLiteHelper.DATABASE_VERSION}"
            )
        }
    }

    override fun yieldSafely() {
        db.yieldIfContendedSafely()
    }

    fun beginTransaction() {
        db.beginTransactionNonExclusive()
    }

    fun endTransaction(success: Boolean) {
        if (success) {
            db.setTransactionSuccessful()
        }
        db.endTransaction()
    }

    fun closeDatabase() {
        sqliteHelper.close()
    }

    @Throws(Exception::class)
    private fun validateNotEmpty(value: String?) {
        if (value.isNullOrBlank()) throw Exception("Invalid parameter value")
    }

    private fun deNull(value: String?): String = value ?: ""

    @Synchronized
    private fun insertOrIgnore(table: String, values: ContentValues, uniqueColumns: Array<String>): InsertResult {
        var error: Exception?
        try {
            val id = db.insertOrThrow(table, null, values)
            return InsertResult(id, true)
        } catch (e: SQLException) {
            error = e
        }

        var id: Long = -1
        val where = WhereClause.prepare(values, uniqueColumns)

        db.rawQuery("select id from $table where ${where.statement}", where.arguments).use { cursor ->
            if (cursor.moveToFirst()) {
                id = cursor.getLong(0)
            }
        }

        if (id == -1L) error.printStackTrace()
        return InsertResult(id, false)
    }

    @Synchronized
    @Throws(Exception::class)
    private fun insertOrUpdate(table: String, values: ContentValues, uniqueColumns: Array<String>): InsertResult {
        val result = insertOrIgnore(table, values, uniqueColumns)
        if (!result.inserted) {
            val where = WhereClause.prepare(values, uniqueColumns)

            // clean values
            val updateValues = ContentValues(values)
            for (key in uniqueColumns) {
                updateValues.remove(key)
            }

            val numRows = db.updateWithOnConflict(table, updateValues, where.statement, where.arguments, SQLiteDatabase.CONFLICT_ROLLBACK)
            if (numRows == 0) {
                throw Exception("Failed to update the row in $table")
            } else {
                db.rawQuery("select id from $table where ${where.statement}", where.arguments).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return InsertResult(cursor.getLong(0), false)
                    } else {
                        throw Exception("Failed to find the row in $table")
                    }
                }
            }
        } else {
            return result
        }
    }

    @Throws(Exception::class)
    override fun addSourceLanguage(language: SourceLanguage): Long {
        validateNotEmpty(language.slug)
        validateNotEmpty(language.name)
        validateNotEmpty(language.direction)

        val values = ContentValues().apply {
            put("slug", language.slug)
            put("name", language.name)
            put("direction", language.direction)
        }

        return insertOrUpdate("source_language", values, arrayOf("slug")).id
    }

    @Throws(Exception::class)
    override fun addTargetLanguage(language: TargetLanguage): Boolean {
        validateNotEmpty(language.slug)
        validateNotEmpty(language.name)
        validateNotEmpty(language.direction)

        val values = ContentValues().apply {
            put("slug", language.slug)
            put("name", language.name)
            put("direction", language.direction)
            put("anglicized_name", deNull(language.anglicizedName))
            put("region", deNull(language.region))
            put("is_gateway_language", if (language.isGatewayLanguage) 1 else 0)
        }

        return insertOrUpdate("target_language", values, arrayOf("slug")).id > 0
    }

    @Throws(Exception::class)
    override fun addTempTargetLanguage(language: TargetLanguage): Boolean {
        validateNotEmpty(language.slug)
        validateNotEmpty(language.name)
        validateNotEmpty(language.direction)

        val values = ContentValues().apply {
            put("slug", language.slug)
            put("name", language.name)
            put("direction", language.direction)
            put("anglicized_name", deNull(language.anglicizedName))
            put("region", deNull(language.region))
            put("is_gateway_language", if (language.isGatewayLanguage) 1 else 0)
        }

        return insertOrUpdate("temp_target_language", values, arrayOf("slug")).id > 0
    }

    @Throws(Exception::class)
    fun setApprovedTargetLanguage(tempTargetLanguageSlug: String, targetLanguageSlug: String): Boolean {
        validateNotEmpty(tempTargetLanguageSlug)
        validateNotEmpty(targetLanguageSlug)

        val values = ContentValues().apply {
            put("approved_target_language_slug", targetLanguageSlug)
        }

        val rowsAffected = db.updateWithOnConflict(
            "temp_target_language", values,
            "slug=?", arrayOf(tempTargetLanguageSlug), SQLiteDatabase.CONFLICT_IGNORE
        )

        return rowsAffected > 0
    }

    @Throws(Exception::class)
    override fun addProject(project: Project, categories: List<Category>?, sourceLanguageId: Long): Long {
        validateNotEmpty(project.slug)
        validateNotEmpty(project.name)

        var parentCategoryId: Long = 0
        categories?.forEach { category ->
            validateNotEmpty(category.slug)
            validateNotEmpty(category.name)

            val insertValues = ContentValues().apply {
                put("slug", category.slug)
                put("parent_id", parentCategoryId)
            }

            val id = insertOrIgnore("category", insertValues, arrayOf("slug", "parent_id")).id
            if (id > 0) {
                parentCategoryId = id
            } else {
                throw Exception("Invalid category")
            }

            val updateValues = ContentValues().apply {
                put("source_language_id", sourceLanguageId)
                put("category_id", parentCategoryId)
                put("name", category.name)
            }

            insertOrUpdate("category_name", updateValues, arrayOf("source_language_id", "category_id"))
        }

        val updateProject = ContentValues().apply {
            put("slug", project.slug)
            put("name", project.name)
            put("desc", deNull(project.description))
            put("icon", deNull(project.icon))
            put("sort", project.sort)
            put("chunks_url", deNull(project.chunksUrl))
            put("source_language_id", sourceLanguageId)
            put("category_id", parentCategoryId)
        }

        return insertOrUpdate("project", updateProject, arrayOf("slug", "source_language_id")).id
    }

    @Throws(Exception::class)
    override fun addVersification(versification: Versification, sourceLanguageId: Long): Long {
        validateNotEmpty(versification.slug)
        validateNotEmpty(versification.name)

        val values = ContentValues().apply {
            put("slug", versification.slug)
        }

        val versificationId = insertOrIgnore("versification", values, arrayOf("slug")).id
        if (versificationId > 0) {
            val cv = ContentValues().apply {
                put("source_language_id", sourceLanguageId)
                put("versification_id", versificationId)
                put("name", versification.name)
            }
            insertOrUpdate("versification_name", cv, arrayOf("source_language_id", "versification_id"))
        } else {
            throw Exception("Invalid versification")
        }
        return versificationId
    }

    @Throws(Exception::class)
    fun addChunkMarker(chunk: ChunkMarker, projectSlug: String, versificationId: Long): Long {
        validateNotEmpty(chunk.chapter)
        validateNotEmpty(chunk.verse)
        validateNotEmpty(projectSlug)

        val chunkValues = ContentValues().apply {
            put("chapter", chunk.chapter)
            put("verse", chunk.verse)
            put("project_slug", projectSlug)
            put("versification_id", versificationId)
        }

        val id = insertOrIgnore("chunk_marker", chunkValues, arrayOf("project_slug", "versification_id", "chapter", "verse")).id
        if (id == -1L) {
            throw Exception("Invalid Chunk Marker")
        }
        return id
    }

    @Throws(Exception::class)
    override fun addCatalog(catalog: Catalog): Long {
        validateNotEmpty(catalog.slug)
        validateNotEmpty(catalog.url)

        val values = ContentValues().apply {
            put("slug", catalog.slug)
            put("url", catalog.url)
            put("modified_at", catalog.modifiedAt)
        }

        return insertOrUpdate("catalog", values, arrayOf("slug")).id
    }

    @Throws(Exception::class)
    override fun addResource(resource: Resource, projectId: Long): Long {
        validateNotEmpty(resource.slug)
        validateNotEmpty(resource.name)
        validateNotEmpty(resource.type)
        validateNotEmpty(if (resource.formats.isNotEmpty()) "good" else null)
        validateNotEmpty(resource.translateMode)
        validateNotEmpty(resource.checkingLevel)
        validateNotEmpty(resource.version)

        val values = ContentValues().apply {
            put("slug", resource.slug)
            put("name", resource.name)
            put("type", resource.type)
            put("translate_mode", resource.translateMode)
            put("checking_level", resource.checkingLevel)
            put("comments", deNull(resource.comments))
            put("pub_date", deNull(resource.pubDate))
            put("license", deNull(resource.license))
            put("version", resource.version)
            put("project_id", projectId)
        }

        val resourceId = insertOrUpdate("resource", values, arrayOf("slug", "project_id")).id

        resource.formats.forEach { format ->
            validateNotEmpty(format.mimeType)
            val formatValues = ContentValues().apply {
                put("package_version", format.packageVersion)
                put("mime_type", format.mimeType)
                put("imported", if (format.imported) 1 else 0)
                put("modified_at", format.modifiedAt)
                put("url", deNull(format.url))
                put("resource_id", resourceId)
            }
            insertOrUpdate("resource_format", formatValues, arrayOf("mime_type", "resource_id"))
        }

        val legacyUrl = resource._legacyData[API.LEGACY_WORDS_ASSIGNMENTS_URL] as? String
        if (!legacyUrl.isNullOrEmpty()) {
            val legacyValues = ContentValues().apply {
                put("translation_words_assignments_url", legacyUrl)
                put("resource_id", resourceId)
            }
            insertOrUpdate("legacy_resource_info", legacyValues, arrayOf("resource_id"))
        }
        return resourceId
    }

    @Throws(Exception::class)
    fun addQuestionnaire(questionnaire: Questionnaire): Long {
        validateNotEmpty(questionnaire.languageSlug)
        validateNotEmpty(questionnaire.languageName)
        validateNotEmpty(questionnaire.languageDirection)

        val values = ContentValues().apply {
            put("language_slug", questionnaire.languageSlug)
            put("language_name", questionnaire.languageName)
            put("language_direction", questionnaire.languageDirection)
            put("td_id", questionnaire.tdId)
        }

        val id = insertOrUpdate("questionnaire", values, arrayOf("td_id", "language_slug")).id

        questionnaire.dataFields.forEach { (key, value) ->
            val fieldValues = ContentValues().apply {
                put("questionnaire_id", id)
                put("field", key)
                put("question_td_id", value)
            }
            insertOrUpdate("questionnaire_data_field", fieldValues, arrayOf("field", "questionnaire_id"))
        }

        return id
    }

    @Throws(Exception::class)
    fun addQuestion(question: Question, questionnaireId: Long): Long {
        validateNotEmpty(question.text)
        validateNotEmpty("ok")

        val values = ContentValues().apply {
            put("text", question.text)
            put("help", deNull(question.help))
            put("is_required", if (question.isRequired) 1 else 0)
            put("input_type", question.inputType.toString())
            put("sort", question.sort)
            put("depends_on", question.dependsOn)
            put("td_id", question.tdId)
            put("questionnaire_id", questionnaireId)
        }

        return insertOrUpdate("question", values, arrayOf("td_id", "questionnaire_id")).id
    }

    override fun listSourceLanguagesLastModified(): List<Map<String, Any>> {
        val query = """
            select sl.slug, max(rf.modified_at) as modified_at from resource_format as rf
            left join resource as r on r.id=rf.resource_id
            left join project as p on p.id=r.project_id
            left join source_language as sl on sl.id=p.source_language_id
            where rf.mime_type like("${ResourceContainer.baseMimeType}+%")
            group by sl.slug
        """.trimIndent()

        val results = mutableListOf<Map<String, Any>>()
        db.rawQuery(query, null).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(mapOf(reader.getString("slug") to reader.getInt("modified_at")))
            }
        }
        return results
    }

    override fun listProjectsLastModified(languageSlug: String?): Map<String, Int> {
        val query = """
            select p.slug, max(rf.modified_at) as modified_at from resource_format as rf
            left join resource as r on r.id=rf.resource_id
            left join project as p on p.id=r.project_id
            left join source_language as sl on sl.id=p.source_language_id
            where rf.mime_type like("${ResourceContainer.baseMimeType}+%") and sl.slug LIKE ?
            group by p.slug
        """.trimIndent()

        val args = arrayOf(languageSlug ?: "%")
        val results = mutableMapOf<String, Int>()
        db.rawQuery(query, args).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results[reader.getString("slug")] = reader.getInt("modified_at")
            }
        }
        return results
    }

    fun getProjectMeta(projectSlug: String): JSONObject? {
        db.rawQuery("select * from project where slug=? limit 1", arrayOf(projectSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return try {
                    JSONObject().apply {
                        put("slug", reader.getString("slug"))
                        put("icon", reader.getString("icon"))
                        put("sort", reader.getString("sort"))
                        put("chunks_url", reader.getString("chunks_url"))
                        put("category_id", reader.getString("category_id"))
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    null
                }
            }
        }
        return null
    }

    override fun getTranslation(containerSlug: String): Translation? {
        return try {
            val slugs = ContainerTools.explodeSlug(containerSlug)
            val l = getSourceLanguage(slugs[0])
            val p = getProject(slugs[0], slugs[1])
            val r = getResource(slugs[0], slugs[1], slugs[2])
            if (l != null && p != null && r != null) Translation(l, p, r) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun findTranslations(
        languageSlug: String?,
        projectSlug: String?,
        resourceSlug: String?,
        resourceType: String?,
        translateMode: String?,
        minCheckingLevel: Int,
        maxCheckingLevel: Int
    ): List<Translation> {
        val lSlug = if (languageSlug.isNullOrEmpty()) "%" else languageSlug
        val pSlug = if (projectSlug.isNullOrEmpty()) "%" else projectSlug
        val rSlug = if (resourceSlug.isNullOrEmpty()) "%" else resourceSlug
        val rType = if (resourceType.isNullOrEmpty()) "%" else resourceType
        val tMode = if (translateMode.isNullOrEmpty()) "%" else translateMode

        val conditionMaxChecking = if (maxCheckingLevel >= 0) " and r.checking_level <= $maxCheckingLevel" else ""

        val query = """
            select l.slug as language_slug, l.name as language_name, l.direction,
            p.slug as project_slug, p.name as project_name, p.desc, p.icon, p.sort, p.chunks_url,
            r.id as resource_id, r.slug as resource_slug, r.name as resource_name, r.type, r.translate_mode, r.checking_level, r.comments, r.pub_date, r.license, r.version,
            lri.translation_words_assignments_url
            from source_language as l
            left join project as p on p.source_language_id=l.id
            left join (
              select r.*, count(rf.id) as num_imported from resource as r
              left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'
              group by r.id
            ) as r on r.project_id=p.id
            left join legacy_resource_info as lri on lri.resource_id=r.id
            where l.slug like(?) and p.slug like(?) and r.slug like(?)
            and (
              (r.checking_level >= $minCheckingLevel $conditionMaxChecking and r.translate_mode like(?))
              or r.num_imported > 0
            )
            and r.type like(?)
        """.trimIndent()

        val results = mutableListOf<Translation>()
        db.rawQuery(query, arrayOf(lSlug, pSlug, rSlug, tMode, rType)).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(buildTranslation(reader))
            }
        }
        return results
    }

    override fun getImportedTranslations(): List<Translation> {
        val query = """
            select l.slug as language_slug, l.name as language_name, l.direction,
            p.slug as project_slug, p.name as project_name, p.`desc`, p.icon, p.sort, p.chunks_url,
            r.id as resource_id, r.slug as resource_slug, r.name as resource_name, r.type, r.translate_mode, r.checking_level, r.comments, r.pub_date, r.license, r.version,
            lri.translation_words_assignments_url
            from source_language as l
            left join project as p on p.source_language_id=l.id
            left join (
              select r.*, count(rf.id) as num_imported from resource as r
              left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'
              group by r.id
            ) as r on r.project_id=p.id
            left join legacy_resource_info as lri on lri.resource_id=r.id
            where r.num_imported > 0
        """.trimIndent()

        val results = mutableListOf<Translation>()
        db.rawQuery(query, null).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(buildTranslation(reader))
            }
        }
        return results
    }

    private fun buildTranslation(reader: CursorReader): Translation {
        val l = Language(reader.getString("language_slug"), reader.getString("language_name"), reader.getString("direction"))

        val p = Project(reader.getString("project_slug"), reader.getString("project_name"), reader.getInt("sort")).apply {
            description = reader.getString("desc")
            icon = reader.getString("icon")
            chunksUrl = reader.getString("chunks_url")
            languageSlug = reader.getString("language_slug")
        }

        val r = Resource(
            reader.getString("resource_slug"), reader.getString("resource_name"),
            reader.getString("type"), reader.getString("translate_mode"), reader.getString("checking_level"), reader.getString("version")
        ).apply {
            comments = reader.getString("comments")
            pubDate = reader.getString("pub_date")
            license = reader.getString("license")
            _legacyData[API.LEGACY_WORDS_ASSIGNMENTS_URL] = reader.getString("translation_words_assignments_url")
        }

        db.rawQuery("select * from resource_format where resource_id=${reader.getLong("resource_id")}", null).use { formatCursor ->
            val formatReader = CursorReader(formatCursor)
            while (formatCursor.moveToNext()) {
                r.addFormat(
                    Resource.Format(
                        formatReader.getString("package_version"),
                        formatReader.getString("mime_type"),
                        formatReader.getInt("modified_at"),
                        formatReader.getString("url"),
                        formatReader.getBoolean("imported")
                    )
                )
            }
        }
        return Translation(l, p, r)
    }

    override fun getSourceLanguage(sourceLanguageSlug: String): SourceLanguage? {
        db.rawQuery("select * from source_language where slug=? limit 1", arrayOf(sourceLanguageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return SourceLanguage(sourceLanguageSlug, reader.getString("name"), reader.getString("direction"))
            }
        }
        return null
    }

    override fun getSourceLanguages(): List<SourceLanguage> {
        val results = mutableListOf<SourceLanguage>()
        db.rawQuery("select * from source_language order by slug asc", null).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(SourceLanguage(reader.getString("slug"), reader.getString("name"), reader.getString("direction")))
            }
        }
        return results
    }

    override fun getSourceLanguages(projectSlug: String): List<SourceLanguage> {
        val query = """
            select * from source_language where id in (
              select source_language_id from project where slug=? group by source_language_id
            ) order by slug asc
        """.trimIndent()

        val results = mutableListOf<SourceLanguage>()
        db.rawQuery(query, arrayOf(projectSlug)).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(SourceLanguage(reader.getString("slug"), reader.getString("name"), reader.getString("direction")))
            }
        }
        return results
    }

    override fun getTargetLanguage(targetLanguageSlug: String): TargetLanguage? {
        val query = """
            select * from (
              select slug, name, anglicized_name, direction, region, is_gateway_language from target_language
              union
              select slug, name, anglicized_name, direction, region, is_gateway_language from temp_target_language
              where approved_target_language_slug is null
            ) where slug=? limit 1
        """.trimIndent()

        db.rawQuery(query, arrayOf(targetLanguageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return TargetLanguage(
                    targetLanguageSlug, reader.getString("name"), reader.getString("anglicized_name"),
                    reader.getString("direction"), reader.getString("region"), reader.getBoolean("is_gateway_language")
                )
            }
        }
        return null
    }

    override fun findTargetLanguage(nameQuery: String): List<TargetLanguage> {
        val query = """
            select * from (
              select slug, name, anglicized_name, direction, region, is_gateway_language from target_language
              union
              select slug, name, anglicized_name, direction, region, is_gateway_language from temp_target_language
              where approved_target_language_slug is null
            ) where lower(name) like ? order by slug asc, name asc
        """.trimIndent()

        val results = mutableListOf<TargetLanguage>()
        db.rawQuery(query, arrayOf("%${nameQuery.lowercase()}%")).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(
                    TargetLanguage(
                        reader.getString("slug"), reader.getString("name"), reader.getString("anglicized_name"),
                        reader.getString("direction"), reader.getString("region"), reader.getBoolean("is_gateway_language")
                    )
                )
            }
        }

        val nameLower = nameQuery.lowercase()
        results.sortWith { lhs, rhs ->
            var lhId = lhs.slug
            var rhId = rhs.slug
            if (lhId.lowercase().startsWith(nameLower)) lhId = "!!$lhId"
            if (rhId.lowercase().startsWith(nameLower)) rhId = "!!$rhId"
            if (lhs.name.lowercase().startsWith(nameLower)) lhId = "!$lhId"
            if (rhs.name.lowercase().startsWith(nameLower)) rhId = "!$rhId"
            lhId.compareTo(rhId, ignoreCase = true)
        }
        return results
    }

    override fun getTargetLanguages(): List<TargetLanguage> {
        val query = """
            select * from (
              select slug, name, anglicized_name, direction, region, is_gateway_language from target_language
              union
              select slug, name, anglicized_name, direction, region, is_gateway_language from temp_target_language
              where approved_target_language_slug is null
            ) order by slug asc, name asc
        """.trimIndent()

        val results = mutableListOf<TargetLanguage>()
        db.rawQuery(query, null).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(
                    TargetLanguage(
                        reader.getString("slug"), reader.getString("name"), reader.getString("anglicized_name"),
                        reader.getString("direction"), reader.getString("region"), reader.getBoolean("is_gateway_language")
                    )
                )
            }
        }
        return results
    }

    override fun getApprovedTargetLanguage(tempTargetLanguageSlug: String): TargetLanguage? {
        val query = """
            select tl.* from target_language as tl
            left join temp_target_language as ttl on ttl.approved_target_language_slug=tl.slug
            where ttl.slug=?
        """.trimIndent()

        db.rawQuery(query, arrayOf(tempTargetLanguageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return TargetLanguage(
                    reader.getString("slug"), reader.getString("name"), reader.getString("anglicized_name"),
                    reader.getString("direction"), reader.getString("region"), reader.getBoolean("is_gateway_language")
                )
            }
        }
        return null
    }

    override fun getProject(
        sourceLanguageSlug: String,
        projectSlug: String,
        enableDefaultLanguage: Boolean
    ): Project? {
        val query = """
            select p.*, sl.slug as source_language_slug from project as p
            left join source_language as sl on sl.id=p.source_language_id
            where p.slug=? and sl.slug LIKE (?) limit 1
        """.trimIndent()

        var project: Project? = null
        db.rawQuery(query, arrayOf(projectSlug, sourceLanguageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                project = Project(reader.getString("slug"), reader.getString("name"), reader.getInt("sort")).apply {
                    description = reader.getString("desc")
                    icon = reader.getString("icon")
                    chunksUrl = reader.getString("chunks_url")
                    languageSlug = reader.getString("source_language_slug")
                }
            }
        }

        if (project == null && enableDefaultLanguage) project = getProject("en", projectSlug, false)
        if (project == null && enableDefaultLanguage) project = getProject("%", projectSlug, false)

        return project
    }

    override fun getProjects(
        sourceLanguageSlug: String,
        enableDefaultLanguage: Boolean
    ): List<Project> {
        val results = mutableListOf<Project>()
        val query = if (enableDefaultLanguage) {
            """
                select p.*, sl.slug as source_language_slug,
                max(case sl.slug when ? then 3 when ? then 2 else 1 end) as weight
                from project as p
                left join source_language as sl on sl.id=p.source_language_id
                group by p.slug order by p.sort asc
            """.trimIndent()
        } else {
            """
                select * from project where source_language_id in (select id from source_language where slug=?)
                order by sort asc
            """.trimIndent()
        }

        val args = if (enableDefaultLanguage) arrayOf(sourceLanguageSlug, "en") else arrayOf(sourceLanguageSlug)

        db.rawQuery(query, args).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                val project = Project(reader.getString("slug"), reader.getString("name"), reader.getInt("sort")).apply {
                    description = reader.getString("desc")
                    icon = reader.getString("icon")
                    chunksUrl = reader.getString("chunks_url")
                    languageSlug = if (enableDefaultLanguage) reader.getString("source_language_slug") else sourceLanguageSlug
                }
                results.add(project)
            }
        }
        return results
    }

    override fun getProjectCategories(
        parentCategoryId: Long,
        languageSlug: String,
        translateMode: String?
    ): List<CategoryEntry> {
        val mode = translateMode ?: ""
        val preferredSlugs = arrayOf(languageSlug, "en", "%")
        val projectCategories = mutableListOf<CategoryEntry>()

        val categoryQuery = if (mode.isNotEmpty()) {
            """
                select 'category' as type, c.slug as name, '' as source_language_slug,
                c.id, c.slug, c.parent_id, count(p.id) as num, max(p.sort) as csort from category as c
                left join (
                  select p.id, p.category_id, p.sort, count(r.id) as num from project as p
                  left join (
                    select r.*, count(rf.id) as num_imported from resource as r
                    left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'
                    group by r.id
                  ) as r on r.project_id=p.id and (r.translate_mode like (?) or r.num_imported > 0)
                  group by p.slug
                ) p on p.category_id=c.id and p.num > 0
                where parent_id=$parentCategoryId and num > 0 group by c.slug order by csort
            """.trimIndent()
        } else {
            "select 'category' as type, slug as name, '' as source_language_slug, * from category where parent_id=$parentCategoryId group by slug"
        }

        val catArgs = if (mode.isNotEmpty()) arrayOf(mode) else null
        db.rawQuery(categoryQuery, catArgs).use { cursor ->
            while (cursor.moveToNext()) {
                val catSlug = cursor.getString(cursor.getColumnIndexOrThrow("slug"))
                val catId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))

                for (slug in preferredSlugs) {
                    var found = false
                    db.rawQuery(
                        "select sl.slug as source_language_slug, cn.name as name from category_name as cn left join source_language as sl on sl.id=cn.source_language_id where sl.slug like(?) and cn.category_id=$catId",
                        arrayOf(slug)
                    ).use { nameCursor ->
                        if (nameCursor.moveToFirst()) {
                            val reader = CursorReader(nameCursor)
                            projectCategories.add(CategoryEntry(CategoryEntry.Type.CATEGORY, catId.toLong(), catSlug, reader.getString("name"), reader.getString("source_language_slug"), parentCategoryId))
                            found = true
                        }
                    }
                    if (found) break
                }
            }
        }

        val projectQuery = """
            select * from (
              select 'project' as type, '' as source_language_slug, p.id, p.slug, p.sort, p.name, count(r.id) as num from project as p
              left join (
                select r.*, count(rf.id) as num_imported from resource as r
                left join resource_format as rf on rf.resource_id=r.id and rf.imported='1'
                group by r.id
              ) as r on r.project_id=p.id and (r.translate_mode like (?) or r.num_imported > 0)
              where p.category_id=$parentCategoryId group by p.slug order by p.sort asc
            ) ${if (mode.isNotEmpty()) "where num > 0" else ""}
        """.trimIndent()

        db.rawQuery(projectQuery, arrayOf(if (mode.isNotEmpty()) mode else "%")).use { cursor ->
            while (cursor.moveToNext()) {
                val projectSlug = cursor.getString(cursor.getColumnIndexOrThrow("slug"))
                val projectId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))

                for (slug in preferredSlugs) {
                    var found = false
                    db.rawQuery(
                        "select sl.slug as source_language_slug, p.name as name from project as p left join source_language as sl on sl.id=p.source_language_id where sl.slug like(?) and p.slug=? order by sl.slug asc",
                        arrayOf(slug, projectSlug)
                    ).use { nameCursor ->
                        if (nameCursor.moveToFirst()) {
                            val reader = CursorReader(nameCursor)
                            projectCategories.add(CategoryEntry(CategoryEntry.Type.PROJECT, projectId, projectSlug, reader.getString("name"), reader.getString("source_language_slug"), parentCategoryId))
                            found = true
                        }
                    }
                    if (found) break
                }
            }
        }
        return projectCategories
    }

    override fun getResource(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): Resource? {
        val query = """
            select r.id, r.name, r.translate_mode, r.type, r.checking_level, r.comments, r.pub_date, r.license, r.version,
            lri.translation_words_assignments_url from resource as r
            left join legacy_resource_info as lri on lri.resource_id=r.id
            where r.slug=? and r.project_id in (
              select id from project where slug=? and source_language_id in (select id from source_language where slug=?)
            ) limit 1
        """.trimIndent()

        db.rawQuery(query, arrayOf(resourceSlug, projectSlug, sourceLanguageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                val resourceId = reader.getLong("id")
                val resource = Resource(
                    resourceSlug, reader.getString("name"), reader.getString("type"),
                    reader.getString("translate_mode"), reader.getString("checking_level"), reader.getString("version")
                ).apply {
                    _legacyData[API.LEGACY_WORDS_ASSIGNMENTS_URL] = reader.getString("translation_words_assignments_url")
                    comments = reader.getString("comments")
                    pubDate = reader.getString("pub_date")
                    license = reader.getString("license")
                }

                db.rawQuery("select * from resource_format where resource_id=$resourceId", null).use { formatCursor ->
                    val formatReader = CursorReader(formatCursor)
                    while (formatCursor.moveToNext()) {
                        resource.addFormat(
                            Resource.Format(
                                formatReader.getString("package_version"), formatReader.getString("mime_type"),
                                formatReader.getInt("modified_at"), formatReader.getString("url"), formatReader.getBoolean("imported")
                            )
                        )
                    }
                }
                return resource
            }
        }
        return null
    }

    override fun getResources(sourceLanguageSlug: String?, projectSlug: String): List<Resource> {
        val query = if (!sourceLanguageSlug.isNullOrEmpty()) {
            """
                select r.*, lri.translation_words_assignments_url from resource as r
                left join legacy_resource_info as lri on lri.resource_id=r.id
                where r.project_id in (
                  select id from project where slug=? and source_language_id in (select id from source_language where slug=?)
                ) order by r.slug asc
            """.trimIndent()
        } else {
            """
                select sl.slug as source_language_slug, r.*, lri.translation_words_assignments_url from resource as r
                left join legacy_resource_info as lri on lri.resource_id=r.id
                left join project as p on p.id=r.project_id
                left join source_language as sl on sl.id=p.source_language_id
                where p.slug=? order by r.slug asc
            """.trimIndent()
        }

        val args = if (!sourceLanguageSlug.isNullOrEmpty()) arrayOf(projectSlug, sourceLanguageSlug) else arrayOf(projectSlug)
        val results = mutableListOf<Resource>()

        db.rawQuery(query, args).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                val resourceId = reader.getLong("id")
                val resource = Resource(
                    reader.getString("slug"), reader.getString("name"), reader.getString("type"),
                    reader.getString("translate_mode"), reader.getString("checking_level"), reader.getString("version")
                ).apply {
                    _legacyData[API.LEGACY_WORDS_ASSIGNMENTS_URL] = reader.getString("translation_words_assignments_url")
                    comments = reader.getString("comments")
                    pubDate = reader.getString("pub_date")
                    license = reader.getString("license")
                }

                db.rawQuery("select * from resource_format where resource_id=$resourceId", null).use { formatCursor ->
                    val formatReader = CursorReader(formatCursor)
                    while (formatCursor.moveToNext()) {
                        resource.addFormat(
                            Resource.Format(
                                formatReader.getString("package_version"), formatReader.getString("mime_type"),
                                formatReader.getInt("modified_at"), formatReader.getString("url"), formatReader.getBoolean("imported")
                            )
                        )
                    }
                }
                results.add(resource)
            }
        }
        return results
    }

    override fun getCatalog(catalogSlug: String): Catalog? {
        db.rawQuery("select id, url, modified_at from catalog where slug=?", arrayOf(catalogSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return Catalog(catalogSlug, reader.getString("url"), reader.getInt("modified_at"))
            }
        }
        return null
    }

    override fun getCatalogs(): List<Catalog> {
        val results = mutableListOf<Catalog>()
        db.rawQuery("select * from catalog", null).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(Catalog(reader.getString("slug"), reader.getString("url"), reader.getInt("modified_at")))
            }
        }
        return results
    }

    override fun getVersification(sourceLanguageSlug: String, versificationSlug: String): Versification? {
        val query = """
            select v.id, v.slug, vn.name from versification_name as vn
            left join versification as v on v.id=vn.versification_id
            left join source_language as sl on sl.id=vn.source_language_id
            where sl.slug=? and v.slug=?
        """.trimIndent()

        db.rawQuery(query, arrayOf(sourceLanguageSlug, versificationSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return Versification(reader.getString("slug"), reader.getString("name")).apply {
                    rowId = reader.getLong("id")
                }
            }
        }
        return null
    }

    override fun getVersifications(sourceLanguageSlug: String): List<Versification> {
        val query = """
            select vn.name, v.slug, v.id from versification_name as vn
            left join versification as v on v.id=vn.versification_id
            left join source_language as sl on sl.id=vn.source_language_id
            where sl.slug=?
        """.trimIndent()

        val results = mutableListOf<Versification>()
        db.rawQuery(query, arrayOf(sourceLanguageSlug)).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(
                    Versification(reader.getString("slug"), reader.getString("name")).apply {
                        rowId = reader.getLong("id")
                    }
                )
            }
        }
        return results
    }

    override fun getChunkMarkers(projectSlug: String, versificationSlug: String): List<ChunkMarker> {
        val query = """
            select cm.id, cm.chapter, cm.verse from chunk_marker as cm
            left join versification as v on v.id=cm.versification_id
            where v.slug=? and cm.project_slug=?
        """.trimIndent()

        val results = mutableListOf<ChunkMarker>()
        db.rawQuery(query, arrayOf(versificationSlug, projectSlug)).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(ChunkMarker(reader.getString("chapter"), reader.getString("verse")))
            }
        }
        return results
    }

    override fun getQuestionnaire(tdId: Long): Questionnaire? {
        db.rawQuery("select * from questionnaire where td_id=$tdId", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                val id = reader.getLong("id")

                val dataFields = mutableMapOf<String, Long>()
                db.rawQuery("select field, question_td_id from questionnaire_data_field where questionnaire_id=$id", null).use { dataCursor ->
                    val dataReader = CursorReader(dataCursor)
                    while (dataCursor.moveToNext()) {
                        dataFields[dataReader.getString("field")] = dataReader.getLong("question_td_id")
                    }
                }

                return Questionnaire(
                    reader.getString("language_slug"), reader.getString("language_name"),
                    reader.getString("language_direction"), reader.getLong("td_id"), dataFields
                )
            }
        }
        return null
    }

    override fun getQuestionnaires(): List<Questionnaire> {
        val results = mutableListOf<Questionnaire>()
        db.rawQuery("select * from questionnaire", null).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                val id = reader.getLong("id")
                val dataFields = mutableMapOf<String, Long>()
                db.rawQuery("select field, question_td_id from questionnaire_data_field where questionnaire_id=$id", null).use { dataCursor ->
                    val dataReader = CursorReader(dataCursor)
                    while (dataCursor.moveToNext()) {
                        dataFields[dataReader.getString("field")] = dataReader.getLong("question_td_id")
                    }
                }
                results.add(
                    Questionnaire(
                        reader.getString("language_slug"), reader.getString("language_name"),
                        reader.getString("language_direction"), reader.getLong("td_id"), dataFields
                    )
                )
            }
        }
        return results
    }

    override fun getQuestions(questionnaireTDId: Long): List<Question> {
        val query = """
            select * from question where questionnaire_id in (
              select id from questionnaire where td_id=$questionnaireTDId
            ) order by sort asc
        """.trimIndent()

        val results = mutableListOf<Question>()
        db.rawQuery(query, null).use { cursor ->
            val reader = CursorReader(cursor)
            while (cursor.moveToNext()) {
                results.add(
                    Question(
                        reader.getString("text"), reader.getString("help"), reader.getBoolean("is_required"),
                        Question.InputType.get(reader.getString("input_type")), reader.getInt("sort"),
                        reader.getLong("depends_on"), reader.getLong("td_id")
                    )
                )
            }
        }
        return results
    }

    override fun getCategory(languageSlug: String, slug: String): Category? {
        val query = """
            select cn.name, c.slug from category as c
            left join category_name as cn on cn.category_id=c.id
            left join source_language as sl on sl.id=cn.source_language_id
            where c.slug=? and sl.slug=?
        """.trimIndent()

        db.rawQuery(query, arrayOf(slug, languageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return Category(reader.getString("slug"), reader.getString("name"))
            }
        }
        return null
    }

    private fun getParentCategory(languageSlug: String, childCategorySlug: String): Category? {
        val query = """
            select pcn.name, pc.slug from category as pc
            left join category as cc on cc.parent_id=pc.id
            left join category_name as pcn on pcn.category_id=pc.id
            left join source_language as sl on sl.id=pcn.source_language_id
            where cc.slug=? and sl.slug=?
        """.trimIndent()

        db.rawQuery(query, arrayOf(childCategorySlug, languageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                return Category(reader.getString("slug"), reader.getString("name"))
            }
        }
        return null
    }

    override fun getCategories(languageSlug: String, projectSlug: String): List<Category> {
        val categories = mutableListOf<Category>()
        val query = """
            select c.slug, cn.name from category as c
            left join category_name as cn on cn.category_id=c.id
            left join source_language as sl on sl.id=cn.source_language_id
            left join project as p on p.source_language_id=sl.id and p.category_id=c.id
            where p.slug=? and sl.slug=?
        """.trimIndent()

        db.rawQuery(query, arrayOf(projectSlug, languageSlug)).use { cursor ->
            if (cursor.moveToFirst()) {
                val reader = CursorReader(cursor)
                val cat = Category(reader.getString("slug"), reader.getString("name"))
                categories.add(cat)

                var previousSlug = cat.slug
                while (true) {
                    val nextCat = getParentCategory(languageSlug, previousSlug) ?: break
                    previousSlug = nextCat.slug
                    categories.add(0, nextCat)
                }
            }
        }
        return categories
    }

    fun clearTargetLanguages() {
        truncateTable("target_language")
        vacuum()
    }

    fun clearTempLanguages() {
        truncateTable("temp_target_language")
        vacuum()
    }

    fun clearNewLanguageQuestions() {
        truncateTable("questionnaire_data_field")
        truncateTable("question")
        truncateTable("questionnaire")
        vacuum()
    }

    fun clearApprovedTempLanguages() {
        db.execSQL("update temp_target_language set approved_target_language_slug=null")
    }

    protected fun truncateTable(table: String) {
        db.execSQL("delete from $table")
    }

    protected fun vacuum() {
        try {
            db.execSQL("vacuum")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private data class InsertResult(val id: Long, val inserted: Boolean)

    private class CursorReader(private val cursor: Cursor) {
        fun getString(key: String): String {
            val index = cursor.getColumnIndexOrThrow(key)
            return if (cursor.isNull(index)) "" else cursor.getString(index)
        }
        fun getLong(key: String): Long =
            cursor.getLong(cursor.getColumnIndexOrThrow(key))
        fun getInt(key: String): Int =
            cursor.getInt(cursor.getColumnIndexOrThrow(key))
        fun getBoolean(key: String): Boolean =
            cursor.getInt(cursor.getColumnIndexOrThrow(key)) > 0
    }
}