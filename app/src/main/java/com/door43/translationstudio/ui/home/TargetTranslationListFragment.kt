package com.door43.translationstudio.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.door43.data.IPreferenceRepository;
import com.door43.translationstudio.R;
import com.door43.translationstudio.databinding.FragmentTargetTranslationListBinding;
import com.door43.translationstudio.ui.BaseFragment;
import com.door43.translationstudio.ui.viewmodels.HomeViewModel;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Displays a list of target translations
 */
@AndroidEntryPoint
public class TargetTranslationListFragment extends BaseFragment {

    @Inject
    IPreferenceRepository prefRepository;

    public static final String TAG = TargetTranslationListFragment.class.getSimpleName();
    public static final String STATE_SORT_BY_COLUMN = "state_sort_by_column";
    public static final String STATE_SORT_PROJECT_COLUMN = "state_sort_project_column";
    public static final String SORT_PROJECT_ITEM = "sort_project_item";
    public static final String SORT_BY_COLUMN_ITEM = "sort_by_column_item";

    private TargetTranslationAdapter adapter;
    private OnItemClickListener listener;
    private TargetTranslationAdapter.SortProjectColumnType sortProjectColumn = TargetTranslationAdapter.SortProjectColumnType.bibleOrder;
    private TargetTranslationAdapter.SortByColumnType sortByColumn = TargetTranslationAdapter.SortByColumnType.projectThenLanguage;

    private HomeViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentTargetTranslationListBinding binding =
                FragmentTargetTranslationListBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        setupObservers();

        adapter = new TargetTranslationAdapter();
        adapter.setOnInfoClickListener(item -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            Fragment prev = getParentFragmentManager().findFragmentByTag("infoDialog");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            TargetTranslationInfoDialog dialog = new TargetTranslationInfoDialog();
            Bundle args = new Bundle();
            args.putString(TargetTranslationInfoDialog.ARG_TARGET_TRANSLATION_ID, item.getTranslation().getId());
            dialog.setArguments(args);
            dialog.show(ft, "infoDialog");
        });
        binding.translationsList.setAdapter(adapter);

        // open target translation
        binding.translationsList.setOnItemClickListener(
                (parent, view, position, id) -> listener.onItemClick(adapter.getItem(position))
        );

        if(savedInstanceState != null) {
            sortByColumn = TargetTranslationAdapter.SortByColumnType.fromInt(savedInstanceState.getInt(STATE_SORT_BY_COLUMN, sortByColumn.getValue()));
            sortProjectColumn = TargetTranslationAdapter.SortProjectColumnType.fromInt(savedInstanceState.getInt(STATE_SORT_PROJECT_COLUMN, sortProjectColumn.getValue()));
        } else { // if not restoring states, get last values
            sortByColumn = TargetTranslationAdapter.SortByColumnType.fromString(
                    prefRepository.getDefaultPref(SORT_BY_COLUMN_ITEM, null),
                    TargetTranslationAdapter.SortByColumnType.projectThenLanguage
            );
            sortProjectColumn = TargetTranslationAdapter.SortProjectColumnType.fromString(
                    prefRepository.getDefaultPref(SORT_PROJECT_ITEM, null),
                    TargetTranslationAdapter.SortProjectColumnType.bibleOrder
            );
        }
        adapter.sort(sortByColumn, sortProjectColumn);

        List<String> projectTypes = new ArrayList<>();
        projectTypes.add(this.getResources().getString(R.string.sort_project_then_language));
        projectTypes.add(this.getResources().getString(R.string.sort_language_then_project));
        projectTypes.add(this.getResources().getString(R.string.sort_progress_then_project));
        ArrayAdapter<String> projectTypesAdapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_item, projectTypes);
        projectTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.sortColumn.setAdapter(projectTypesAdapter);
        binding.sortColumn.setSelection(sortByColumn.getValue());
        binding.sortColumn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                    Logger.i(TAG, "Sort column item selected: " + position);
                sortByColumn = TargetTranslationAdapter.SortByColumnType.fromInt(position);
                prefRepository.getDefaultPref(SORT_BY_COLUMN_ITEM, String.valueOf(sortByColumn.getValue()));
                if(adapter != null) {
                    adapter.sort(sortByColumn, sortProjectColumn);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        List<String> bibleTypes = new ArrayList<>();
        bibleTypes.add(this.getResources().getString(R.string.sort_bible_order));
        bibleTypes.add(this.getResources().getString(R.string.sort_alphabetical_order));
        ArrayAdapter<String> bibleTypesAdapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_item, bibleTypes);
        bibleTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.sortProjects.setAdapter(bibleTypesAdapter);
        binding.sortProjects.setSelection(sortProjectColumn.getValue());
        binding.sortProjects.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                    Logger.i(TAG, "Sort project column item selected: " + position);
                sortProjectColumn = TargetTranslationAdapter.SortProjectColumnType.fromInt(position);
                prefRepository.setDefaultPref(SORT_PROJECT_ITEM, String.valueOf(sortProjectColumn.getValue()));
                if(adapter != null) {
                    adapter.sort(sortByColumn, sortProjectColumn);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        return binding.getRoot();
    }

    private void setupObservers() {
        viewModel.getTranslations().observe(getViewLifecycleOwner(), translations -> {
            if (translations != null && adapter != null) {
                adapter.changeData(translations);
            }
        });
    }

    public void reloadList() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            this.listener = (OnItemClickListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnItemClickListener");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putInt(STATE_SORT_BY_COLUMN, sortByColumn.getValue());
        out.putInt(STATE_SORT_PROJECT_COLUMN, sortProjectColumn.getValue());
        super.onSaveInstanceState(out);
    }

    public interface OnItemClickListener {
        void onItemClick(TranslationItem item);
    }
}
