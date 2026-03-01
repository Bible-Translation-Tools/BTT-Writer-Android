package com.door43.util

import android.util.Base64
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

/**
 *
 * @author Anuj
 * Blog www.goldenpackagebyanuj.blogspot.com
 * RSA - Encrypt Data using Public Key
 * RSA - Descypt Data using Private Key
 */
object RSAEncryption {

    /**
     * Generates a set of private and public keys
     * @param privateKeyFile the private key file
     * @param publicKeyFile the public key file
     * @throws Exception
     */
    @Throws(Exception::class)
    fun generateKeys(privateKeyFile: File, publicKeyFile: File) {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKey = keyPair.public
        val privateKey = keyPair.private

        // pull out parameters which makes up Key
        val keyFactory = KeyFactory.getInstance("RSA")
        val rsaPubKeySpec = keyFactory.getKeySpec(
            publicKey,
            RSAPublicKeySpec::class.java
        )
        val rsaPrivKeySpec = keyFactory.getKeySpec(
            privateKey,
            RSAPrivateKeySpec::class.java
        )

        // save keys
        saveKeys(
            publicKeyFile.absolutePath,
            rsaPubKeySpec.modulus,
            rsaPubKeySpec.publicExponent
        )
        saveKeys(
            privateKeyFile.absolutePath,
            rsaPrivKeySpec.modulus,
            rsaPrivKeySpec.privateExponent
        )
    }

    /**
     * Save Files
     * @param fileName
     * @param mod
     * @param exp
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun saveKeys(fileName: String, mod: BigInteger, exp: BigInteger) {
        try {
            FileOutputStream(fileName).use { fos ->
                ObjectOutputStream(BufferedOutputStream(fos)).use { oos ->
                    oos.writeObject(mod)
                    oos.writeObject(exp)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Encrypt Data
     * @param data
     * @param pubKey
     * @throws Exception
     */
    @Throws(Exception::class)
    fun encryptData(data: String, pubKey: PublicKey): ByteArray? {
        val dataToEncrypt = data.toByteArray()
        return try {
            val cipher = Cipher.getInstance("RSA")
            cipher.init(Cipher.ENCRYPT_MODE, pubKey)
            cipher.doFinal(dataToEncrypt)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypt Data
     * @param data
     * @param privateKey
     * @throws Exception
     */
    @Throws(Exception::class)
    fun decryptData(data: ByteArray, privateKey: PrivateKey): String? {
        return try {
            val cipher = Cipher.getInstance("RSA")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            String(cipher.doFinal(data))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts the public key to a string
     * @param key
     * @return
     */
    @Throws(Exception::class)
    fun getPublicKeyAsString(key: PublicKey): String {
        // pull out parameters which makes up Key
        val keyFactory = KeyFactory.getInstance("RSA")
        val rsaPubKeySpec = keyFactory.getKeySpec(key, RSAPublicKeySpec::class.java)

        // save as string
        val modulus = String(Base64.encode(rsaPubKeySpec.modulus.toByteArray(), Base64.NO_WRAP))
        val exponent = String(Base64.encode(rsaPubKeySpec.publicExponent.toByteArray(), Base64.NO_WRAP))

        return "$modulus<split>$exponent"
    }

    /**
     * Creates a public key from a string
     * @param keyString
     * @return
     */
    fun getPublicKeyFromString(keyString: String): PublicKey? {
        val pieces = keyString.split("<split>").toTypedArray()
        if (pieces.size == 2) {
            val modulus = BigInteger(Base64.decode(pieces[0].toByteArray(), Base64.NO_WRAP))
            val exponent = BigInteger(Base64.decode(pieces[1].toByteArray(), Base64.NO_WRAP))

            val rsaPublicKeySpec = RSAPublicKeySpec(modulus, exponent)
            return try {
                val fact = KeyFactory.getInstance("RSA")
                fact.generatePublic(rsaPublicKeySpec)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return null
    }

    /**
     * read Public Key From File
     * @param file
     * @return PublicKey
     * @throws Exception
     */
    @Throws(Exception::class)
    fun readPublicKeyFromFile(file: File): PublicKey? {
        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    val modulus = ois.readObject() as BigInteger
                    val exponent = ois.readObject() as BigInteger

                    //Get Public Key
                    val rsaPublicKeySpec = RSAPublicKeySpec(modulus, exponent)
                    val fact = KeyFactory.getInstance("RSA")
                    fact.generatePublic(rsaPublicKeySpec)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * read Private Key From File
     * @param file
     * @return PrivateKey
     * @throws Exception
     */
    @Throws(Exception::class)
    fun readPrivateKeyFromFile(file: File): PrivateKey? {
        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    val modulus = ois.readObject() as BigInteger
                    val exponent = ois.readObject() as BigInteger

                    //Get Private Key
                    val rsaPrivateKeySpec = RSAPrivateKeySpec(modulus, exponent)
                    val fact = KeyFactory.getInstance("RSA")
                    fact.generatePrivate(rsaPrivateKeySpec)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}