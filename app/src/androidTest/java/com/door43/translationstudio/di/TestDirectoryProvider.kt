package com.door43.translationstudio.di

import android.content.Context
import com.door43.translationstudio.DirectoryProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class TestDirectoryProvider @Inject constructor(
    @ApplicationContext private val context: Context
): DirectoryProvider(context) {

    override val cacheDir: File
        get() = run {
            val dir = File(context.cacheDir, "test_cache")
            dir.mkdirs()
            dir
        }

    override val translationsDir: File
        get() = File(externalAppDir, "test_translations")

    override val translationsCacheDir: File
        get() = File(translationsDir, "test_cache")

    override val databaseFile: File
        get() = File(databaseDir, "test_index.sqlite")

    override val backupsDir: File
        get() = File(externalAppDir, "test_backups")

    override val containersDir: File
        get() = File(externalAppDir, "test_resource_containers")

    override val logFile: File
        get() = File(externalAppDir, "test_log.txt")

    override val publicKey: File
        get() = File(sshKeysDir, "test_id_rsa.pub")

    override val privateKey: File
        get() = File(sshKeysDir, "test_id_rsa")

    override val p2pPublicKey: File
        get() = File(p2pKeysDir, "test_id_rsa.pub")

    override val p2pPrivateKey: File
        get() = File(p2pKeysDir, "test_id_rsa")
}