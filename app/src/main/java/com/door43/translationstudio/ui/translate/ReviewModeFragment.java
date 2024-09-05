package com.door43.translationstudio.ui.translate;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Frame;
import com.door43.translationstudio.core.TranslationArticle;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.databinding.FragmentResourcesArticleBinding;
import com.door43.translationstudio.databinding.FragmentResourcesExampleItemBinding;
import com.door43.translationstudio.databinding.FragmentResourcesNoteBinding;
import com.door43.translationstudio.databinding.FragmentResourcesQuestionBinding;
import com.door43.translationstudio.databinding.FragmentResourcesWordBinding;
import com.door43.translationstudio.rendering.HtmlRenderer;
import com.door43.translationstudio.rendering.LinkToHtmlRenderer;
import com.door43.translationstudio.ui.spannables.ArticleLinkSpan;
import com.door43.translationstudio.ui.spannables.LinkSpan;
import com.door43.translationstudio.ui.spannables.PassageLinkSpan;
import com.door43.translationstudio.ui.spannables.ShortReferenceSpan;
import com.door43.translationstudio.ui.spannables.Span;
import com.door43.translationstudio.ui.spannables.TranslationWordLinkSpan;
import com.door43.translationstudio.ui.translate.review.ReviewHolder;
import com.door43.util.StringUtilities;

import org.markdownj.MarkdownProcessor;
import org.sufficientlysecure.htmltextview.LocalLinkMovementMethod;

import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by joel on 9/8/2015.
 */
public class ReviewModeFragment extends ViewModeFragment {

    private static final String STATE_RESOURCES_OPEN = "state_resources_open";
    private static final String STATE_RESOURCES_DRAWER_OPEN = "state_resources_drawer_open";
    private static final String STATE_WORD_ID = "state_word_id";
    private static final String STATE_NOTE_ID = "state_note_id";
    private static final String STATE_CHAPTER_ID = "state_chapter_id";
    private static final String STATE_FRAME_ID = "state_frame_id";
    private static final String STATE_RESOURCE_CONTAINER_SLUG = "container-slug";
    private static final String STATE_HELP_TITLE = "state_help_title";
    private static final String STATE_HELP_BODY = "state_help_body";
    private static final String STATE_HELP_TYPE = "state_help_type";

    private boolean mResourcesOpen = false;
    private boolean mResourcesDrawerOpen = false;
    private String mTranslationWordId;

    private String mResourceContainerSlug;
    private ResourceContainer mSourceContainer = null;
    private TranslationHelp mTranslationQuestion = null;
    private TranslationHelp mTranslationNote = null;

    @Override
    ViewModeAdapter generateAdapter(
            Activity activity,
            String targetTranslationId,
            String chapterId,
            String frameId,
            Bundle extras
    ) {
        return new ReviewModeAdapter(
                activity,
                targetTranslationId,
                chapterId,
                frameId,
                mResourcesOpen
        );
    }

    @Override
    public void onTaskFinished(final ManagedTask task) {
        super.onTaskFinished(task);
    }

    @Override
    protected void onSourceContainerLoaded(final ResourceContainer container) {
        mSourceContainer = container;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onPrepareView(final View rootView) {
        binding.closeResourcesDrawerBtn.setOnClickListener(v -> closeResourcesDrawer());

        // open the drawer on rotate
        if(mResourcesDrawerOpen && mResourcesOpen) {
            ViewTreeObserver viewTreeObserver = rootView.getViewTreeObserver();
            if(viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        ReviewHolder sample = (ReviewHolder) getViewHolderSample();
                        if (sample != null) {
                            if (mTranslationNote != null) {
                                onTranslationNoteClick(mTranslationNote, sample.getResourceCardWidth());
                            } else if (mTranslationWordId != null) {
                                onTranslationWordClick(mResourceContainerSlug, mTranslationWordId, sample.getResourceCardWidth());
                            } else if (mTranslationQuestion != null) {
                                onTranslationQuestionClick(mTranslationQuestion, sample.getResourceCardWidth());
                            }
                        }
                    }
                });
            }
        }
        closeResourcesDrawer();
    }

    @Override
    protected void onRightSwipe(MotionEvent e1, MotionEvent e2) {
        if(mResourcesDrawerOpen) {
            closeResourcesDrawer();
        } else {
            if (getAdapter() != null) {
                ((ReviewModeAdapter) getAdapter()).closeResources();
            }
        }
    }

    @Override
    protected void onLeftSwipe(MotionEvent e1, MotionEvent e2) {
        if(getAdapter() != null) {
            ((ReviewModeAdapter)getAdapter()).openResources();
        }
    }

    private void openResourcesDrawer(int width) {
        mResourcesDrawerOpen = true;
        ViewGroup.LayoutParams params = binding.resourcesDrawerCard.getLayoutParams();
        params.width = width;
        binding.resourcesDrawerCard.setLayoutParams(params);
        // TODO: animate in
    }

    private void closeResourcesDrawer() {
        mResourcesDrawerOpen = false;
        ViewGroup.LayoutParams params = binding.resourcesDrawerCard.getLayoutParams();
        params.width = 0;
        binding.resourcesDrawerCard.setLayoutParams(params);
        // TODO: animate
    }

    @Override
    public void onTranslationWordClick(String resourceContainerSlug, String chapterSlug, int width) {
        renderTranslationWord(resourceContainerSlug, chapterSlug);
        openResourcesDrawer(width);
    }

    @Override
    public void onTranslationArticleClick(String volume, String manual, String slug, int width) {
        renderTranslationArticle(volume, manual, slug);
        openResourcesDrawer(width);
    }

    @Override
    public void onTranslationNoteClick(TranslationHelp note, int width) {
        renderTranslationNote(note);
        openResourcesDrawer(width);
    }

    @Override
    public void onTranslationQuestionClick(TranslationHelp question, int width) {
        renderTranslationQuestion(question);
        openResourcesDrawer(width);
    }

    @Override
    public void markAllChunksDone() {
        getAdapter().markAllChunksDone();
    }

    /**
     * Prepares the resources drawer with the translation words index
     */
    private void renderTranslationWordsIndex() {
        binding.scrollingResourcesDrawerContent.setVisibility(View.GONE);
        binding.resourcesDrawerContent.setVisibility(View.VISIBLE);
//            mCloseResourcesDrawerButton.setText(getActivity().getResources().getString(R.string.translation_words_index));
        ListView list = (ListView) getActivity().getLayoutInflater().inflate(R.layout.fragment_words_index_list, null);
        binding.resourcesDrawerContent.removeAllViews();
        binding.resourcesDrawerContent.addView(list);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.list_clickable_text);
        final ResourceContainer rc = viewModel.getResourceContainer(mResourceContainerSlug);
        if(rc != null) {
            String[] chapters = rc.chapters();
            final List<String> words = Arrays.asList(chapters);
            Collections.sort(words);
            Pattern titlePattern = Pattern.compile("#(.*)");
            for(String slug:words) {
                // get title and add to adapter
                Matcher match = titlePattern.matcher(rc.readChunk(slug, "01"));
                if(match.find()) {
                    adapter.add(match.group(1));
                } else {
                    adapter.add(slug);
                }
            }
            list.setAdapter(adapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String slug = words.get(position);
                    renderTranslationWord(rc.slug, slug);
                }
            });
        }
    }

    /**
     * Prepares the resources drawer with the translation article
     * @param volume
     * @param manual
     * @param slug
     */
    private void renderTranslationArticle(String volume, String manual, String slug) {
        TranslationArticle article = getPreferredTranslationArticle(getSelectedResourceContainer(), volume, manual, slug);
        binding.resourcesDrawerContent.setVisibility(View.GONE);

        if(article != null) {
//            mCloseResourcesDrawerButton.setText(article.getTitle());
            binding.scrollingResourcesDrawerContent.setVisibility(View.VISIBLE);
            binding.scrollingResourcesDrawerContent.scrollTo(0, 0);

            FragmentResourcesArticleBinding articleBinding = FragmentResourcesArticleBinding.inflate(getLayoutInflater());
//            TextView title = (TextView)view.findViewById(R.id.title);
//            TextView descriptionView = (TextView)view.findViewById(R.id.description);

            final ResourceContainer resourceContainer = getSelectedResourceContainer();
            LinkToHtmlRenderer renderer = new LinkToHtmlRenderer(new LinkToHtmlRenderer.OnPreprocessLink() {
                @Override
                public boolean onPreprocess(Span span) {
                    if(span instanceof ArticleLinkSpan) {
                        ArticleLinkSpan link = ((ArticleLinkSpan)span);
                        TranslationArticle article = getPreferredTranslationArticle(resourceContainer, link.getVolume(), link.getManual(), link.getId());
                        if(article != null) {
                            link.setTitle(article.getTitle());
                        } else {
                            return false;
                        }
                    } else if(span instanceof PassageLinkSpan) {
                        PassageLinkSpan link = (PassageLinkSpan)span;
                        String text = resourceContainer.readChunk(link.getChapterId(), link.getFrameId());

//                        Frame frame = library.getFrame(resourceContainer, link.getChapterId(), link.getFrameId());
                        String title = resourceContainer.readChunk("front", "title") + " " + Integer.parseInt(link.getChapterId()) + ":" + Frame.parseVerseTitle(text, TranslationFormat.parse(resourceContainer.contentMimeType));
                        link.setTitle(title);
                        return !resourceContainer.readChunk(link.getChapterId(), link.getFrameId()).isEmpty();
                    }
                    return true;
                }
            });
//            , new Span.OnClickListener() {
//                @Override
//                public void onClick(View view, Span span, int start, int end) {
//                    if(((LinkSpan)span).getType().equals("ta")) {
//                        String url = span.getMachineReadable().toString();
//                        ArticleLinkSpan link = ArticleLinkSpan.parse(url);
//                        if(link != null) {
//                            onTranslationArticleClick(link.getVolume(), link.getManual(), link.getId(), mResourcesDrawer.getLayoutParams().width);
//                        }
//                    } else if(((LinkSpan)span).getType().equals("p")) {
//                        PassageLinkSpan link = (PassageLinkSpan) span;
//                        scrollToChunk(link.getChapterId(), link.getFrameId());
//                    }
//                }
//
//                @Override
//                public void onLongClick(View view, Span span, int start, int end) {
//
//                }
//            });

//            title.setText(article.getTitle());
//            SourceLanguage sourceLanguage = library.getSourceLanguage(sourceTranslation.projectSlug, sourceTranslation.sourceLanguageSlug);
//            Typography.format(getActivity(), title, sourceLanguage.getId(), sourceLanguage.getDirection());

//            descriptionView.setText(renderer.render(article.getBody()));
//            Typography.formatSub(getActivity(), descriptionView, sourceLanguage.getId(), sourceLanguage.getDirection());
//            descriptionView.setMovementMethod(LocalLinkMovementMethod.getInstance());

            articleBinding.getRoot().setWebViewClient(new LinkToHtmlRenderer.CustomWebViewClient() {
                @Override
                public void onOverriddenLinkClick(WebView view, String url, Span span) {
                    if (span instanceof ArticleLinkSpan) {
                        ArticleLinkSpan link = (ArticleLinkSpan) span;
                        onTranslationArticleClick(link.getVolume(), link.getManual(), link.getId(), binding.resourcesDrawerCard.getLayoutParams().width);
                    } else if (span instanceof PassageLinkSpan) {
                        PassageLinkSpan link = (PassageLinkSpan) span;
                        scrollToChunk(link.getChapterId(), link.getFrameId());
                    }
                }

                @Override
                public void onLinkClick(WebView view, final String url) {
                    // opens web url
                    new AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.view_online)
                            .setMessage(R.string.use_internet_confirmation)
                            .setNegativeButton(R.string.title_cancel, null)
                            .setPositiveButton(R.string.label_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    startActivity(intent);
                                }
                            })
                            .show();
                }
            });
            articleBinding.getRoot().loadData(Typography.getStyle(getActivity(), TranslationType.SOURCE )
                    + renderer.render(article.getBody()).toString(), "text/html", "utf-8");

            binding.scrollingResourcesDrawerContent.removeAllViews();
            binding.scrollingResourcesDrawerContent.addView(articleBinding.getRoot());
        }

    }

    /**
     * Prepares the resources drawer with the translation word
     * @param resourceContainerSlug
     * @param chapterSlug
     */
    private void renderTranslationWord(String resourceContainerSlug, String chapterSlug) {
        mTranslationWordId = chapterSlug;
        mResourceContainerSlug = resourceContainerSlug;

        final ResourceContainer rc = viewModel.getResourceContainer(resourceContainerSlug);
        binding.resourcesDrawerContent.setVisibility(View.GONE);
        binding.scrollingResourcesDrawerContent.setVisibility(View.VISIBLE);
        binding.scrollingResourcesDrawerContent.scrollTo(0, 0);

        FragmentResourcesWordBinding wordBinding = FragmentResourcesWordBinding.inflate(getLayoutInflater());

        wordBinding.wordsIndex.setOnClickListener(v -> renderTranslationWordsIndex());
        String word = rc.readChunk(chapterSlug, "01");
        Pattern pattern = Pattern.compile("#+([^\\n]+)\\n+([\\s\\S]*)");
        Matcher match = pattern.matcher(word);
        String description = "";
        if(match.find()) {
            wordBinding.wordTitle.setText(match.group(1));
            description = match.group(2);
            // TODO: 10/12/16 load the description title. This should be read from the config maybe?
            wordBinding.descriptionTitle.setText("Description");
        }
        Typography.formatTitle(getActivity(), TranslationType.SOURCE, wordBinding.descriptionTitle, rc.language.slug, rc.language.direction);
        HtmlRenderer renderer = new HtmlRenderer(span -> {
            Boolean result = false;

            if(span instanceof ArticleLinkSpan) {
                ArticleLinkSpan link = ((ArticleLinkSpan)span);
                TranslationArticle article = getPreferredTranslationArticle(rc, link.getVolume(), link.getManual(), link.getId());
                if(article != null) {
                    link.setTitle(article.getTitle());
                    result = true;
                }
            } else if(span instanceof PassageLinkSpan) {
                PassageLinkSpan link = (PassageLinkSpan)span;
                String chunk = rc.readChunk(link.getChapterId(), link.getFrameId());
                String verseTitle = Frame.parseVerseTitle(chunk, TranslationFormat.parse(rc.contentMimeType));
                String title = rc.readChunk("front", "title") + " " + Integer.parseInt(link.getChapterId()) + ":" + verseTitle;
                link.setTitle(title);
                result = !chunk.isEmpty();
            } else if(span instanceof TranslationWordLinkSpan) {
                ResourceContainer currentRC = getSelectedResourceContainer();
                if (currentRC != null) {
                    Pattern titlePattern = Pattern.compile("#(.*)");
                    ResourceContainer rc1 = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw");

                    if (rc1 != null) {
                        String word1 = rc1.readChunk(span.getMachineReadable().toString(), "01");
                        if(!word1.isEmpty()) {
                            Matcher linkMatch = titlePattern.matcher(word1.trim());
                            String title = span.getMachineReadable().toString();
                            if (linkMatch.find()) {
                                title = linkMatch.group(1);
                            }
                            ((TranslationWordLinkSpan) span).setTitle(title);
                            result = true;
                        }
                    }
                }
            }
            return result;
        }, new Span.OnClickListener() {
            @Override
            public void onClick(View view, Span span, int start, int end) {
                String type = ((LinkSpan)span).getType();
                if(type.equals("ta")) {
                    String url = span.getMachineReadable().toString();
                    ArticleLinkSpan link = ArticleLinkSpan.parse(url);
                    if(link != null) {
                        onTranslationArticleClick(link.getVolume(), link.getManual(), link.getId(), binding.resourcesDrawerCard.getLayoutParams().width);
                    }
                } else if(type.equals("p")) {
                    String url = span.getMachineReadable().toString();
                    PassageLinkSpan link = new PassageLinkSpan("", url);
                    scrollToChunk(link.getChapterId(), link.getFrameId());
                } else if(type.equals("m")) {
                    // markdown link
                    final String url = span.getMachineReadable().toString();
                    new AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.view_online)
                            .setMessage(R.string.use_internet_confirmation)
                            .setNegativeButton(R.string.title_cancel, null)
                            .setPositiveButton(R.string.label_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    startActivity(intent);
                                }
                            })
                            .show();
                } else if(type.equals("tw")) {
                    // translation word
                    ResourceContainer currentRC = getSelectedResourceContainer();
                    if (currentRC != null) {
                        ResourceContainer rc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw");
                        if(rc != null) {
                            onTranslationWordClick(rc.slug, span.getMachineReadable().toString(), binding.resourcesDrawerCard.getLayoutParams().width);
                        }
                    }
                }
            }

            @Override
            public void onLongClick(View view, Span span, int start, int end) {

            }
        });
        wordBinding.description.setText(renderer.render(description));
        wordBinding.description.setMovementMethod(LocalLinkMovementMethod.getInstance());
        Typography.formatSub(getActivity(), TranslationType.SOURCE, wordBinding.description, rc.language.slug, rc.language.direction);

        wordBinding.seeAlso.removeAllViews();
        wordBinding.seeAlsoTitle.setVisibility(View.GONE);
        wordBinding.examples.removeAllViews();
        wordBinding.examplesTitle.setVisibility(View.GONE);
        if(rc.config != null && rc.config.containsKey(chapterSlug)) {
            Map chapterConfig = (Map<String, List<String>> )rc.config.get(chapterSlug);
            if(chapterConfig.containsKey("see_also")) {
                Pattern titlePattern = Pattern.compile("#(.*)");
                List<String> relatedSlugs = (List<String>)chapterConfig.get("see_also");
                for(final String relatedSlug:relatedSlugs) {
                    // TODO: 10/12/16 the words need to have their title placed into a "title" file instead of being inline in the chunk
                    String relatedWord = rc.readChunk(relatedSlug, "01");
                    Matcher linkMatch = titlePattern.matcher(relatedWord.trim());
                    String relatedTitle = relatedSlug;
                    if(linkMatch.find()) {
                        relatedTitle = linkMatch.group(1);
                    }
                    Button button = new Button(new ContextThemeWrapper(getActivity(), R.style.Widget_Button_Tag), null, R.style.Widget_Button_Tag);
                    button.setText(relatedTitle);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onTranslationWordClick(rc.slug, relatedSlug, binding.resourcesDrawerCard.getLayoutParams().width);
                        }
                    });
                    Typography.formatSub(getActivity(), TranslationType.SOURCE, button, rc.language.slug, rc.language.direction);
                    wordBinding.seeAlso.addView(button);
                }
                if(!relatedSlugs.isEmpty()) wordBinding.seeAlsoTitle.setVisibility(View.VISIBLE);
            }
            if(chapterConfig.containsKey("examples")) {
                List<String> exampleSlugs = (List<String>)chapterConfig.get("examples");
                for(String exampleSlug:exampleSlugs) {
                    final String[] slugs = exampleSlug.split("-");
                    if(slugs.length != 2) continue;
                    if(mSourceContainer == null) continue;
                    String projectTitle = mSourceContainer.readChunk("front", "title");

                    // get verse title
                    String verseTitle = StringUtilities.formatNumber(slugs[1]);
                    if(mSourceContainer.contentMimeType.equals("text/usfm")) {
                        verseTitle = Frame.parseVerseTitle(mSourceContainer.readChunk(slugs[0], slugs[1]), TranslationFormat.parse(mSourceContainer.contentMimeType));
                    }

                    FragmentResourcesExampleItemBinding examplesBinding = FragmentResourcesExampleItemBinding.inflate(getLayoutInflater());

                    examplesBinding.reference.setText(projectTitle.trim() + " " + StringUtilities.formatNumber(slugs[0]) + ":" + verseTitle);
                    examplesBinding.passage.setHtmlFromString(mSourceContainer.readChunk(slugs[0], slugs[1]), true);
                    examplesBinding.getRoot().setOnClickListener(v -> scrollToChunk(slugs[0], slugs[1]));
                    Typography.formatSub(getActivity(), TranslationType.SOURCE, examplesBinding.reference, rc.language.slug, rc.language.direction);
                    Typography.formatSub(getActivity(), TranslationType.SOURCE, examplesBinding.passage, rc.language.slug, rc.language.direction);
                    wordBinding.examples.addView(examplesBinding.getRoot());
                }
                if(!exampleSlugs.isEmpty()) wordBinding.examplesTitle.setVisibility(View.VISIBLE);
            }
        }
        Typography.formatTitle(getActivity(), TranslationType.SOURCE, wordBinding.seeAlsoTitle, rc.language.slug, rc.language.direction);
        Typography.formatTitle(getActivity(), TranslationType.SOURCE, wordBinding.examplesTitle, rc.language.slug, rc.language.direction);

        binding.scrollingResourcesDrawerContent.removeAllViews();
        binding.scrollingResourcesDrawerContent.addView(wordBinding.getRoot());
    }

    /**
     * Prepares the resources drawer with the translation note
     * @param note
     */
    private void renderTranslationNote(TranslationHelp note) {
        mTranslationNote = note;
        mTranslationWordId = null;

        final ResourceContainer sourceTranslation = getSelectedResourceContainer();
//        TranslationNote note = null;//getPreferredNote(sourceTranslation, chapterId, frameId, noteId);
        binding.resourcesDrawerContent.setVisibility(View.GONE);
        binding.scrollingResourcesDrawerContent.setVisibility(View.VISIBLE);
        binding.scrollingResourcesDrawerContent.scrollTo(0, 0);

        FragmentResourcesNoteBinding noteBinding = FragmentResourcesNoteBinding.inflate(getLayoutInflater());
//            mCloseResourcesDrawerButton.setText(note.getTitle());

        HtmlRenderer renderer = new HtmlRenderer(span -> {
            Boolean result = false;
            if(span instanceof ArticleLinkSpan) {
                ArticleLinkSpan link = ((ArticleLinkSpan)span);
                TranslationArticle article = getPreferredTranslationArticle(sourceTranslation, link.getVolume(), link.getManual(), link.getId());
                if(article != null) {
                    link.setTitle(article.getTitle());
                    result = true;
                }
            } else if(span instanceof PassageLinkSpan) {
//                        PassageLinkSpan link = (PassageLinkSpan)span;
//                        String chapterID = link.getChapterId();
                // TODO: 3/30/2016 rather than assuming passage links are always referring to the current source translation we need to support links to other source translations
//                        Frame frame = library.getFrame(sourceTranslation, chapterID, link.getFrameId());
//                        if(frame != null) {
//                            String chapter = (chapterID != null) ? String.valueOf(Integer.parseInt(chapterID)) : ""; // handle null chapter ID
//                            String title = sourceTranslation.getProjectTitle() + " " + chapter + ":" + frame.getTitle();
//                            link.setTitle(title);
//                            return library.getFrame(sourceTranslation, chapterID, link.getFrameId()) != null;
//                            return false;
//                        } else {
//                            return false;
//                        }
            } else if(span instanceof TranslationWordLinkSpan) {
                ResourceContainer currentRC = getSelectedResourceContainer();
                if (currentRC != null) {
                    Pattern titlePattern = Pattern.compile("#(.*)");
                    ResourceContainer rc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw");
                    if (rc != null) {
                        String word = rc.readChunk(span.getMachineReadable().toString(), "01");
                        if(!word.isEmpty()) {
                            Matcher linkMatch = titlePattern.matcher(word.trim());
                            String title = span.getMachineReadable().toString();
                            if (linkMatch.find()) {
                                title = linkMatch.group(1);
                            }
                            ((TranslationWordLinkSpan) span).setTitle(title);
                            result = true;
                        }
                    }
                }
            } else if(span instanceof ShortReferenceSpan) {
//                        ShortReferenceSpan link = (ShortReferenceSpan)span;
//                        String chapterId = link.getChapter();
//                        String chunkId = link.getVerse();
                // TODO: 3/7/17 validate
            }
            return result;
        }, new Span.OnClickListener() {
            @Override
            public void onClick(View view, Span span, int start, int end) {
                String type = ((LinkSpan)span).getType();
                if(type.equals("ta")) {
                    String url = span.getMachineReadable().toString();
                    ArticleLinkSpan link = ArticleLinkSpan.parse(url);
                    if(link != null) {
                        onTranslationArticleClick(link.getVolume(), link.getManual(), link.getId(), binding.resourcesDrawerCard.getLayoutParams().width);
                    }
                } else if(type.equals("p")) {
                    String url = span.getMachineReadable().toString();
                    PassageLinkSpan link = new PassageLinkSpan("", url);
                    scrollToChunk(link.getChapterId(), link.getFrameId());
                } else if(type.equals("m")) {
                    // markdown link
                    final String url = span.getMachineReadable().toString();
                    new AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.view_online)
                            .setMessage(R.string.use_internet_confirmation)
                            .setNegativeButton(R.string.title_cancel, null)
                            .setPositiveButton(R.string.label_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    startActivity(intent);
                                }
                            })
                            .show();
                } else if(type.equals("tw")) {
                    // translation word
                    ResourceContainer currentRC = getSelectedResourceContainer();
                    if (currentRC != null) {
                        ResourceContainer rc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw");
                        if(rc != null) {
                            onTranslationWordClick(rc.slug, span.getMachineReadable().toString(), binding.resourcesDrawerCard.getLayoutParams().width);
                        }
                    }
                } else if(type.equals("sr")) {
                    // reference
                    ShortReferenceSpan link = new ShortReferenceSpan(span.getMachineReadable().toString());
                    scrollToVerse(link.getChapter(), link.getVerse());
                }
            }

            @Override
            public void onLongClick(View view, Span span, int start, int end) {

            }
        });

        noteBinding.title.setText(note.title);
        SourceLanguage sourceLanguage = viewModel.getSourceLanguage();
        if (sourceLanguage != null) {
            Typography.format(getActivity(), TranslationType.SOURCE, noteBinding.title, sourceLanguage.slug, sourceLanguage.direction);
            noteBinding.description.setText(renderer.render(note.body));
            Typography.formatSub(getActivity(), TranslationType.SOURCE, noteBinding.description, sourceLanguage.slug, sourceLanguage.direction);
            noteBinding.description.setMovementMethod(LocalLinkMovementMethod.getInstance());
        }

        binding.scrollingResourcesDrawerContent.removeAllViews();
        binding.scrollingResourcesDrawerContent.addView(noteBinding.getRoot());
    }

    /**
     * Prepares the resources drawer with the translation question
     * @param question the question to be displayed
     */
    private void renderTranslationQuestion(TranslationHelp question) {
        mTranslationWordId = null;
        mTranslationQuestion = question;

        SourceLanguage sourceLanguage = viewModel.getSourceLanguage();
        binding.resourcesDrawerContent.setVisibility(View.GONE);
        binding.scrollingResourcesDrawerContent.setVisibility(View.VISIBLE);
        binding.scrollingResourcesDrawerContent.scrollTo(0, 0);

        FragmentResourcesQuestionBinding questionBinding = FragmentResourcesQuestionBinding.inflate(getLayoutInflater());
//            mCloseResourcesDrawerButton.setText(question.getQuestion());

        Typography.formatTitle(getActivity(), TranslationType.SOURCE, questionBinding.questionTitle, sourceLanguage.slug, sourceLanguage.direction);
        Typography.formatTitle(getActivity(), TranslationType.SOURCE, questionBinding.answerTitle, sourceLanguage.slug, sourceLanguage.direction);

        questionBinding.question.setText(question.title);
        Typography.formatSub(getActivity(), TranslationType.SOURCE, questionBinding.question, sourceLanguage.slug, sourceLanguage.direction);
        questionBinding.answer.setText(question.body);
        Typography.formatSub(getActivity(), TranslationType.SOURCE, questionBinding.answer, sourceLanguage.slug, sourceLanguage.direction);

        binding.scrollingResourcesDrawerContent.removeAllViews();
        binding.scrollingResourcesDrawerContent.addView(questionBinding.getRoot());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            mResourcesOpen = savedInstanceState.getBoolean(STATE_RESOURCES_OPEN, false);
            mResourcesDrawerOpen = savedInstanceState.getBoolean(STATE_RESOURCES_DRAWER_OPEN, false);
            if(savedInstanceState.containsKey(STATE_WORD_ID)) {
                mTranslationWordId = savedInstanceState.getString(STATE_WORD_ID);
            } else if(savedInstanceState.containsKey(STATE_HELP_TYPE)) {
                String type = savedInstanceState.getString(STATE_HELP_TYPE);
                TranslationHelp help = new TranslationHelp(savedInstanceState.getString(STATE_HELP_TITLE), savedInstanceState.getString(STATE_HELP_TITLE));
                if(type != null && type.equals("tn")) {
                    mTranslationNote = help;
                } else if(type != null && type.equals("tq")) {
                    mTranslationQuestion = help;
                }
            }
            if(savedInstanceState.containsKey(STATE_RESOURCE_CONTAINER_SLUG)) {
                mResourceContainerSlug = savedInstanceState.getString(STATE_RESOURCE_CONTAINER_SLUG);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putBoolean(STATE_RESOURCES_OPEN, ((ReviewModeAdapter) getAdapter()).isResourcesOpen());
        out.putBoolean(STATE_RESOURCES_DRAWER_OPEN, mResourcesDrawerOpen);
        if(mTranslationWordId != null) {
            out.putString(STATE_WORD_ID, mTranslationWordId);
        } else {
            out.remove(STATE_WORD_ID);
        }
        if(mTranslationNote != null) {
            out.putString(STATE_HELP_TITLE, mTranslationNote.title);
            out.putString(STATE_HELP_BODY, mTranslationNote.body);
            out.putString(STATE_HELP_TYPE, "tn");
        } else if(mTranslationQuestion != null) {
            out.putString(STATE_HELP_TITLE, mTranslationQuestion.title);
            out.putString(STATE_HELP_BODY, mTranslationQuestion.body);
            out.putString(STATE_HELP_TYPE, "tq");
        } else {
            out.remove(STATE_HELP_TITLE);
            out.remove(STATE_HELP_BODY);
            out.remove(STATE_HELP_TYPE);
        }
        out.putString(STATE_RESOURCE_CONTAINER_SLUG, mResourceContainerSlug);
        super.onSaveInstanceState(out);
    }

    /**
     * Returns the preferred translation academy
     * if none exist in the source language it will return the english version.
     * @param resourceContainer
     * @param volume
     *@param manual
     * @param articleId  @return
     */
    private TranslationArticle getPreferredTranslationArticle(ResourceContainer resourceContainer, String volume, String manual, String articleId) {
        List<Translation> translations = viewModel.findTranslations(resourceContainer.language.slug, "ta-" + manual, volume, "man", null, 0, -1);
        if(translations.isEmpty()) return null;

        ResourceContainer container = viewModel.getResourceContainer(translations.get(0).resourceContainerSlug);
        if(container == null) return null;
        String title = container.readChunk(articleId, "title");
        String body = container.readChunk(articleId, "01");
        MarkdownProcessor md = new MarkdownProcessor();
        String html = md.markdown(body);

        return new TranslationArticle(volume, "ta-" + manual,  articleId, title, html, "test");
    }
}
