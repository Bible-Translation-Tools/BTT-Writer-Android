package com.door43.translationstudio.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.door43.translationstudio.App;

import org.unfoldingword.tools.foreground.Foreground;
import org.unfoldingword.tools.logger.Logger;

import java.io.File;

/**
 * This should be extended by all activities in the app so that we can perform verification on
 * activities such as recovery from crashes.
 *
 */
public abstract class BaseActivity extends AppCompatActivity implements Foreground.Listener {

    private Foreground foreground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            this.foreground = Foreground.get();
            this.foreground.addListener(this);
        } catch (IllegalStateException e) {
            Logger.i(this.getClass().getName(), "Foreground was not initialized");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!isBootActivity()) {
            File[] crashFiles = Logger.listStacktraces();;
            if (crashFiles.length > 0) {
                // restart
                Intent intent = new Intent(this, SplashScreenActivity.class);
                startActivity(intent);
                finish();
            }
        }
    }

    private boolean isBootActivity() {
        return this instanceof TermsOfUseActivity
                || this instanceof SplashScreenActivity
                || this instanceof CrashReporterActivity;
    }

    @Override
    public void onDestroy() {
        if(this.foreground != null) {
            this.foreground.removeListener(this);
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBecameForeground() {
        // check if the index had been loaded
        if(!isBootActivity() && !App.isLibraryDeployed()) {
            Logger.w(this.getClass().getName(), "The library was not deployed.");
            // restart
            Intent intent = new Intent(this, SplashScreenActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onBecameBackground() {
    }
}
