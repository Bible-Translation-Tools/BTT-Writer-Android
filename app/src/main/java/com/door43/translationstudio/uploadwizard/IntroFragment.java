package com.door43.translationstudio.uploadwizard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.door43.translationstudio.R;
import com.door43.translationstudio.projects.Project;

public class IntroFragment extends WizardFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.fragment_upload_intro, container, false);

        Button cancelBtn = (Button)rootView.findViewById(R.id.upload_wizard_cancel_btn);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onCancel();
            }
        });
        Button continueBtn = (Button)rootView.findViewById(R.id.upload_wizard_continue_btn);
        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onContinue();
            }
        });

        Project p = app().getSharedProjectManager().getSelectedProject();

        TextView detailsText = (TextView)rootView.findViewById(R.id.project_details_text);
        detailsText.setText(String.format(getResources().getString(R.string.project_details), p.getTitle(), p.getSelectedSourceLanguage().getName(), p.getSelectedTargetLanguage().getName()));

        return rootView;
    }


}