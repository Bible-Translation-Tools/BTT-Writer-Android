package com.door43.util;

import org.unfoldingword.tools.logger.Logger;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * This class provides some utility methods for handling files
 */
public class FileUtilities {

    /**
     * Converts an input stream into a string
     * @param is
     * @return
     * @throws Exception
     */
    public static String readStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns the contents of a file as a string
     * @param file
     * @return
     * @throws Exception
     */
    public static String readFileToString(File file) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            String contents = readStreamToString(fis);
            fis.close();
            return contents;
        } finally {
            if(fis != null) {
                fis.close();
            }
        }
    }

    /**
     * Writes a string to a file
     * @param file
     * @param contents
     * @throws IOException
     */
    public static void writeStringToFile(File file, String contents) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file.getAbsolutePath());
            fos.write(contents.getBytes());
        } finally {
            if(fos != null) {
                fos.close();
            }
        }
    }

    /**
     * Recursively deletes a direcotry or just deletes the file
     * @param fileOrDirectory
     */
    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    /**
     * Attempts to move a file or directory. If moving fails it will try to copy instead.
     * @param sourceFile
     * @param destFile
     * @return
     */
    public static boolean moveOrCopy(File sourceFile, File destFile) {
        if(sourceFile.exists()) {
            // first try to move
            if (!sourceFile.renameTo(destFile)) {
                // try to copy
                try {
                    if (sourceFile.isDirectory()) {
                        FileUtils.copyDirectory(sourceFile, destFile);
                    } else {
                        FileUtils.copyFile(sourceFile, destFile);
                    }
                    return true;
                } catch (IOException e) {
                    Logger.e(FileUtils.class.getName(), "Failed to copy the file", e);
                }
            }
        }
        return false;
    }

    /**
     * Deletes a file/directory by first moving it to a temporary location then deleting it.
     * This avoids an issue with FAT32 on some devices where you cannot create a file
     * with the same name right after deleting it
     * @param file
     */
    public static void safeDelete(File file) {
        if(file != null && file.exists()) {
            File temp = new File(file.getParentFile(), System.currentTimeMillis() + ".trash");
            file.renameTo(temp);
            try {
                if (file.isDirectory()) {
                    FileUtils.moveDirectoryToDirectory(file, temp, true);
                } else {
                    FileUtils.moveFile(file, temp);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            FileUtils.deleteQuietly(file); // just in case the move failed
            FileUtils.deleteQuietly(temp);
        }
    }
}
