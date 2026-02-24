package com.door43.translationstudio.ui;

import androidx.fragment.app.Fragment;

/**
 * This should be extended by all activities in the app so that we can perform verification on
 * activities such as recovery from crashes.
 *
 */
public abstract class BaseFragment extends Fragment {

    @Override
    public void onResume() {
        super.onResume();
    }
}
