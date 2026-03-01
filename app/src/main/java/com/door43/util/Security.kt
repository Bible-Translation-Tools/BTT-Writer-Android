package com.door43.util

import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Created by joel on 1/9/2015.
 */
object Security {

    /**
     * Generates a md5 hash of a string
     * @param s
     * @return
     */
    fun md5(s: String): String {
        val encrypter = try {
            MessageDigest.getInstance("MD5")
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            // Returning an empty string to satisfy the non-null requirement
            // and avoid the NPE that would have happened in the original Java code.
            return ""
        }

        encrypter.update(s.toByteArray(), 0, s.length)
        var md5 = BigInteger(1, encrypter.digest()).toString(16)
        while (md5.length < 32) {
            md5 = "0$md5"
        }
        return md5
    }

    /**
     * generate sha1 hash for string
     * @param source
     * @return
     */
    fun sha1(source: String): String? {
        return try {
            val messageDigest = MessageDigest.getInstance("SHA-1")
            messageDigest.update(source.toByteArray(Charsets.UTF_8))
            val bytes = messageDigest.digest()
            val buffer = StringBuilder()
            for (b in bytes) {
                buffer.append(((b.toInt() and 0xff) + 0x100)
                    .toString(16)
                    .substring(1))
            }
            buffer.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}