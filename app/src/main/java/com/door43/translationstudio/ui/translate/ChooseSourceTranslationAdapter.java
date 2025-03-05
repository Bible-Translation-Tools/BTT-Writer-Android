package com.door43.translationstudio.ui.translate;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
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
        void onCheckForItemUpdates(String containerSlug, int position);
        void onTriggerDownload(RCItem item, int position, Callbacks.OnDownloadCancel callback);
        void onTriggerDeleteContainer(
                String containerSlug,
                int position,
                Callbacks.OnDeleteContainer callback
        );
    }

    public interface Callbacks {
        interface OnDownloadCancel {
            void onCancel(int position);
        }
        interface OnDeleteContainer {
            void onDelete(int position);
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
     * toggle selection state for item
     * @param position
     */
    public void toggleSelection(int position) {
        if(getItem(position).selected) {
            deselect(position);
        } else {
            select(position);
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

    @Override
    public int getItemViewType(int position) {
        int type = sectionHeader.contains(position) ? TYPE_SEPARATOR : TYPE_ITEM_SELECTABLE;
        if(type == TYPE_ITEM_SELECTABLE) {
            RCItem v = getItem(position);
            if(!v.downloaded) { // check if we need to download
                type = TYPE_ITEM_NEED_DOWNLOAD;
            } else if(v.hasUpdates) {
                type = TYPE_ITEM_SELECTABLE_UPDATABLE;
            }
        }
        return type;
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

        List<RCItem> section = getViewItems(selected, null); // do not restrict selections by search string
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
            section.add(this.data.get(id));
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
            if (itemClickListener != null && isSelectableItem(position)) {
                if (item.hasUpdates || !item.downloaded) {
                    itemClickListener.onTriggerDownload(item, position, this::toggleSelection);
                } else {
                    toggleSelection(position);
                    if (!item.checkedUpdates && item.downloaded) {
                        itemClickListener.onCheckForItemUpdates(item.containerSlug, position);
                    }
                }
            }
        });

        view.setOnLongClickListener(v -> {
            if (itemClickListener != null && item.downloaded && isSelectableItem(position)) {
                itemClickListener.onTriggerDeleteContainer(
                        item.containerSlug,
                        position,
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

    private void select(int position) {
        if (selected.size() >= MAX_SOURCE_ITEMS) {
            return;
        }

        RCItem item = getItem(position);
        item.selected = true;
        selected.remove(item.containerSlug);
        available.remove(item.containerSlug);
        downloadable.remove(item.containerSlug);
        selected.add(item.containerSlug);
    }

    private void deselect(int position) {
        RCItem item = getItem(position);
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
    private void markItemDeleted(int position) {
        RCItem item = getItem(position);
        if(item != null) {
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
        RCItem item = getItem(position);
        if(item != null) {
            item.hasUpdates = false;
            item.downloaded = true;
            select(position); // auto select download item
        }
        sort();
    }

    public void setItemClickListener(OnItemClickListener listener) {
        itemClickListener = listener;
    }

    public void setItemHasUpdates(int position, boolean hasUpdates) {
        RCItem item = getItem(position);
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
