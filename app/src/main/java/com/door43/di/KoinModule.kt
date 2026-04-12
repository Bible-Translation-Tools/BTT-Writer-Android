package com.door43.di

import android.content.Context
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.ILanguageRequestRepository
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.repositories.LanguageRequestRepository
import com.door43.repositories.PreferenceRepository
import com.door43.translationstudio.DirectoryProvider
import com.door43.translationstudio.MainAssetsProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.AndroidBackupController
import com.door43.translationstudio.core.AndroidResourceProvider
import com.door43.translationstudio.core.ArchiveImporter
import com.door43.translationstudio.core.BackupController
import com.door43.translationstudio.core.DownloadImages
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ResourceProvider
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.crash.CrashReporterViewModel
import com.door43.translationstudio.ui.devtools.DeveloperViewModel
import com.door43.translationstudio.ui.dialogs.ExportViewModel
import com.door43.translationstudio.ui.dialogs.FeedbackViewModel
import com.door43.translationstudio.ui.home.ImportViewModel
import com.door43.translationstudio.ui.home.UsfmImportViewModel
import com.door43.translationstudio.ui.draft.DraftViewModel
import com.door43.translationstudio.ui.home.HomeViewModel
import com.door43.translationstudio.ui.home.UpdateLibraryViewModel
import com.door43.translationstudio.ui.legal.TermsOfUseViewModel
import com.door43.translationstudio.ui.profile.LoginViewModel
import com.door43.translationstudio.ui.publish.PublishViewModel
import com.door43.translationstudio.ui.settings.SettingsViewModel
import com.door43.translationstudio.ui.splash.SplashScreenViewModel
import com.door43.translationstudio.ui.translate.TargetTranslationViewModel
import com.door43.translationstudio.ui.translate.chunk.ChunkModeViewModel
import com.door43.translationstudio.ui.translate.dialogs.SourceSelectionViewModel
import com.door43.translationstudio.ui.translate.read.ReadModeViewModel
import com.door43.translationstudio.ui.translate.review.ReviewModeViewModel
import com.door43.translationstudio.ui.home.DownloadSourcesViewModel
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationModel
import com.door43.translationstudio.ui.newlanguage.NewTempLanguageViewModel
import com.door43.usecases.AdvancedGogsRepoSearch
import com.door43.usecases.BackupRC
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.CloneRepository
import com.door43.usecases.CreateRepository
import com.door43.usecases.DownloadIndex
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.DownloadResourceContainers
import com.door43.usecases.ExportProjects
import com.door43.usecases.GetAvailableSources
import com.door43.usecases.GetRepository
import com.door43.usecases.GogsLogin
import com.door43.usecases.GogsLogout
import com.door43.usecases.ImportDraft
import com.door43.usecases.ImportProjects
import com.door43.usecases.MergeTargetTranslation
import com.door43.usecases.MigrateTranslations
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import com.door43.usecases.RenderHelps
import com.door43.usecases.SearchGogsRepositories
import com.door43.usecases.SearchGogsUsers
import com.door43.usecases.SubmitNewLanguageRequests
import com.door43.usecases.TranslationProgress
import com.door43.usecases.UpdateAll
import com.door43.usecases.UpdateApp
import com.door43.usecases.UpdateCatalogs
import com.door43.usecases.UpdateSource
import com.door43.usecases.UploadCrashReport
import com.door43.usecases.UploadFeedback
import com.door43.usecases.ValidateProject
import org.json.JSONObject
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.unfoldingword.door43client.Door43Client

val appModule = module {
    singleOf(::DirectoryProvider).bind<IDirectoryProvider>()

    singleOf(::BackupRC)
    singleOf(::Translator)
    singleOf(::Profile)
    singleOf(::ArchiveImporter)
    singleOf(::TargetTranslationMigrator)

    single<Profile> {
        val pref: IPreferenceRepository = get()
        val dir: IDirectoryProvider = get()
        try {
            val profileString = pref.getDefaultPref<String>("profile")
            Profile.fromJSON(pref, dir, profileString?.let { JSONObject(it) })
        } catch (e: Exception) {
            throw e
        }
    }

    singleOf(::PreferenceRepository).bind<IPreferenceRepository>()
    singleOf(::LanguageRequestRepository).bind<ILanguageRequestRepository>()

    singleOf(::UpdateSource)
    singleOf(::PushTargetTranslation)
    singleOf(::GetRepository)
    singleOf(::CreateRepository)
    singleOf(::SearchGogsRepositories)
    singleOf(::SearchGogsUsers)
    singleOf(::SubmitNewLanguageRequests)
    singleOf(::AdvancedGogsRepoSearch)
    singleOf(::GogsLogin)
    singleOf(::PullTargetTranslation)
    singleOf(::UploadFeedback)
    singleOf(::ImportProjects)
    singleOf(::RegisterSSHKeys)
    singleOf(::ExportProjects)
    singleOf(::ImportDraft)
    singleOf(::UpdateCatalogs)
    singleOf(::TranslationProgress)
    singleOf(::CheckForLatestRelease)
    singleOf(::DownloadLatestRelease)
    singleOf(::UploadCrashReport)
    singleOf(::CloneRepository)
    singleOf(::UpdateAll)
    singleOf(::DownloadIndex)
    singleOf(::MergeTargetTranslation)
    singleOf(::DownloadResourceContainers)
    singleOf(::MigrateTranslations)
    singleOf(::GogsLogout)
    singleOf(::RenderHelps)
    singleOf(::ValidateProject)
    singleOf(::GetAvailableSources)
    singleOf(::RenderingProvider)
    single {
        // TODO Remove android dependency
        val context: Context = get()
        val defaultFontName = context.getString(R.string.pref_default_translation_typeface)
        val defaultFontSize = context.getString(R.string.pref_default_typeface_size)
        Typography(get(), defaultFontName, defaultFontSize)
    }
    singleOf(::DownloadImages)
    singleOf(::UpdateApp)

    viewModelOf(::ImportViewModel)
    viewModelOf(::UsfmImportViewModel)
    viewModelOf(::TargetTranslationViewModel)
    viewModelOf(::CrashReporterViewModel)
    viewModelOf(::SplashScreenViewModel)
    viewModelOf(::DeveloperViewModel)
    viewModelOf(::FeedbackViewModel)
    viewModelOf(::DraftViewModel)
    viewModelOf(::ExportViewModel)
    viewModelOf(::DownloadSourcesViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::NewTargetTranslationModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::NewTempLanguageViewModel)
    viewModelOf(::TermsOfUseViewModel)
    viewModelOf(::ReadModeViewModel)
    viewModelOf(::ChunkModeViewModel)
    viewModelOf(::ReviewModeViewModel)
    viewModelOf(::SourceSelectionViewModel)
    viewModelOf(::PublishViewModel)
    viewModelOf(::UpdateLibraryViewModel)
}

val prodDataModule = module {
    singleOf(::MainAssetsProvider).bind<AssetsProvider>()
    singleOf(::AndroidResourceProvider).bind<ResourceProvider>()
    singleOf(::AndroidBackupController).bind<BackupController>()
    singleOf(::Door43Client)
}
