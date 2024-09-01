package com.door43.translationstudio.git

import com.door43.data.IDirectoryProvider
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport

/**
 * Created by joel on 9/15/2014.
 */
class TransportCallback(
    directoryProvider: IDirectoryProvider,
    port: Int
) : TransportConfigCallback {
    private val ssh = GitSessionFactory(directoryProvider, port)

    override fun configure(tn: Transport) {
        if (tn is SshTransport) {
            tn.sshSessionFactory = ssh
        }
    }
}
