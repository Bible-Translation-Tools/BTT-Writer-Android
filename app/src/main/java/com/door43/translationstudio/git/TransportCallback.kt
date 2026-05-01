package com.door43.translationstudio.git

import com.door43.data.IDirectoryProvider
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import javax.inject.Inject

class TransportCallback @Inject constructor(
    directoryProvider: IDirectoryProvider
) : TransportConfigCallback {
    private val ssh = SshSessionFactory.create(directoryProvider)

    override fun configure(tn: Transport) {
        if (tn is SshTransport) {
            tn.sshSessionFactory = ssh
        }
    }
}
