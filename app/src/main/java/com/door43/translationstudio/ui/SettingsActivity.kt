package com.door43.translationstudio.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App.Companion.configureLogger
import com.door43.translationstudio.App.Companion.updateColorTheme
import com.door43.translationstudio.R
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.legal.LegalDocumentActivity
import com.door43.translationstudio.ui.viewmodels.SettingsViewModel
import com.door43.usecases.CheckForLatestRelease
import com.door43.util.TTFAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import java.util.Arrays
import javax.inject.Inject

/**
 * A [SettingsActivity] that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 *
 *
 * See [
 * Android Design: Settings](http://developer.android.com/design/patterns/settings.html) for design guidelines and the [Settings
 * API Guide](http://developer.android.com/guide/topics/ui/settings.html) for more information on developing a Settings UI.
 *
 * NOTE: if you add new preference categories be sure to update MainApplication to load their default values.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.title_activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, GeneralPreferenceFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @AndroidEntryPoint
    class GeneralPreferenceFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        @Inject lateinit var directoryProvider: IDirectoryProvider
        @Inject lateinit var prefRepository: IPreferenceRepository

        var progressDialog: ProgressHelper.ProgressDialog? = null
        private val viewModel: SettingsViewModel by viewModels()

        private var initSettings = true
        private var _delegate: AppCompatDelegate? = null
        private val delegate: AppCompatDelegate
            get() {
                if (_delegate == null) {
                    _delegate = AppCompatDelegate.create(requireActivity(), null)
                }
                return _delegate!!
            }

        internal inner class CaseInsensitiveComparator : Comparator<String> {
            override fun compare(strA: String, strB: String): Int {
                return strA.compareTo(strB, ignoreCase = true)
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            view.fitsSystemWindows = true
            return view
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            initSettings = true

            progressDialog = ProgressHelper.newInstance(
                parentFragmentManager,
                R.string.loading,
                false
            )

            setupObservers()

            val preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())

            // Add 'general' preferences.
            addPreferencesFromResource(R.xml.general_preferences)

            setPreferenceSummaryFromValue(KEY_PREF_COLOR_THEME)

            val am = resources.assets
            var fileList: Array<String>? = null
            val entries = ArrayList<String>()
            val entryValues = ArrayList<String>()
            try {
                fileList = am.list("fonts")
            } catch (e: IOException) {
                Logger.e(this.javaClass.name, "failed to load font assets", e)
            }
            if (fileList != null) {
                Arrays.sort(fileList, CaseInsensitiveComparator())
                for (i in fileList.indices) {
                    val typeface = directoryProvider.getAssetAsFile("fonts/" + fileList[i])
                    if (typeface != null) {
                        val analyzer = TTFAnalyzer()
                        var fontname: String? = ""
                        fontname = analyzer.getTtfFontName(typeface.absolutePath)
                        if (fontname == null) {
                            fontname = typeface.name.substring(0, typeface.name.lastIndexOf("."))
                        }
                        entries.add(fontname)
                        entryValues.add(fileList[i])
                    }
                }
            }

            val pref = findPreference(KEY_PREF_TRANSLATION_TYPEFACE) as? ListPreference
            pref?.entries = entries.toTypedArray<CharSequence>()
            pref?.entryValues = entryValues.toTypedArray<CharSequence>()
            setPreferenceSummaryFromValue(KEY_PREF_TRANSLATION_TYPEFACE)

            setPreferenceSummaryFromValue(KEY_PREF_TRANSLATION_TYPEFACE_SIZE)

            val fontSourcePref = findPreference(KEY_PREF_SOURCE_TYPEFACE) as? ListPreference
            fontSourcePref?.entries = entries.toTypedArray<CharSequence>()
            fontSourcePref?.entryValues = entryValues.toTypedArray<CharSequence>()
            setPreferenceSummaryFromValue(KEY_PREF_SOURCE_TYPEFACE)

            setPreferenceSummaryFromValue(KEY_PREF_SOURCE_TYPEFACE_SIZE)

            val appUpdates: Preference? = findPreference(KEY_PREF_APP_UPDATES)
            appUpdates?.setOnPreferenceClickListener {
                viewModel.checkForLatestRelease()
                true
            }

            // Add 'server' preferences.
            addPreferencesFromResource(R.xml.server_preferences)

            // Add 'legal' preferences.
            addPreferencesFromResource(R.xml.legal_preferences)

            // add 'advanced' preferences
            addPreferencesFromResource(R.xml.advanced_preferences)

            // bind the correct legal document to the preference intent
            bindPreferenceClickToLegalDocument(
                KEY_PREF_LICENSE_AGREEMENT,
                R.string.license_pdf
            )
            bindPreferenceClickToLegalDocument(
                KEY_PREF_STATEMENT_OF_FAITH,
                R.string.statement_of_faith
            )
            bindPreferenceClickToLegalDocument(
                KEY_PREF_TRANSLATION_GUIDELINES,
                R.string.translation_guidlines
            )
            bindPreferenceClickToLegalDocument(
                KEY_PREF_SOFTWARE_LICENSES,
                R.string.software_licenses
            )

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
            // their values. When their values change, their summaries are updated
            // to reflect the new value, per the Android Design guidelines.
            setPreferenceSummaryFromValue(KEY_PREF_CONTENT_SERVER)
            setPreferenceSummaryFromValue(KEY_PREF_GIT_SERVER_PORT)
            setPreferenceSummaryFromValue(KEY_PREF_GOGS_API)
            setPreferenceSummaryFromValue(KEY_PREF_MEDIA_SERVER)
            setPreferenceSummaryFromValue(KEY_PREF_READER_SERVER)
            setPreferenceSummaryFromValue(KEY_PREF_CREATE_ACCOUNT_URL)
            setPreferenceSummaryFromValue(KEY_PREF_LANGUAGES_URL)
            setPreferenceSummaryFromValue(KEY_PREF_INDEX_SQLITE_URL)
            setPreferenceSummaryFromValue(KEY_PREF_LOGGING_LEVEL)
            setPreferenceSummaryFromValue(KEY_PREF_BACKUP_INTERVAL)

            val appVersionPref: Preference? = findPreference(KEY_PREF_APP_VERSION)
            try {
                val pInfo = requireActivity().packageManager.getPackageInfo(
                    requireActivity().packageName,
                    0
                )
                appVersionPref?.summary = pInfo.versionName + " - " + pInfo.versionCode
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }

            initSettings = false
        }

        private fun setupObservers() {
            viewModel.progress.observe(this) {
                if (it != null) {
                    progressDialog?.show()
                    progressDialog?.setProgress(it.progress)
                    progressDialog?.setMessage(it.message)
                    progressDialog?.setMax(it.max)
                } else {
                    progressDialog?.dismiss()
                }
            }
            viewModel.latestRelease.observe(this) {
                it?.let { result ->
                    val hand = Handler(Looper.getMainLooper())
                    hand.post {
                        if (result.release != null) {
                            promptUserToDownloadLatestVersion(result.release)
                        } else {
                            AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog)
                                .setTitle(R.string.check_for_updates)
                                .setMessage(R.string.have_latest_app_update)
                                .setPositiveButton(R.string.label_ok, null)
                                .show()
                        }
                    }
                }
            }
            viewModel.loggedOut.observe(this) {
                if (it == true) doLogout()
            }
        }

        /**
         * Intercepts clicks and passes the resource to the intent.
         * The preference should be configured as an action to an intent for the LegalDocumentActivity
         * @param key
         * @param res
         */
        private fun bindPreferenceClickToLegalDocument(key: String, res: Int) {
            val preference: Preference? = findPreference(key)
            preference?.let { pref ->
                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    val intent = pref.intent
                    intent?.putExtra(LegalDocumentActivity.ARG_RESOURCE, res)
                    it.intent = intent
                    false
                }
            }
        }

        /**
         * Sets a preference's summary based on its value. More specifically, when the
         * preference's value is changed, its summary (line of text below the
         * preference title) is updated to reflect the value. The exact display format is
         * dependent on the type of preference.
         *
         */
        private fun setPreferenceSummaryFromValue(key: String) {
            val preference: Preference? = findPreference(key)

            preference?.let { pref ->
                val value = prefRepository.getDefaultPref(preference.key, "")

                if (pref is ListPreference) {
                    // For list preferences, look up the correct display value in
                    // the preference's 'entries' list.
                    val index = pref.findIndexOfValue(value)

                    // Set the summary to reflect the new value.
                    pref.setSummary(
                        if (index >= 0) pref.entries[index] else null
                    )
                } else {
                    // For all other preferences, set the summary to the value's
                    // simple string representation.
                    pref.summary = value
                }
            }
        }

        private fun updateGitServerPrefs(newValue: String) {
            println("Git server changed, newValue:$newValue")

            val values = resources.getStringArray(R.array.content_server_values_array)
            val serverNames = resources.getStringArray(R.array.content_server_names_array)
            val index = listOf(*values).indexOf(newValue)

            val contentServer = findPreference(KEY_PREF_CONTENT_SERVER) as? ListPreference
            contentServer?.summary = serverNames[index]

            val gitServerPorts = resources.getStringArray(
                R.array.content_server_git_server_port_values_array
            )
            val gitServerPort = findPreference(KEY_PREF_GIT_SERVER_PORT) as? EditTextPreference
            gitServerPort?.text = gitServerPorts[index]
            gitServerPort?.summary = gitServerPorts[index]

            val gitServerApis = resources.getStringArray(
                R.array.content_server_git_server_api_values_array
            )
            val gitServerApi = findPreference(KEY_PREF_GOGS_API) as? EditTextPreference
            gitServerApi?.text = gitServerApis[index]
            gitServerApi?.summary = gitServerApis[index]

            val mediaServers = resources.getStringArray(
                R.array.content_server_media_server_values_array
            )
            val mediaServer = findPreference(KEY_PREF_MEDIA_SERVER) as? EditTextPreference
            mediaServer?.text = mediaServers[index]
            mediaServer?.summary = mediaServers[index]

            val readerServers = resources.getStringArray(
                R.array.content_server_reader_server_values_array
            )
            val readerServer = findPreference(KEY_PREF_READER_SERVER) as? EditTextPreference
            readerServer?.text = readerServers[index]
            readerServer?.summary = readerServers[index]

            val createAccountUrls = resources.getStringArray(
                R.array.content_server_account_create_urls_array
            )
            val createAccountUrl =
                findPreference(KEY_PREF_CREATE_ACCOUNT_URL) as? EditTextPreference
            createAccountUrl?.text = createAccountUrls[index]
            createAccountUrl?.summary = createAccountUrls[index]

            val langNameUrls = resources.getStringArray(
                R.array.content_server_lang_names_url_array
            )
            val langNameUrl = findPreference(KEY_PREF_LANGUAGES_URL) as? EditTextPreference
            langNameUrl?.text = langNameUrls[index]
            langNameUrl?.summary = langNameUrls[index]

            val indexSqliteUrls = resources.getStringArray(
                R.array.content_server_index_sqlite_url_array
            )
            val indexSqliteUrl = findPreference(KEY_PREF_INDEX_SQLITE_URL) as? EditTextPreference
            indexSqliteUrl?.text = indexSqliteUrls[index]
            indexSqliteUrl?.summary = indexSqliteUrls[index]
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen
                .sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen
                .sharedPreferences
                ?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            // Ignore preferences which values are booleans
            if (key == KEY_PREF_ALWAYS_SHARE || key == KEY_PREF_CHECK_HARDWARE) {
                return
            }

            sharedPreferences?.getString(key, "")?.let { value ->
                when (key) {
                    KEY_PREF_BACKUP_INTERVAL -> {
                        // restart the backup service.
                        if (BackupService.isRunning && !initSettings) {
                            val context = requireContext()
                            Logger.i("Settings", "Re-loading backup settings")
                            val backupIntent = Intent(requireContext(), BackupService::class.java)
                            context.stopService(backupIntent)
                            context.startService(backupIntent)
                        }
                    }
                    KEY_PREF_LOGGING_LEVEL -> {
                        // TODO: only re-configure if changed
                        configureLogger(value.toInt())
                    }
                    KEY_PREF_LANGUAGES_URL -> {
                        try {
                            viewModel.updateLanguageUrl(value)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    KEY_PREF_CONTENT_SERVER -> {
                        updateGitServerPrefs(value)
                        viewModel.logout()
                    }
                    KEY_PREF_COLOR_THEME -> {
                        Logger.i(
                            this.javaClass.simpleName,
                            "Color theme changed, newValue:$value"
                        )
                        updateColorTheme(value)
                        delegate.applyDayNight()
                        requireActivity().recreate()
                    }
                }

                key?.let { setPreferenceSummaryFromValue(it) }
            }
        }

        /**
         * ask the user if they want to download the latest version
         */
        private fun promptUserToDownloadLatestVersion(
            release: CheckForLatestRelease.Release
        ) {
            AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                .setTitle(R.string.apk_update_available)
                .setMessage(R.string.download_latest_apk)
                .setPositiveButton(R.string.label_ok) { _, _ ->
                    viewModel.downloadLatestRelease(release)
                }
                .setNegativeButton(R.string.title_cancel, null)
                .show()
        }

        private fun doLogout() {
            val logoutIntent = Intent(requireActivity(), ProfileActivity::class.java)
            startActivity(logoutIntent)
            requireActivity().finish()
        }
    }

    companion object {
        const val KEY_PREF_CONTENT_SERVER = "content_server"
        const val KEY_PREF_GIT_SERVER_PORT = "git_server_port"
        const val KEY_PREF_ALWAYS_SHARE = "always_share"
        const val KEY_PREF_MEDIA_SERVER = "media_server"
        const val KEY_PREF_READER_SERVER = "reader_server"
        const val KEY_PREF_CREATE_ACCOUNT_URL = "create_account_url"
        const val KEY_PREF_LANGUAGES_URL = "lang_names_url"
        const val KEY_PREF_INDEX_SQLITE_URL = "index_sqlite_url"
        const val KEY_PREF_COLOR_THEME = "color_theme"

        const val KEY_PREF_TRANSLATION_TYPEFACE = "translation_typeface"
        const val KEY_PREF_TRANSLATION_TYPEFACE_SIZE = "typeface_size"
        const val KEY_PREF_SOURCE_TYPEFACE = "source_typeface"
        const val KEY_PREF_SOURCE_TYPEFACE_SIZE = "source_typeface_size"

        const val KEY_PREF_LOGGING_LEVEL = "logging_level"
        const val KEY_PREF_BACKUP_INTERVAL = "backup_interval"
        const val KEY_PREF_DEVICE_ALIAS = "device_name"
        const val KEY_SDCARD_ACCESS_URI = "internal_uri_extsdcard"
        const val KEY_SDCARD_ACCESS_FLAGS = "internal_flags_extsdcard"
        const val KEY_PREF_GOGS_API = "gogs_api"
        const val KEY_PREF_CHECK_HARDWARE = "check_hardware_requirements"

        const val KEY_PREF_APP_UPDATES = "app_updates"
        const val KEY_PREF_APP_VERSION = "app_version"
        const val KEY_PREF_LICENSE_AGREEMENT = "license_agreement"
        const val KEY_PREF_STATEMENT_OF_FAITH = "statement_of_faith"
        const val KEY_PREF_TRANSLATION_GUIDELINES = "translation_guidelines"
        const val KEY_PREF_SOFTWARE_LICENSES = "software_licenses"
    }
}
