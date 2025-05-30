package com.door43.translationstudio.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.unfoldingword.resourcecontainer.ResourceContainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Date;

/**
 * Created by joel on 9/2/2015.
 */
public class Util {
    public static String readStream(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    public static void writeStream(InputStream is, File output) throws Exception{
        output.getParentFile().mkdirs();
        try {
            FileOutputStream outputStream = new FileOutputStream(output);
            try {
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) > 0) {
                    outputStream.write(buf, 0, len);
                }
            } finally {
                outputStream.close();
            }
        } finally {
            is.close();
        }
    }

    /**
     * Converts a json array to a string array
     * @param json
     * @return
     */
    public static String[] jsonArrayToString(JSONArray json) throws JSONException {
        String[] values = new String[json.length()];
        for(int i = 0; i < json.length(); i ++) {
            values[i] = json.getString(i);
        }
        return values;
    }

    /**
     * Returns the date_modified from a url
     * @param url
     * @return returns 0 if the date could not be parsed
     */
    public static int getDateFromUrl(String url) {
        String[] pieces = url.split("\\?");
        if(pieces.length > 1) {
            // date_modified=123456
            String attribute = pieces[1];
            pieces = attribute.split("=");
            if(pieces.length > 1) {
                try {
                    return Integer.parseInt(pieces[1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Returns a unix timestamp
     * @return
     */
    public static long unixTime() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Converts a unix time value to a date object
     * @param unix
     * @return
     */
    public static Date dateFromUnixTime(long unix) {
        return new Date(unix * 1000L);
    }

    /**
     * do string to integer with default value on conversion error
     * @param value
     * @param defaultValue
     * @return
     */
    public static int strToInt(String value, int defaultValue) {
        try {
            int retValue = Integer.parseInt(value);
            return retValue;
        } catch (Exception e) {
//            Log.d(TAG, "Cannot convert to int: " + value);
        }
        return defaultValue;
    }

    /**
     * Converts a verse id to a chunk id.
     * If an error occurs the verse will be returned
     * @param verse
     * @param sortedChunks
     * @return
     */
    public static String verseToChunk(String verse, String[] sortedChunks) {
        String match = verse;
        for(String chunk:sortedChunks) {
            try {
                if(Integer.parseInt(chunk) > Integer.parseInt(verse)) {
                    break;
                }
                match = chunk;
            } catch (Exception e) {
                // TRICKY: some chunks are not numbers
                if(chunk.equals(verse)) {
                    match = chunk;
                    break;
                }
            }
        }
        return match;
    }

    /**
     * Maps a verse to a chunk.
     *
     * @param rc
     * @param chapter
     * @param verse
     * @return
     */
    public static String mapVerseToChunk(ResourceContainer rc, String chapter, String verse) {
        try {
            String[] chunks = rc.chunks(chapter);
            if (chunks != null) {
                Arrays.sort(chunks, (o1, o2) -> {
                    Integer i1;
                    Integer i2;
                    // TRICKY: push strings to top
                    try {
                        i1 = Integer.valueOf(o1);
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                    try {
                        i2 = Integer.valueOf(o2);
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                    return i1.compareTo(i2);
                });
                return Util.verseToChunk(verse, chunks);
            } else {
                return null;
            }
        } catch (Exception e) {
            return verse;
        }
    }
}
