package com.door43.translationstudio.tasks;

import com.door43.translationstudio.App;
import com.door43.util.FileUtilities;

import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadIndexTask extends ManagedTask {
    public static final String TASK_ID = "download-index-task";
    public static final String TAG = DownloadIndexTask.class.getName();

    private final File index = new File(App.databaseDir(), "index.sqlite");
    private final String url = "https://btt-writer-resources.s3.amazonaws.com/index.sqlite";

    private int maxProgress = 0;
    private boolean success = false;
    private String prefix;

    @Override
    public void start() {
        success = false;

        publishProgress(-1, "");

        success = this.downloadIndex();
    }

    private boolean downloadIndex() {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;

        try {
            // Closing database for pending transactions to be committed
            Door43Client library = App.getLibrary();
            if (library != null) {
                library.tearDown();
            }

            URL downloadUrl = new URL(this.url);

            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }

            int fileLength = connection.getContentLength();
            this.maxProgress = fileLength;

            input = connection.getInputStream();
            output = FileUtilities.openOutputStream(index);

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                if (DownloadIndexTask.this.isCanceled()) {
                    input.close();
                    return false;
                }
                total += count;

                if (fileLength > 0) {
                    publishProgress(total / (float)fileLength, "");
                }

                output.write(data, 0, count);
            }

            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
            } catch (IOException ignored){}

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public int maxProgress() {
        return maxProgress;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix + "  ";
    }
}
