package com.door43.translationstudio.ui.translate;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.viewbinding.ViewBinding;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListDownloadItemBinding;
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListHeaderBinding;
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListItemBinding;
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListUpdatableItemBinding;
import com.door43.widget.ViewUtil;

import org.unfoldingword.door43client.models.Translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Handles the list of source translations that can be chosen for viewing along side
 * a target translation.
 */
public class ChooseSourceTranslationAdapter extends BaseAdapter {
    public static final String TAG = ChooseSourceTranslationAdapter.class.getSimpleName();
    public static final int TYPE_ITEM_SELECTABLE = 0;
    public static final int TYPE_SEPARATOR = 1;
    public static final int TYPE_ITEM_NEED_DOWNLOAD = 2;
    public static final int TYPE_ITEM_SELECTABLE_UPDATABLE = 3;
    public static final int MAX_SOURCE_ITEMS = 3;

    private final Context context;
    private final Typography typography;

    private final Map<String, RCItem> data = new HashMap<>();
    private final List<String> selected = new ArrayList<>();
    private final List<String> available = new ArrayList<>();
    private final List<String> downloadable = new ArrayList<>();
    private List<RCItem> sortedData = new ArrayList<>();
    private TreeSet<Integer> sectionHeader = new TreeSet<>();
    private String searchText;

    private OnItemClickListener itemClickListener = null;

    public interface OnItemClickListener {
        void onCheckForItemUpdates(String containerSlug);
        void onTriggerDownload(RCItem item, Callbacks.OnDownloadCancel callback);
        void onTriggerDeleteContainer(
                String containerSlug,
                Callbacks.OnDeleteContainer callback
        );
    }

    public interface Callbacks {
        interface OnDownloadCancel {
            void onCancel(String containerSlug);
        }
        interface OnDeleteContainer {
            void onDelete(String containerSlug);
        }
    }

    public ChooseSourceTranslationAdapter(Context context, Typography typography) {
        this.context = context;
        this.typography = typography;
    }

    public void setItems(List<RCItem> items) {
        data.clear();
        available.clear();
        selected.clear();
        downloadable.clear();
        sortedData.clear();

        for (RCItem item : items) {
            addItem(item);
        }
        sort();
    }

    @Override
    public int getCount() {
        return sortedData.size();
    }

    /**
     * Adds an item to the list
     * If the item id matches an existing item it will be skipped
     * @param item
     */
    private void addItem(final RCItem item) {
        if(!data.containsKey(item.containerSlug)) {
            data.put(item.containerSlug, item);
            if(item.selected && item.downloaded) {
                selected.add(item.containerSlug);
            } else if(!item.downloaded) {
                downloadable.add(item.containerSlug);
            } else {
                available.add(item.containerSlug);
            }
        }
    }

    @Override
    public RCItem getItem(int position) {
        if(position >= 0 && position < sortedData.size()) {
            return sortedData.get(position);
        } else {
            return null;
        }
    }

    /**
     * Finds an item by its container slug.
     * Positions are unstable because the list is resorted on every change,
     * so the slug is the only safe way to refer to an item from an asynchronous callback.
     * @param containerSlug
     * @return the item or null if it is not in the list
     */
    public RCItem getItemBySlug(String containerSlug) {
        if(containerSlug == null) return null;
        return data.get(containerSlug);
    }

    /**
     * The slugs of all selected items.
     * Selections are kept even when the search filter hides them from the list.
     * @return
     */
    public List<String> getSelectedSlugs() {
        return new ArrayList<>(selected);
    }

    /**
     * toggle selection state for item
     * @param position
     */
    public void toggleSelection(int position) {
        toggleSelection(getSelectableItem(position));
    }

    /**
     * toggle selection state for item
     * @param containerSlug
     */
    public void toggleSelection(String containerSlug) {
        toggleSelection(getItemBySlug(containerSlug));
    }

    private void toggleSelection(RCItem item) {
        if(item == null) return;
        if(item.selected) {
            deselect(item);
        } else {
            select(item);
        }
        sort();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public boolean isSelectableItem(int position) {
        return getItemViewType(position) != TYPE_SEPARATOR;
    }

    /**
     * Returns the item at the given position only if it is a real, selectable item.
     * Section headers and out of range positions resolve to null.
     * @param position
     * @return
     */
    private RCItem getSelectableItem(int position) {
        RCItem item = getItem(position);
        if(item == null || item.containerSlug == null) return null;
        return item;
    }

    @Override
    public int getItemViewType(int position) {
        RCItem v = getItem(position);
        // headers, unknown positions and items without a container are not selectable
        if(sectionHeader.contains(position) || v == null || v.containerSlug == null) {
            return TYPE_SEPARATOR;
        }
        if(!v.downloaded) { // check if we need to download
            return TYPE_ITEM_NEED_DOWNLOAD;
        }
        if(v.hasUpdates) {
            return TYPE_ITEM_SELECTABLE_UPDATABLE;
        }
        return TYPE_ITEM_SELECTABLE;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    /**
     * applies search string and resorts list
     */
    public void applySearch(String search) {
        searchText = search;
        sort();
    }

    /**
     * Resorts the data
     */
    private void sort() {
        sortedData = new ArrayList<>();
        sectionHeader = new TreeSet<>();

        // build list
        RCItem selectedHeader = new RCItem(getSelectedText(), null, false, false);
        sortedData.add(selectedHeader);
        sectionHeader.add(sortedData.size() - 1);

        List<RCItem> section = getViewItems(selected, searchText);
        sortedData.addAll(section);

        RCItem availableHeader = new RCItem(context.getResources().getString(R.string.available), null, false, false);
        sortedData.add(availableHeader);
        sectionHeader.add(sortedData.size() - 1);

        section = getViewItems(available, searchText);
        sortedData.addAll(section);

        RCItem downloadableHeader = new RCItem(getDownloadableText(), null, false, false);
        sortedData.add(downloadableHeader);
        sectionHeader.add(sortedData.size() - 1);

        section = getViewItems(downloadable, searchText);
        sortedData.addAll(section);

        notifyDataSetChanged();
    }

    /**
     * get ViewItems from data list and apply any search filters
     * @param data
     * @return
     */
    private List<RCItem> getViewItems(List<String> data, String searchText) {
        List<RCItem> section = new ArrayList<>();
        for(String id:data) {
            RCItem item = this.data.get(id);
            if(item != null) { // never place null items in the list
                section.add(item);
            }
        }

        // sort by language code
        // do numeric sort
        Collections.sort(section, (lhs, rhs) -> {
            try {
                return lhs.sourceTranslation.language.slug.compareTo(rhs.sourceTranslation.language.slug);
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        });

        if((searchText != null) && (!searchText.isEmpty())) {
            List<RCItem> filtered = new ArrayList<>();

            // filter by language code
            for (RCItem item : section) {
                String code = item.sourceTranslation.language.slug;
                if(code.length() >= searchText.length()) {
                    if (code.substring(0, searchText.length()).equalsIgnoreCase(searchText)) {
                        filtered.add(item);
                    }
                }
            }

            // filter by language name
            for (RCItem item : section) {
                String name = item.sourceTranslation.language.name;
                if(name.length() >= searchText.length()) {
                    if (name.substring(0, searchText.length()).equalsIgnoreCase(searchText)) {
                        if (!filtered.contains(item)) { // prevent duplicates
                            filtered.add(item);
                        }
                    }
                }
            }

            // filter by resource name
            for (RCItem item : section) {
                String[] parts = item.sourceTranslation.resource.name.split("-");
                for (String part : parts) { // handle sections separately
                    String name = part.trim();
                    if(name.length() >= searchText.length()) {
                        if (name.substring(0, searchText.length()).equalsIgnoreCase(searchText)) {
                            if (!filtered.contains(item)) { // prevent duplicates
                                filtered.add(item);
                            }
                        }
                    }
                }
            }
            section = filtered;
        }
        return section;
    }

    /**
     * create text for selected separator
     * @return
     */
    private CharSequence getSelectedText() {
        CharSequence text = context.getResources().getString(R.string.selected);
        CharSequence limit = context.getResources().getString(R.string.maximum_limit, MAX_SOURCE_ITEMS);
        SpannableStringBuilder refresh = createImageSpannable(R.drawable.ic_refresh_secondary_24dp);
        CharSequence warning = context.getResources().getString(R.string.requires_internet);
        SpannableStringBuilder wifi = createImageSpannable(R.drawable.ic_wifi_secondary_18dp);
        return TextUtils.concat(text, " ", limit, "    ", refresh, " ", warning, " ", wifi); // combine all on one line
    }

    /**
     * create text for selected separator
     * @return
     */
    private CharSequence getDownloadableText() {
        CharSequence text = context.getResources().getString(R.string.available_online);
        CharSequence warning = context.getResources().getString(R.string.requires_internet);
        SpannableStringBuilder wifi = createImageSpannable(R.drawable.ic_wifi_secondary_18dp);
        return TextUtils.concat(text, "    ", warning, " ", wifi); // combine all on one line
    }

    /**
     * create an image spannable
     * @param resource
     * @return
     */
    private SpannableStringBuilder createImageSpannable(int resource) {
        SpannableStringBuilder refresh = new SpannableStringBuilder(" ");
        Drawable refreshDrawable = ResourcesCompat.getDrawable(context.getResources(), resource, null);
        if (refreshDrawable != null) {
            refreshDrawable.setBounds(0, 0, refreshDrawable.getMinimumWidth(), refreshDrawable.getMinimumHeight());
            refresh.setSpan(new ImageSpan(refreshDrawable), 0, refresh.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return refresh;
    }


    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewBinding binding;
        ViewHolder holder;
        int rowType = getItemViewType(position);
        final RCItem item = getItem(position);
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if(convertView == null) {
            switch (rowType) {
                case TYPE_SEPARATOR:
                    binding = FragmentSelectSourceTranslationListHeaderBinding.inflate(
                            inflater,
                            parent,
                            false
                    );
                    FragmentSelectSourceTranslationListHeaderBinding separatorBinding =
                            ((FragmentSelectSourceTranslationListHeaderBinding) binding);
                    holder = new ViewHolder();
                    holder.titleView = separatorBinding.title;
                    separatorBinding.title.setTransformationMethod(null);
                    break;
                case TYPE_ITEM_SELECTABLE:
                    binding = FragmentSelectSourceTranslationListItemBinding.inflate(
                            inflater,
                            parent,
                            false
                    );
                    FragmentSelectSourceTranslationListItemBinding selectableBinding =
                            ((FragmentSelectSourceTranslationListItemBinding) binding);
                    holder = new ViewHolder();
                    holder.titleView = selectableBinding.title;
                    holder.checkboxView = selectableBinding.checkBoxView;
                    break;
                case TYPE_ITEM_SELECTABLE_UPDATABLE:
                    binding = FragmentSelectSourceTranslationListUpdatableItemBinding.inflate(
                            inflater,
                            parent,
                            false
                    );
                    FragmentSelectSourceTranslationListUpdatableItemBinding updatableItemBinding =
                            ((FragmentSelectSourceTranslationListUpdatableItemBinding) binding);
                    holder = new ViewHolder();
                    holder.titleView = updatableItemBinding.title;
                    holder.checkboxView = updatableItemBinding.checkBoxView;
                    holder.downloadView = updatableItemBinding.downloadResource;
                    break;
                case TYPE_ITEM_NEED_DOWNLOAD:
                    binding = FragmentSelectSourceTranslationListDownloadItemBinding.inflate(
                            inflater,
                            parent,
                            false
                    );
                    FragmentSelectSourceTranslationListDownloadItemBinding downloadBinding =
                            ((FragmentSelectSourceTranslationListDownloadItemBinding) binding);
                    holder = new ViewHolder();
                    holder.titleView = downloadBinding.title;
                    holder.downloadView = downloadBinding.downloadResource;
                    break;
                default:
                    throw new IllegalArgumentException("Incorrect view type");
            }
            view = binding.getRoot();
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        // load update status
        holder.currentPosition = position;

        if(item == null) { // list changed under us, render an empty row instead of crashing
            holder.titleView.setText("");
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
            return view;
        }

        holder.titleView.setText(item.title);
        if(item.sourceTranslation != null) {
            setFontForLanguage(holder, item);
        }
        if( (rowType == TYPE_ITEM_NEED_DOWNLOAD) || (rowType == TYPE_ITEM_SELECTABLE_UPDATABLE)) {
            if(holder.downloadView != null) {
                if (rowType == TYPE_ITEM_NEED_DOWNLOAD) {
                    holder.downloadView.setBackgroundResource(R.drawable.ic_file_download_black_24dp);
                } else {
                    holder.downloadView.setBackgroundResource(R.drawable.ic_refresh_black_24dp);
                }
                ViewUtil.tintViewDrawable(holder.downloadView, parent.getContext().getResources().getColor(R.color.accent));
            }
        }

        if((rowType == TYPE_ITEM_SELECTABLE) || (rowType == TYPE_ITEM_SELECTABLE_UPDATABLE)){
            if (item.selected) {
                holder.checkboxView.setBackgroundResource(R.drawable.ic_check_box_black_24dp);
                ViewUtil.tintViewDrawable(holder.checkboxView, parent.getContext().getResources().getColor(R.color.accent));
                // display checked
            } else {
                holder.checkboxView.setBackgroundResource(R.drawable.ic_check_box_outline_blank_black_24dp);
                ViewUtil.tintViewDrawable(holder.checkboxView, parent.getContext().getResources().getColor(R.color.dark_primary_text));
                // display unchecked
            }
        }

        view.setOnClickListener(v -> {
            if (itemClickListener != null && item.containerSlug != null) {
                if (item.hasUpdates || !item.downloaded) {
                    itemClickListener.onTriggerDownload(item, this::toggleSelection);
                } else {
                    toggleSelection(item.containerSlug);
                    if (!item.checkedUpdates && item.downloaded) {
                        itemClickListener.onCheckForItemUpdates(item.containerSlug);
                    }
                }
            }
        });

        view.setOnLongClickListener(v -> {
            if (itemClickListener != null && item.downloaded && item.containerSlug != null) {
                itemClickListener.onTriggerDeleteContainer(
                        item.containerSlug,
                        this::markItemDeleted
                );
                return true;
            }
            return false;
        });

        return view;
    }

    /**
     * will substitute some fonts for specific languages that may not be supported on all devices.
     *      Uses lookup by language code.
     *
     * @param holder
     * @param item
     */
    private void setFontForLanguage(ViewHolder holder, RCItem item) {
        String code = item.sourceTranslation.language.slug;
        typography.format(TranslationType.SOURCE, holder.titleView, code, item.sourceTranslation.language.direction);

        Typeface typeface = typography.getBestFontForLanguage(TranslationType.SOURCE, code, item.sourceTranslation.language.direction);
        if(typeface != Typeface.DEFAULT) {
            holder.titleView.setTypeface(typeface, Typeface.NORMAL);
        }
    }

    private void select(RCItem item) {
        if (item == null || item.containerSlug == null) return;
        if (selected.size() >= MAX_SOURCE_ITEMS) {
            return;
        }

        item.selected = true;
        selected.remove(item.containerSlug);
        available.remove(item.containerSlug);
        downloadable.remove(item.containerSlug);
        selected.add(item.containerSlug);
    }

    private void deselect(RCItem item) {
        if (item == null || item.containerSlug == null) return;

        item.selected = false;
        selected.remove(item.containerSlug);
        available.remove(item.containerSlug);
        downloadable.remove(item.containerSlug);
        available.add(item.containerSlug);
    }

    /**
     * Marks an item as deleted
     * @param position
     */
    public void markItemDeleted(int position) {
        markItemDeleted(getSelectableItem(position));
    }

    /**
     * Marks an item as deleted
     * @param containerSlug
     */
    public void markItemDeleted(String containerSlug) {
        markItemDeleted(getItemBySlug(containerSlug));
    }

    private void markItemDeleted(RCItem item) {
        if(item != null && item.containerSlug != null) {
            item.hasUpdates = false;
            item.downloaded = false;
            item.selected = false;
            selected.remove(item.containerSlug);
            available.remove(item.containerSlug);
            if(!downloadable.contains(item.containerSlug)) downloadable.add(item.containerSlug);
        }
        sort();
    }

    /**
     * marks an item as downloaded
     * @param position
     */
    public void markItemDownloaded(int position) {
        markItemDownloaded(getSelectableItem(position));
    }

    /**
     * marks an item as downloaded
     * @param containerSlug
     */
    public void markItemDownloaded(String containerSlug) {
        markItemDownloaded(getItemBySlug(containerSlug));
    }

    private void markItemDownloaded(RCItem item) {
        if(item != null && item.containerSlug != null) {
            item.hasUpdates = false;
            item.downloaded = true;
            select(item); // auto select download item
        }
        sort();
    }

    public void setItemClickListener(OnItemClickListener listener) {
        itemClickListener = listener;
    }

    public void setItemHasUpdates(int position, boolean hasUpdates) {
        setItemHasUpdates(getSelectableItem(position), hasUpdates);
    }

    public void setItemHasUpdates(String containerSlug, boolean hasUpdates) {
        setItemHasUpdates(getItemBySlug(containerSlug), hasUpdates);
    }

    private void setItemHasUpdates(RCItem item, boolean hasUpdates) {
        if(item == null) return;

        item.hasUpdates = hasUpdates;
        item.checkedUpdates = true;
        notifyDataSetChanged();
        Log.i(
                TAG,
                "Checking for updates on " + item.containerSlug + " finished, needs updates: " + hasUpdates
        );
    }

    public static class ViewHolder {
        public TextView titleView;
        public ImageView checkboxView;
        public ImageView downloadView;
        public int currentPosition;
    }

    public static class RCItem {
        public final CharSequence title;
        public final String containerSlug;
        public final Translation sourceTranslation;
        public boolean selected;
        public boolean downloaded;
        public boolean hasUpdates;
        public boolean checkedUpdates = false;

        public RCItem(CharSequence title, Translation sourceTranslation, boolean selected, boolean downloaded) {
            this.title = title;
            this.selected = selected;
            this.sourceTranslation = sourceTranslation;
            if(sourceTranslation != null) {
                this.containerSlug = sourceTranslation.resourceContainerSlug;
            } else {
                this.containerSlug = null;
            }
            this.downloaded = downloaded;
        }
    }
}
