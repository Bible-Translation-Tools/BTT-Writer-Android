package com.door43.translationstudio.git

import com.door43.data.IDirectoryProvider
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import java.io.File

object SshSessionFactory {

    fun create(directoryProvider: IDirectoryProvider): SshdSessionFactory {
        val configurator = SSHConfigurator(directoryProvider)

        configurator.setHomeDir()
        configurator.setSecurityProvider()

        return SshdSessionFactoryBuilder()
            .setServerKeyDatabase { _, _ -> configurator.trustAllDatabase }
            .setConfigFile { _ -> configurator.configFile }
            .setHomeDirectory(directoryProvider.internalAppDir)
            .setSshDirectory(File(directoryProvider.internalAppDir, "ssh"))
            .build(null)
    }
}