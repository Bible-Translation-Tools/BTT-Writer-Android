package com.door43.translationstudio.git

import com.door43.data.IDirectoryProvider
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.util.FS

/**
 * Created by joel on 9/15/2014.
 */
class GitSessionFactory(
    private val directoryProvider: IDirectoryProvider,
    private val port: Int
) : JschConfigSessionFactory() {
    override fun configure(host: OpenSshConfig.Host, session: Session) {
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "publickey,password")
        session.port = port
    }

    override fun createDefaultJSch(fs: FS): JSch {
        val jsch = JSch()
        val privateKey = directoryProvider.privateKey
        val publicKey = directoryProvider.publicKey
        if (privateKey.exists() && publicKey.exists()) {
            jsch.addIdentity(privateKey.absolutePath)
            publicKey.inputStream().use { input ->
                jsch.setKnownHosts(input)
            }
        }
        return jsch
    }
}
