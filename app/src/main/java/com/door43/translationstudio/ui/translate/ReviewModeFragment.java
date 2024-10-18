package com.door43.translationstudio.ui.translate;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Frame;
import com.door43.translationstudio.core.TranslationArticle;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.core.Util;
import com.door43.translationstudio.databinding.FragmentResourcesArticleBinding;
import com.door43.translationstudio.databinding.FragmentResourcesExampleItemBinding;
import com.door43.translationstudio.databinding.FragmentResourcesNoteBinding;
import com.door43.translationstudio.databinding.FragmentResourcesQuestionBinding;
import com.door43.translationstudio.databinding.FragmentResourcesWordBinding;
import com.door43.translationstudio.databinding.FragmentWordsIndexListBinding;
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

import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.Language;
import org.unfoldingword.resourcecontainer.Link;
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
public class ReviewModeFragment extends ViewModeFragment implements ReviewModeAdapter.OnRenderHelpsListener {

    private static final String STATE_RESOURCES_OPEN = "state_resources_open";
    private static final String STATE_RESOURCES_DRAWER_OPEN = "state_resources_drawer_open";
    private static final String STATE_WORD_ID = "state_word_id";
    private static final String STATE_HELP_TITLE = "state_help_title";
    private static final String STATE_HELP_BODY = "state_help_body";
    private static final String STATE_HELP_TYPE = "state_help_type";

    private boolean resourcesOpen = false;
    private boolean resourcesDrawerOpen = false;
    private String translationWordId;

    private TranslationHelp translationQuestion = null;
    private TranslationHelp translationNote = null;

    private boolean resourcesOpened = false;
    private boolean enableMergeConflictsFilter = false;

    @Override
    ViewModeAdapter generateAdapter() {
        return new ReviewModeAdapter(resourcesOpen, enableMergeConflictsFilter, typography);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            enableMergeConflictsFilter = args.getBoolean(
                    TargetTranslationActivity.STATE_FILTER_MERGE_CONFLICTS,
                    false
            );
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(savedInstanceState != null) {
            resourcesOpen = savedInstanceState.getBoolean(STATE_RESOURCES_OPEN, false);
            resourcesDrawerOpen = savedInstanceState.getBoolean(STATE_RESOURCES_DRAWER_OPEN, false);

            if(savedInstanceState.containsKey(STATE_WORD_ID)) {
                translationWordId = savedInstanceState.getString(STATE_WORD_ID);
            } else if(savedInstanceState.containsKey(STATE_HELP_TYPE)) {
                String type = savedInstanceState.getString(STATE_HELP_TYPE);
                TranslationHelp help = new TranslationHelp(
                        savedInstanceState.getString(STATE_HELP_TITLE),
                        savedInstanceState.getString(STATE_HELP_BODY)
                );
                if(type != null && type.equals("tn")) {
                    translationNote = help;
                } else if(type != null && type.equals("tq")) {
                    translationQuestion = help;
                }
            }
        }
        ((ReviewModeAdapter) getAdapter()).setOnRenderHelpsListener(this);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected void setupObservers() {
        super.setupObservers();

        viewModel.getRenderHelpsResult().observe(getViewLifecycleOwner(), result -> {
            if(result != null) renderHelpsResult(result.getItem(), result.getHelps());
        });
    }

    @Override
    public void onTaskFinished(final ManagedTask task) {
        super.onTaskFinished(task);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onPrepareView(final View rootView) {
        binding.closeResourcesDrawerBtn.setOnClickListener(v -> closeResourcesDrawer());

        // open the drawer on rotate
        if(resourcesDrawerOpen && resourcesOpen) {
            ViewTreeObserver viewTreeObserver = rootView.getViewTreeObserver();
            if(viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        ReviewHolder sample = (ReviewHolder) getViewHolderSample();
                        if (sample != null) {
                            if (translationNote != null) {
                                onTranslationNoteClick(translationNote, sample.getResourceCardWidth());
                            } else if (translationWordId != null) {
                                onTranslationWordClick(
                                        viewModel.getResourceContainer().slug,
                                        translationWordId,
                                        sample.getResourceCardWidth()
                                );
                            } else if (translationQuestion != null) {
                                onTranslationQuestionClick(translationQuestion, sample.getResourceCardWidth());
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
        if(resourcesDrawerOpen) {
            closeResourcesDrawer();
        } else {
            if (getAdapter() != null) {
                closeResources();
            }
        }
    }

    @Override
    protected void onLeftSwipe(MotionEvent e1, MotionEvent e2) {
        if(getAdapter() != null) {
            openResources();
        }
    }

    /**
     * Checks if the resources are open
     *
     * @return
     */
    public boolean isResourcesOpen() {
        return resourcesOpened;
    }

    private void openResourcesDrawer(int width) {
        resourcesDrawerOpen = true;
        ViewGroup.LayoutParams params = binding.resourcesDrawerCard.getLayoutParams();
        params.width = width;
        binding.resourcesDrawerCard.setLayoutParams(params);
        // TODO: animate in
    }

    private void closeResourcesDrawer() {
        resourcesDrawerOpen = false;
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
    private void renderTranslationWordsIndex(String resourceContainerSlug) {
        binding.scrollingResourcesDrawerContent.setVisibility(View.GONE);
        binding.resourcesDrawerContent.setVisibility(View.VISIBLE);

        FragmentWordsIndexListBinding wordsIndexListBinding =
                FragmentWordsIndexListBinding.inflate(requireActivity().getLayoutInflater());

        binding.resourcesDrawerContent.removeAllViews();
        binding.resourcesDrawerContent.addView(wordsIndexListBinding.list);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireActivity(),
                R.layout.list_clickable_text
        );
        final ResourceContainer rc = viewModel.getResourceContainer(resourceContainerSlug);
        if (rc != null) {
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
            wordsIndexListBinding.list.setAdapter(adapter);
            wordsIndexListBinding.list.setOnItemClickListener((parent, view, position, id) -> {
                String slug = words.get(position);
                renderTranslationWord(rc.slug, slug);
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

            FragmentResourcesArticleBinding articleBinding = FragmentResourcesArticleBinding.inflate(requireActivity().getLayoutInflater());
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
//            typography.format(title, sourceLanguage.getId(), sourceLanguage.getDirection());

//            descriptionView.setText(renderer.render(article.getBody()));
//            typography.formatSub(descriptionView, sourceLanguage.getId(), sourceLanguage.getDirection());
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
            articleBinding.getRoot().loadData(typography.getStyle(TranslationType.SOURCE )
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
        translationWordId = chapterSlug;
        final ResourceContainer rc = viewModel.getResourceContainer(resourceContainerSlug);

        if (rc != null) {
            binding.resourcesDrawerContent.setVisibility(View.GONE);
            binding.scrollingResourcesDrawerContent.setVisibility(View.VISIBLE);
            binding.scrollingResourcesDrawerContent.scrollTo(0, 0);

            FragmentResourcesWordBinding wordBinding = FragmentResourcesWordBinding.inflate(requireActivity().getLayoutInflater());

            wordBinding.wordsIndex.setOnClickListener(v -> renderTranslationWordsIndex(resourceContainerSlug));
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
            typography.formatTitle(TranslationType.SOURCE, wordBinding.descriptionTitle, rc.language.slug, rc.language.direction);
            HtmlRenderer renderer = new HtmlRenderer(span -> {
                boolean result = false;

                if(span instanceof ArticleLinkSpan link) {
                    TranslationArticle article = getPreferredTranslationArticle(rc, link.getVolume(), link.getManual(), link.getId());
                    if(article != null) {
                        link.setTitle(article.getTitle());
                        result = true;
                    }
                } else if(span instanceof PassageLinkSpan link) {
                    String chunk = rc.readChunk(link.getChapterId(), link.getFrameId());
                    String verseTitle = Frame.parseVerseTitle(chunk, TranslationFormat.parse(rc.contentMimeType));
                    String title = rc.readChunk("front", "title") + " " + Integer.parseInt(link.getChapterId()) + ":" + verseTitle;
                    link.setTitle(title);
                    result = !chunk.isEmpty();
                } else if(span instanceof TranslationWordLinkSpan) {
                    ResourceContainer currentRC = getSelectedResourceContainer();
                    if (currentRC != null) {
                        Pattern titlePattern = Pattern.compile("#(.*)");
                        ResourceContainer closestRc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw");

                        if (closestRc != null) {
                            String closestWord = closestRc.readChunk(span.getMachineReadable().toString(), "01");
                            if(!closestWord.isEmpty()) {
                                Matcher linkMatch = titlePattern.matcher(closestWord.trim());
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
                    switch (type) {
                        case "ta" -> {
                            String url = span.getMachineReadable().toString();
                            ArticleLinkSpan link = ArticleLinkSpan.parse(url);
                            if (link != null) {
                                onTranslationArticleClick(link.getVolume(), link.getManual(), link.getId(), binding.resourcesDrawerCard.getLayoutParams().width);
                            }
                        }
                        case "p" -> {
                            String url = span.getMachineReadable().toString();
                            PassageLinkSpan link = new PassageLinkSpan("", url);
                            scrollToChunk(link.getChapterId(), link.getFrameId());
                        }
                        case "m" -> {
                            // markdown link
                            final String url = span.getMachineReadable().toString();
                            new AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                                    .setTitle(R.string.view_online)
                                    .setMessage(R.string.use_internet_confirmation)
                                    .setNegativeButton(R.string.title_cancel, null)
                                    .setPositiveButton(R.string.label_continue, (dialogInterface, i) -> {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                        startActivity(intent);
                                    })
                                    .show();
                        }
                        case "tw" -> {
                            // translation word
                            ResourceContainer currentRC = getSelectedResourceContainer();
                            if (currentRC != null) {
                                ResourceContainer rc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw");
                                if (rc != null) {
                                    onTranslationWordClick(rc.slug, span.getMachineReadable().toString(), binding.resourcesDrawerCard.getLayoutParams().width);
                                }
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
            typography.formatSub(TranslationType.SOURCE, wordBinding.description, rc.language.slug, rc.language.direction);

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
                        Button button = new Button(new ContextThemeWrapper(requireActivity(), R.style.Widget_Button_Tag), null, R.style.Widget_Button_Tag);
                        button.setText(relatedTitle);
                        button.setOnClickListener(v ->
                                onTranslationWordClick(rc.slug, relatedSlug, binding.resourcesDrawerCard.getLayoutParams().width)
                        );
                        typography.formatSub(TranslationType.SOURCE, button, rc.language.slug, rc.language.direction);
                        wordBinding.seeAlso.addView(button);
                    }
                    if(!relatedSlugs.isEmpty()) wordBinding.seeAlsoTitle.setVisibility(View.VISIBLE);
                }
                if(chapterConfig.containsKey("examples")) {
                    List<String> exampleSlugs = (List<String>)chapterConfig.get("examples");
                    for(String exampleSlug:exampleSlugs) {
                        final String[] slugs = exampleSlug.split("-");
                        if(slugs.length != 2) continue;

                        String projectTitle = viewModel.getResourceContainer().readChunk("front", "title");

                        // get verse title
                        String verseTitle = StringUtilities.formatNumber(slugs[1]);
                        if(viewModel.getResourceContainer().contentMimeType.equals("text/usfm")) {
                            verseTitle = Frame.parseVerseTitle(
                                    viewModel.getResourceContainer().readChunk(slugs[0], slugs[1]),
                                    TranslationFormat.parse(viewModel.getResourceContainer().contentMimeType)
                            );
                        }

                        FragmentResourcesExampleItemBinding examplesBinding = FragmentResourcesExampleItemBinding.inflate(requireActivity().getLayoutInflater());

                        examplesBinding.reference.setText(projectTitle.trim() + " " + StringUtilities.formatNumber(slugs[0]) + ":" + verseTitle);
                        examplesBinding.passage.setHtmlFromString(viewModel.getResourceContainer().readChunk(slugs[0], slugs[1]), true);
                        examplesBinding.getRoot().setOnClickListener(v -> scrollToChunk(slugs[0], slugs[1]));
                        typography.formatSub( TranslationType.SOURCE, examplesBinding.reference, rc.language.slug, rc.language.direction);
                        typography.formatSub(TranslationType.SOURCE, examplesBinding.passage, rc.language.slug, rc.language.direction);
                        wordBinding.examples.addView(examplesBinding.getRoot());
                    }
                    if(!exampleSlugs.isEmpty()) wordBinding.examplesTitle.setVisibility(View.VISIBLE);
                }
            }
            typography.formatTitle(TranslationType.SOURCE, wordBinding.seeAlsoTitle, rc.language.slug, rc.language.direction);
            typography.formatTitle(TranslationType.SOURCE, wordBinding.examplesTitle, rc.language.slug, rc.language.direction);

            binding.scrollingResourcesDrawerContent.removeAllViews();
            binding.scrollingResourcesDrawerContent.addView(wordBinding.getRoot());
        }
    }

    /**
     * Prepares the resources drawer with the translation note
     * @param note
     */
    private void renderTranslationNote(TranslationHelp note) {
        translationNote = note;
        translationWordId = null;

        final ResourceContainer sourceTranslation = getSelectedResourceContainer();
//        TranslationNote note = null;//getPreferredNote(sourceTranslation, chapterId, frameId, noteId);
        binding.resourcesDrawerContent.setVisibility(View.GONE);
        binding.scrollingResourcesDrawerContent.setVisibility(View.VISIBLE);
        binding.scrollingResourcesDrawerContent.scrollTo(0, 0);

        FragmentResourcesNoteBinding noteBinding = FragmentResourcesNoteBinding.inflate(requireActivity().getLayoutInflater());
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
                switch (type) {
                    case "ta" -> {
                        String url = span.getMachineReadable().toString();
                        ArticleLinkSpan link = ArticleLinkSpan.parse(url);
                        if (link != null) {
                            onTranslationArticleClick(link.getVolume(), link.getManual(), link.getId(), binding.resourcesDrawerCard.getLayoutParams().width);
                        }
                    }
                    case "p" -> {
                        String url = span.getMachineReadable().toString();
                        PassageLinkSpan link = new PassageLinkSpan("", url);
                        scrollToChunk(link.getChapterId(), link.getFrameId());
                    }
                    case "m" -> {
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
                    }
                    case "tw" -> {
                        // translation word
                        ResourceContainer currentRC = getSelectedResourceContainer();
                        if (currentRC != null) {
                            ResourceContainer rc = viewModel.getClosestResourceContainer(currentRC.language.slug, "bible", "tw");
                            if (rc != null) {
                                onTranslationWordClick(rc.slug, span.getMachineReadable().toString(), binding.resourcesDrawerCard.getLayoutParams().width);
                            }
                        }
                    }
                    case "sr" -> {
                        // reference
                        ShortReferenceSpan link = new ShortReferenceSpan(span.getMachineReadable().toString());
                        scrollToVerse(link.getChapter(), link.getVerse());
                    }
                }
            }

            @Override
            public void onLongClick(View view, Span span, int start, int end) {

            }
        });

        noteBinding.title.setText(note.title);
        Language sourceLanguage = viewModel.getSourceLanguage();
        if (sourceLanguage != null) {
            typography.format(TranslationType.SOURCE, noteBinding.title, sourceLanguage.slug, sourceLanguage.direction);
            noteBinding.description.setText(renderer.render(note.body));
            typography.formatSub(TranslationType.SOURCE, noteBinding.description, sourceLanguage.slug, sourceLanguage.direction);
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
        translationWordId = null;
        translationQuestion = question;

        binding.resourcesDrawerContent.setVisibility(View.GONE);
        binding.scrollingResourcesDrawerContent.setVisibility(View.VISIBLE);
        binding.scrollingResourcesDrawerContent.scrollTo(0, 0);

        FragmentResourcesQuestionBinding questionBinding = FragmentResourcesQuestionBinding.inflate(requireActivity().getLayoutInflater());
//            mCloseResourcesDrawerButton.setText(question.getQuestion());

        Language sourceLanguage = viewModel.getSourceLanguage();
        if (sourceLanguage != null) {
            typography.formatTitle(TranslationType.SOURCE, questionBinding.questionTitle, sourceLanguage.slug, sourceLanguage.direction);
            typography.formatTitle(TranslationType.SOURCE, questionBinding.answerTitle, sourceLanguage.slug, sourceLanguage.direction);

            questionBinding.question.setText(question.title);
            typography.formatSub(TranslationType.SOURCE, questionBinding.question, sourceLanguage.slug, sourceLanguage.direction);
            questionBinding.answer.setText(question.body);
            typography.formatSub(TranslationType.SOURCE, questionBinding.answer, sourceLanguage.slug, sourceLanguage.direction);
        }

        binding.scrollingResourcesDrawerContent.removeAllViews();
        binding.scrollingResourcesDrawerContent.addView(questionBinding.getRoot());
    }

    private void renderHelpsResult(ListItem item, Map<String, Object> helps) {
        // skip if resources are closed
        if (!isResourcesOpen()) return;

        final List<TranslationHelp> notes = (List<TranslationHelp>) helps.get("notes");
        final List<Link> words = (List<Link>) helps.get("words");
        final List<TranslationHelp> questions = (List<TranslationHelp>) helps.get("questions");

        final int position = getAdapter().filteredItems.indexOf(item);

        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(() -> {
            ReviewHolder holder = (ReviewHolder) getVisibleViewHolder(position);
            if (holder != null) {
                holder.setResources(item.source.language, notes, questions, words);
                // TODO: 2/28/17 select the correct tab
            } else {
                getAdapter().notifyItemChanged(position);
            }
        });
    }

    @Override
    public void onRenderHelps(ListItem item) {
        viewModel.renderHelps(item);
    }

    /**
     * opens the resources view
     */
    public void openResources() {
        if (!resourcesOpened) {
            resourcesOpened = true;
            getAdapter().setResourcesOpened(true);
        }
    }

    /**
     * closes the resources view
     */
    public void closeResources() {
        if (resourcesOpened) {
            resourcesOpened = false;
            getAdapter().setResourcesOpened(false);
            viewModel.cancelRenderJobs();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putBoolean(STATE_RESOURCES_OPEN, isResourcesOpen());
        out.putBoolean(STATE_RESOURCES_DRAWER_OPEN, resourcesDrawerOpen);
        if(translationWordId != null) {
            out.putString(STATE_WORD_ID, translationWordId);
        } else {
            out.remove(STATE_WORD_ID);
        }
        if(translationNote != null) {
            out.putString(STATE_HELP_TITLE, translationNote.title);
            out.putString(STATE_HELP_BODY, translationNote.body);
            out.putString(STATE_HELP_TYPE, "tn");
        } else if(translationQuestion != null) {
            out.putString(STATE_HELP_TITLE, translationQuestion.title);
            out.putString(STATE_HELP_BODY, translationQuestion.body);
            out.putString(STATE_HELP_TYPE, "tq");
        } else {
            out.remove(STATE_HELP_TITLE);
            out.remove(STATE_HELP_BODY);
            out.remove(STATE_HELP_TYPE);
        }
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

    @Override
    public String getVerseChunk(String chapter, String verse) {
        return Util.mapVerseToChunk(viewModel.getResourceContainer(), chapter, verse);
    }
}
