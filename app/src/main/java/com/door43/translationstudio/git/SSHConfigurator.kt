package com.door43.translationstudio.git

import com.door43.data.IDirectoryProvider
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import java.io.File
import java.io.StringWriter
import java.net.InetSocketAddress
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.util.Base64

internal class SSHConfigurator(private val directoryProvider: IDirectoryProvider) {
    /**
     * Creates key database that accepts server keys automatically
     */
    val trustAllDatabase = object : ServerKeyDatabase {
        override fun lookup(
            p0: String?,
            p1: InetSocketAddress?,
            p2: ServerKeyDatabase.Configuration?
        ): List<PublicKey?> {
            return mutableListOf()
        }

        override fun accept(
            p0: String?,
            p1: InetSocketAddress?,
            p2: PublicKey?,
            p3: ServerKeyDatabase.Configuration?,
            p4: CredentialsProvider?
        ): Boolean {
            return true
        }
    }

    private var _configFile: File? = null
    /**
     * Get known hosts config file
     */
    val configFile: File
        get() = _configFile ?: run {
            val file = File(directoryProvider.sshKeysDir, "config")
            file.writeText("""
            Host *
                StrictHostKeyChecking no
                UserKnownHostsFile /dev/null
                IdentityFile ${directoryProvider.privateKey.absolutePath}
                PreferredAuthentications publickey
            """.trimIndent())
            _configFile = file
            file
        }

    /**
     * MINA sshd static initializer reads user.home; Android doesn't set it
     * Set to ssh's parent dir
     */
    fun setHomeDir() {
        System.setProperty("user.home", directoryProvider.internalAppDir.absolutePath)
    }

    /**
     * Remove Android's default BC provider to prevent conflicts
     * Insert the full BouncyCastle provider at position 1 (highest priority)
     */
    fun setSecurityProvider() {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    companion object {

        /**
         * Generates private and public keys
         *
         * @param udid Device's identifier
         * @return pair of private and public keys
         */
        fun generateKeys(udid: String): Pair<String, String> {
            val random = SecureRandom()
            val privateKeyParams = Ed25519PrivateKeyParameters(random)
            val publicKeyParams = privateKeyParams.generatePublicKey()
            val privateBytes = OpenSSHPrivateKeyUtil.encodePrivateKey(privateKeyParams)

            val pemObject = PemObject("OPENSSH PRIVATE KEY", privateBytes)
            val stringWriter = StringWriter()
            val pemWriter = PemWriter(stringWriter)
            pemWriter.writeObject(pemObject)
            pemWriter.close()

            val pubBytes = OpenSSHPublicKeyUtil.encodePublicKey(publicKeyParams)
            val pubBase64 = Base64.getEncoder().encodeToString(pubBytes)
            val publicSshString = "ssh-ed25519 $pubBase64 $udid"

            return stringWriter.toString() to publicSshString
        }
    }
}