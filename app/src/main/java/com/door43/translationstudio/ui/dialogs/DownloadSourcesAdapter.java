package com.door43.translationstudio.ui.dialogs;

import android.content.Context;
import android.graphics.Typeface;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.viewbinding.ViewBinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.core.Util;
import com.door43.translationstudio.databinding.FragmentSelectDownloadSourceItemBinding;
import com.door43.translationstudio.databinding.FragmentSelectFilterItemBinding;
import com.door43.usecases.GetAvailableSources;
import com.door43.widget.ViewUtil;

import org.json.JSONObject;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.Language;
import org.unfoldingword.tools.logger.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Created by blm on 12/1/16.
 */

public class DownloadSourcesAdapter extends BaseAdapter {

    public static final String TAG = DownloadSourcesAdapter.class.getSimpleName();
    public static final int TYPE_ITEM_FILTER_SELECTION = 0;
    public static final int TYPE_ITEM_SOURCE_SELECTION = 1;
    private Context context;
    private List<String> selected = new ArrayList<>();
    private List<String> downloaded = new ArrayList<>();
    private List<ViewItem> items = new ArrayList<>();
    private List<Translation> availableSources;
    private Map<String,List<Integer>> byLanguage;
    private Map<String,List<Integer>> otBooks;
    private Map<String,List<Integer>> ntBooks;
    private Map<String,List<Integer>> otherBooks;

    // 02/20/2017 - for now we are disabling updating of TA since a major change coming up could break the app
    private static final int[] bookTypeNameList = {
            R.string.old_testament_label,
            R.string.new_testament_label,
            R.string.other_label
    }; // removed R.string.ta_label to disable updating TA
    private static final int[] bookTypeIconList = {
            R.drawable.ic_library_books_black_24dp,
            R.drawable.ic_library_books_black_24dp,
            R.drawable.ic_local_library_black_24dp
    };

    private SelectionType selectionType = SelectionType.language;
    private List<DownloadSourcesAdapter.FilterStep> steps;
    private String languageFilter;
    private String bookFilter;
    private String search = null;
    private final Map<String, String> downloadErrors = new HashMap<>();

    private final Typography typography;

    public DownloadSourcesAdapter(Typography typography) {
        this.typography = typography;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    /**
     * Loads source lists from task results
a     * @param task
     */
    public void setData(GetAvailableSources.Result result) {
        availableSources = result.getSources();
        Logger.i(TAG, "Found " + availableSources.size() + " sources");

        byLanguage = result.getByLanguage();
        otBooks = result.getOtBooks();
        ntBooks = result.getNtBooks();
        otherBooks = result.getOtherBooks();
        selected = new ArrayList<>(); // clear selections
        initializeSelections();
    }

    /**
     * loads the filter stages (e.g. filter by language, and then by category)
     * @param steps
     * @param search - string to search for
     * @param restore - if true then don't reset selection list
     */
    public void setFilterSteps(List<DownloadSourcesAdapter.FilterStep> steps, String search, boolean restore) {
        this.steps = steps;
        this.search = search;
        if(!restore) {
            selected = new ArrayList<>(); // clear selections
        }
        initializeSelections();
    }

    /**
     * loads the filter stages (e.g. filter by language, and then by category)
     * @param search - string to search for
     */
    public void setSearch(String search) {
        this.search = search;
        initializeSelections();
    }

    @Override
    public ViewItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return switch (selectionType) {
            case source_filtered_by_language, source_filtered_by_book -> TYPE_ITEM_SOURCE_SELECTION;
            default -> TYPE_ITEM_FILTER_SELECTION;
        };
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    public List<String> getSelected() {
        return selected;
    }

    public List<String> getDownloaded() {
        return downloaded;
    }

    public void setSelected(List<String> mSelected) {
        this.selected = mSelected;
    }

    public void setDownloaded(List<String> mDownloaded) {
        this.downloaded = mDownloaded;
    }

    public JSONObject getDownloadErrorMessages() {
        return new JSONObject(downloadErrors);
    }

    public void setDownloadErrorMessages(String jsonDownloadErrorMessagesStr) {
        downloadErrors.clear();
        try {
            JSONObject jsonMessages = new JSONObject(jsonDownloadErrorMessagesStr);
            Iterator<?> keySet = jsonMessages.keys();
            while (keySet.hasNext()) {
                String key = (String) keySet.next();
                Object value = jsonMessages.get(key);
                downloadErrors.put(key, value.toString());
            }
        } catch (Exception e) {
            Logger.w("DownloadSourcesAdapter", "Error parsing download error messages", e);
        }
    }

    public SelectedState getSelectedState() {
        boolean allSelected = true;
        boolean noneSelected = true;
        for (ViewItem item : items) {
            if(!item.downloaded) { // ignore items already downloaded
                if (item.selected) {
                    noneSelected = false;
                } else {
                    allSelected = false;
                }
            }
        }

        if(noneSelected) {
            return SelectedState.none;
        } else if(allSelected) {
            return SelectedState.all;
        }
        return SelectedState.not_empty;
    }

    public List<ViewItem> getItems() {
        return items;
    }

    /**
     * Resorts the data
     */
    public void initializeSelections() {

        bookFilter = null; // clear filters
        languageFilter = null;

        if((steps == null) // make sure we have data to sort
            || (steps.isEmpty())
            || (availableSources == null)
            || (availableSources.isEmpty()) ){
            return;
        }

        selectionType = steps.get(steps.size()-1).selection;

        for (int i = 0; i < steps.size() - 1; i++) { // iterate through previous steps to extract filters
            FilterStep step = steps.get(i);
            switch (step.selection) {
                case language:
                    languageFilter = step.filter;
                    break;
                case oldTestament:
                case newTestament:
                case other_book:
                case book_type:
                    bookFilter = step.filter;
                    break;
            }
        }

        switch (selectionType) {
            case source_filtered_by_language:
                getSourcesForLanguageAndCategory();
                break;

            case source_filtered_by_book:
                getSourcesForBook();
                break;

            case oldTestament:
                getBooksInCategory(otBooks, false);
                break;

            case newTestament:
                getBooksInCategory(ntBooks, false);
                break;

            case other_book:
                getBooksInCategory(otherBooks, true);
                break;

            case book_type:
                getCategories();
                break;

            case language:
            default:
                getLanguages();
                break;
        }
        notifyDataSetChanged();
    }

    /**
     * create list of languages available in sources
     */
    private void getLanguages() {
        items = new ArrayList<>();
        for (String key : byLanguage.keySet()) {
            List<Integer> items = byLanguage.get(key);
            if((items != null)  && (!items.isEmpty())) {
                int index = items.get(0);
                if((index >= 0) && (index < availableSources.size())) {
                    Translation sourceTranslation = availableSources.get(index);
                    String title = sourceTranslation.language.name + "  (" + sourceTranslation.language.slug + ")";
                    ViewItem newItem = new ViewItem(title, sourceTranslation.language.slug, sourceTranslation, false, false);
                    this.items.add(newItem);
                }
            }
        }
        if((search != null) && !search.isEmpty()) {
            List<ViewItem> filteredItems = new ArrayList<>();

            // filter by language code
            for (ViewItem item : items) {
                String code = item.sourceTranslation.language.slug;
                if(code.length() >= search.length()) {
                    if (code.substring(0, search.length()).equalsIgnoreCase(search)) {
                        filteredItems.add(item);
                    }
                }
            }

            // filter by language name
            for (ViewItem item : items) {
                String name = item.sourceTranslation.language.name;
                if(name.length() >= search.length()) {
                    if (name.substring(0, search.length()).equalsIgnoreCase(search)) {
                        if (!filteredItems.contains(item)) { // prevent duplicates
                            filteredItems.add(item);
                        }
                    }
                }
            }

            items = filteredItems;
        }
    }

    /**
     * get list of categories (OT, NT, other).  If language has been selected, only
     *      return categories that contain the language.
     */
    private void getCategories() {
        items = new ArrayList<>();
        for(int i = 0; i < bookTypeNameList.length; i++) {
            int id = bookTypeNameList[i];
            if(languageFilter != null) {
                boolean found;
                if (id == R.string.old_testament_label) {
                    found = isLanguageInCategory(byLanguage, otBooks);
                } else if (id == R.string.new_testament_label) {
                    found = isLanguageInCategory(byLanguage, ntBooks);
                } else {
                    found = isLanguageInCategory(byLanguage, otherBooks);
                }
                if(!found) { // if category is not found, skip
                    continue;
                }
            }
            String title = context.getResources().getString(id);
            ViewItem newItem = new ViewItem(title, Integer.toString(id), null, false, false);
            newItem.icon = bookTypeIconList[i];
            items.add(newItem);
        }
    }

    /**
     * check if category (OT, NT, other) contains the selected language
     * @param sortSet
     * @param category
     * @return
     */
    private boolean isLanguageInCategory(Map<String, List<Integer>> sortSet, Map<String, List<Integer>> category) {
        boolean found = false;
        if(sortSet.containsKey(languageFilter)) {
            List<Integer> items = sortSet.get(languageFilter);
            for (Integer index : items) {
                if ((index >= 0) && (index < availableSources.size())) {
                    Translation sourceTranslation = availableSources.get(index);
                    if (category.containsKey(sourceTranslation.project.slug)) {
                        found = true;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * create list of source selections that match book
     */
    private void getSourcesForBook() {
        List<Integer> sourceList = null;
        items = new ArrayList<>();

        // first get book list for selected book type
        if(ntBooks.containsKey(bookFilter)) {
            sourceList = ntBooks.get(bookFilter);
        }
        if(sourceList == null) {
            if(otBooks.containsKey(bookFilter)) {
                sourceList = otBooks.get(bookFilter);
            }
        }
        if(sourceList == null) {
            if(otherBooks.containsKey(bookFilter)) {
                sourceList = otherBooks.get(bookFilter);
            }
        }

        if(sourceList == null) {
            return;
        }

        for (Integer index : sourceList) {
            if ((index >= 0) && (index < availableSources.size())) {
                Translation source = availableSources.get(index);
                String filter = source.resourceContainerSlug;
                String language = source.language.name + "  (" + source.language.slug + ")";
                String project = source.resource.name + "  (" + source.resource.slug + ")";
                addNewViewItem(language, project, filter, source);
            }
        }

        // sort by language code
        Collections.sort(items, new Comparator<ViewItem>() { // do numeric sort
            @Override
            public int compare(ViewItem lhs, ViewItem rhs) {
                return lhs.filter.compareTo(rhs.filter);
            }
        });
    }

    /**
     * create new view item, apply previous state info, and add to list
     * @param title1
     * @param title2
     * @param filter
     * @param source
     */
    private void addNewViewItem(String title1, String title2, String filter, Translation source) {
        ViewItem newItem = new ViewItem(title1, title2, filter, source, false, false);

        if(selected.contains(newItem.containerSlug)) {
            newItem.selected = true;
        }
        if(downloaded.contains(newItem.containerSlug)) {
            newItem.downloaded = true;
        }
        if(downloadErrors.containsKey(newItem.containerSlug)) {
            newItem.error = true;
            newItem.errorMessage = downloadErrors.get(newItem.containerSlug);
        }
        items.add(newItem);
    }

    /**
     * gets the selection type based on filter
     * @param categoryFilter
     * @return
     */
    public SelectionType getCategoryForFilter(String categoryFilter) {
        int bookTypeSelected = Util.strToInt(categoryFilter, R.string.other_label);
        SelectionType type;
        if (bookTypeSelected == R.string.old_testament_label) {
            type = SelectionType.oldTestament;
        } else if (bookTypeSelected == R.string.new_testament_label) {
            type = SelectionType.newTestament;
        } else {
            type = SelectionType.other_book;
        }
        return type;
    }

    /**
     * create list of source selections that match language and category
     */
    private void getSourcesForLanguageAndCategory() {
        Map<String, List<Integer>> sortSet;
        items = new ArrayList<>();

        //get book list for category
        SelectionType category = getCategoryForFilter(bookFilter);
        sortSet = switch (category) {
            case oldTestament -> otBooks;
            case newTestament -> ntBooks;
            default -> otherBooks;
        };

        for (String key : byLanguage.keySet()) {
            if(languageFilter != null) {
                if(!key.equals(languageFilter)) { // skip over language if not matching filter
                    continue;
                }
            }
            List<Integer> items = byLanguage.get(key);
            if((items != null)  && (!items.isEmpty())) {
                for (Integer index : items) {
                    if ((index >= 0) && (index < availableSources.size())) {
                        Translation source = availableSources.get(index);

                        if(sortSet != null) {
                            if(!sortSet.containsKey(source.project.slug)) { // if not in right category then skip
                                continue;
                            }
                        }

                        String filter = source.resourceContainerSlug;
                        String project = source.project.name + "  (" + source.project.slug + ")";
                        String resource = source.resource.name + "  (" + source.resource.slug + ")";
                        addNewViewItem(project, resource, filter, source);
                    }
                }
            }
            if(languageFilter != null) { // if filtering by specific language, then done
                break;
            }
        }

        if(sortSet != null) {
            List<ViewItem> unOrdered = items;
            items = new ArrayList<>();

            for (String book : sortSet.keySet() ) {
                for (int i = 0; i < unOrdered.size(); i++) {
                    ViewItem viewItem = unOrdered.get(i);
                    Translation sourceTranslation = viewItem.sourceTranslation;
                    if(book.equals(sourceTranslation.project.slug)) {
                        items.add(viewItem);
                        unOrdered.remove(viewItem);
                        i--;
                    }
                }
            }
        }
    }

    /**
     * create ordered list based on category, optionally sort
     * @param bookType
     * @param sort
     */
    private void getBooksInCategory(Map<String, List<Integer>> bookType, boolean sort) {
        items = new ArrayList<>();
        for (String key : bookType.keySet()) {
            List<Integer> items = bookType.get(key);
            if((items != null)  && (!items.isEmpty())) {
                int index;
                Translation sourceTranslation = null;
                String title = null;
                String filter = null;
                for (int i = 0; i < items.size(); i++) {
                    index = items.get(i);
                    sourceTranslation = availableSources.get(index);
                    filter = sourceTranslation.project.slug;
                    title = sourceTranslation.project.name + "  (" + filter + ")";
                    if(sourceTranslation.language.slug.equals("en")) {
                        break;
                    }
                }

                ViewItem newItem = new ViewItem(title, filter, sourceTranslation, false, false);
                this.items.add(newItem);
            }
        }

        if(sort) {
            // do numeric sort
            // do numeric sort
            Collections.sort(items, (lhs, rhs) -> lhs.title.toString().compareTo(rhs.title.toString()));
        }
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        context = parent.getContext();
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        int rowType = getItemViewType(position);
        final ViewItem item = getItem(position);

        BaseViewHolder viewHolder;

        if (convertView == null) {
            if (rowType == TYPE_ITEM_FILTER_SELECTION) {
                FragmentSelectFilterItemBinding binding = FragmentSelectFilterItemBinding.inflate(
                        inflater,
                        parent,
                        false
                );
                viewHolder = new FilterViewHolder(binding);
            } else {
                FragmentSelectDownloadSourceItemBinding binding = FragmentSelectDownloadSourceItemBinding.inflate(
                        inflater,
                        parent,
                        false
                );
                viewHolder = new DownloadSourceViewHolder(binding);
            }
            viewHolder.binding.getRoot().setTag(viewHolder);
        } else {
            viewHolder = (BaseViewHolder) convertView.getTag();
        }

        viewHolder.bind(item);

        return viewHolder.binding.getRoot();
    }

    /**
     * toggle selection state for item
     * @param position
     */
    public void toggleSelection(int position) {
        if(getItem(position).selected) {
            deselect(position);
        } else {
            select(position);
        }
        notifyDataSetChanged();
    }

    public void select(int position) {
        ViewItem item = getItem(position);
        if(item != null) {
            if(!item.downloaded) {
                item.selected = true;
                if (!selected.contains(item.containerSlug)) { // make sure we don't add entry twice (particularly during select all)
                    selected.add(item.containerSlug);
                }
            }
        }
    }

    public void deselect(int position) {
        ViewItem item = getItem(position);
        if(item != null) {
            item.selected = false;
            selected.remove(item.containerSlug);
        }
    }

    /**
     * search items for position that matches slug
     * @param slug
     * @return
     */
    public int findPosition(String slug) {
        for (int i = 0; i < items.size(); i++) {
            ViewItem item = items.get(i);
            if(item.containerSlug.equals(slug)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * marks an item as downloaded
     * @param position
     */
    public void markItemDownloaded(int position) {
        ViewItem item = getItem(position);
        if(item != null) {
            item.downloaded = true;
            item.error = false;
            deselect(position);

            if(!downloaded.contains(item.containerSlug)) {
                downloaded.add(item.containerSlug);
            }
            downloadErrors.remove(item.containerSlug);
        }
    }

    /**
     * marks an item as error
     * @param position
     */
    public void markItemError(int position, String message) {
        ViewItem item = getItem(position);
        if(item != null) {
            item.error = true;
            item.errorMessage = message;

            downloadErrors.put(item.containerSlug, message);
            downloaded.remove(item.containerSlug);
        }
    }

    /**
     * used to force selection of all or none of the items
     * @param selectAll
     * @param selectNone
     */
    public void forceSelection(boolean selectAll, boolean selectNone) {
        if(selectAll) {
            for (int i = 0; i < items.size(); i++) {
                select(i);
            }
        }
        if(selectNone) {
            for (int i = 0; i < items.size(); i++) {
                deselect(i);
            }
        }
        notifyDataSetChanged();
    }

    public static class ViewItem {
        public final CharSequence title;
        public final CharSequence title2;
        public final String containerSlug;
        public final Translation sourceTranslation;
        public boolean selected;
        public boolean downloaded;
        public boolean error;
        public int icon;
        public String filter;
        public String errorMessage;

        // two text field version
        public ViewItem(CharSequence title, CharSequence title2, String filter, Translation sourceTranslation, boolean selected, boolean downloaded) {
            this.title = title;
            this.title2 = title2;
            this.selected = selected;
            this.sourceTranslation = sourceTranslation;
            if (sourceTranslation != null) {
                this.containerSlug = sourceTranslation.resourceContainerSlug;
            } else {
                this.containerSlug = null;
            }
            this.downloaded = downloaded;
            this.filter = filter;
            error = false;
            icon = 0;
        }

        // single text field version
        public ViewItem(CharSequence title, String filter, Translation sourceTranslation, boolean selected, boolean downloaded) {
            this(title, null, filter, sourceTranslation, selected, downloaded);
        }
    }

    public static class FilterStep {
        public final SelectionType selection;
        public String label;
        public String old_label;
        public String filter;
        public Language language;

        public FilterStep(SelectionType selection, String label) {
            this.selection = selection;
            this.label = label;
            filter = null;
            old_label = null;
            language = null;
        }

        private FilterStep(SelectionType selection, String label, String filter, String old_label) {
            this.selection = selection;
            this.label = label;
            this.filter = filter;
            this.old_label = old_label;
        }

        public JSONObject toJson() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.putOpt("selection", selection.getValue());
                jsonObject.putOpt("label", label);
                jsonObject.putOpt("old_label", old_label);
                jsonObject.putOpt("filter", filter);

                return jsonObject;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        static FilterStep generate(JSONObject jsonObject) {
            try {
                SelectionType selection = SelectionType.fromInt((int) getOpt(jsonObject,"selection"));
                String label = (String) getOpt(jsonObject,"label");
                String old_label = (String) getOpt(jsonObject,"old_label");
                String filter = (String) getOpt(jsonObject,"filter");
                return new FilterStep( selection, label, filter, old_label);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        static Object getOpt(JSONObject json, String key) {
            try {
                if(json.has(key)) {
                    return json.get(key);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * enum that keeps track of current state of USFM import
     */
    public enum SelectionType {
        language(0),
        oldTestament(1),
        newTestament(2),
        other_book(3),
        book_type(4),
        source_filtered_by_language(5),
        source_filtered_by_book(6);

        private int _value;

        SelectionType(int Value) {
            this._value = Value;
        }

        public int getValue() {
            return _value;
        }

        public static SelectionType fromInt(int i) {
            for (SelectionType b : SelectionType.values()) {
                if (b.getValue() == i) {
                    return b;
                }
            }
            return null;
        }
    }

    public enum SelectedState {
        all,
        none,
        not_empty;
    }

    private abstract static class BaseViewHolder {
        ViewBinding binding;

        public BaseViewHolder(ViewBinding binding) {
            this.binding = binding;
        }

        public abstract void bind(ViewItem item);
    }

    private class FilterViewHolder extends BaseViewHolder {
        private final FragmentSelectFilterItemBinding binding;

        public FilterViewHolder(FragmentSelectFilterItemBinding binding) {
            super(binding);
            this.binding = binding;
        }

        @Override
        public void bind(ViewItem item) {
            // make sure this is reset to default
            binding.title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            binding.title.setText(item.title);

            if(selectionType == SelectionType.book_type) {
                binding.itemIcon.setImageDrawable(AppCompatResources.getDrawable(context, item.icon));
                binding.itemIcon.setVisibility(View.VISIBLE);
            } else {
                binding.itemIcon.setVisibility(View.GONE);
                // if language selection, look up font
                Typeface typeface = typography.getBestFontForLanguage(
                        TranslationType.SOURCE,
                        item.sourceTranslation.language.slug,
                        item.sourceTranslation.language.direction
                );
                binding.title.setTypeface(typeface, Typeface.NORMAL);
            }
        }
    }

    private static class DownloadSourceViewHolder extends BaseViewHolder {
        private final FragmentSelectDownloadSourceItemBinding binding;

        public DownloadSourceViewHolder(FragmentSelectDownloadSourceItemBinding binding) {
            super(binding);
            this.binding = binding;
        }

        @Override
        public void bind(ViewItem item) {
            Context context = binding.getRoot().getContext();

            // make sure this is reset to default
            binding.title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            binding.title.setText(item.title);
            binding.title2.setText((item.title2 != null) ? item.title2 : "");
            binding.errorIcon.setVisibility(item.error ? View.VISIBLE : View.GONE);

            if(item.error) {
                binding.errorIcon.setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                            .setTitle(R.string.download_failed)
                            .setMessage(R.string.check_network_connection)
                            .setPositiveButton(R.string.label_close, null);
                    if(item.errorMessage != null) {
                        builder.setMessage(item.errorMessage);
                    }
                    builder.show();
                });
            }
            if(item.downloaded) { // display with a green check
                binding.itemIcon.setBackgroundResource(R.drawable.ic_done_black_24dp);
                ViewUtil.tintViewDrawable(binding.itemIcon, context.getResources().getColor(R.color.completed));
            } else if (item.selected) { // display checked box
                binding.itemIcon.setBackgroundResource(R.drawable.ic_check_box_black_24dp);
                ViewUtil.tintViewDrawable(binding.itemIcon, context.getResources().getColor(R.color.accent));
            } else { // display unchecked box
                binding.itemIcon.setBackgroundResource(R.drawable.ic_check_box_outline_blank_black_24dp);
                ViewUtil.tintViewDrawable(binding.itemIcon, context.getResources().getColor(R.color.dark_primary_text));
            }
        }
    }
}
