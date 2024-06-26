package com.door43.translationstudio.tasks;

import android.os.Process;

import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.App;
import com.door43.translationstudio.core.Profile;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.git.Repo;
import com.door43.translationstudio.git.TransportCallback;
import com.door43.util.Manifest;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.merge.MergeStrategy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Pulls down changes from a remote target translation repository
 */
public class PullTargetTranslationTask extends ManagedTask {

    public static final String TASK_ID = "pull_target_translation_task";
    public final TargetTranslation targetTranslation;
    private final MergeStrategy mergeStrategy;
    private String message = "";
    private Status status = Status.UNKNOWN;
    private Map<String, int[][]> conflicts = new HashMap<>();
    private String sourceURL = null;

    /**
     * do a pull from a specific URL
     * @param targetTranslation
     * @param mergeStrategy
     * @param sourceURL
     */
    public PullTargetTranslationTask(TargetTranslation targetTranslation, MergeStrategy mergeStrategy, String sourceURL) {
        setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
        this.targetTranslation = targetTranslation;
        this.mergeStrategy = mergeStrategy;
        this.sourceURL = sourceURL;
    }

    @Override
    public void start() {
        if(App.isNetworkAvailable()) {
            // submit new language requests
            delegate(new SubmitNewLanguageRequestsTask());

            Profile profile = App.getProfile();
            if (targetTranslation != null && App.isNetworkAvailable() && profile != null && profile.gogsUser != null) {
                publishProgress(-1, "Downloading updates");

                if(sourceURL == null) {
                    GetRepositoryTask repoTask = new GetRepositoryTask(profile.gogsUser, targetTranslation);
                    delegate(repoTask);

                    if (repoTask.getRepository() != null) {
                        sourceURL = repoTask.getRepository().getSshUrl();
                    }
                }

                try {
                    this.targetTranslation.commitSync();
                } catch (Exception e) {
                    Logger.w(this.getClass().getName(), "Failed to commit the target translation " + targetTranslation.getId(), e);
                }
                Repo repo = this.targetTranslation.getRepo();
                createBackupBranch(repo);
                this.message = pull(repo, sourceURL);
            }
        }
    }

    private void createBackupBranch(Repo repo) {
        try {
            Git git  = repo.getGit();
            DeleteBranchCommand deleteBranchCommand = git.branchDelete();
            deleteBranchCommand.setBranchNames("backup-master")
                    .setForce(true)
                    .call();
            CreateBranchCommand createBranchCommand = git.branchCreate();
            createBranchCommand.setName("backup-master")
                    .setForce(true)
                    .call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String pull(Repo repo, String remote) {
        Git git;
        try {
            repo.deleteRemote("origin");
            repo.setRemote("origin", remote);
            git = repo.getGit();
        } catch (IOException e) {
            return null;
        }

        Manifest localManifest = Manifest.generate(this.targetTranslation.getPath());

        // TODO: we might want to get some progress feedback for the user
        PullCommand pullCommand = git.pull()
                .setTransportConfigCallback(new TransportCallback())
                .setRemote("origin")
                .setStrategy(mergeStrategy)
                .setRemoteBranchName("master");
        try {
            PullResult result = pullCommand.call();
            MergeResult mergeResult = result.getMergeResult();
            if(mergeResult != null && mergeResult.getConflicts() != null && mergeResult.getConflicts().size() > 0) {
                this.status = Status.MERGE_CONFLICTS;
                this.conflicts = mergeResult.getConflicts();

                // revert manifest merge conflict to avoid corruption
                if(this.conflicts.containsKey("manifest.json")) {
                    Logger.i("PullTargetTranslationTask", "Reverting to server manifest");
                    try {
                        git.checkout()
                                .setStage(CheckoutCommand.Stage.THEIRS)
                                .addPath("manifest.json")
                                .call();
                        Manifest remoteManifest = Manifest.generate(this.targetTranslation.getPath());
                        localManifest = TargetTranslation.mergeManifests(localManifest, remoteManifest);
                    } catch (CheckoutConflictException e) {
                        // failed to reset manifest.json
                        Logger.e(this.getClass().getName(), "Failed to reset manifest: " + e.getMessage(), e);
                    } finally {
                        localManifest.save();
                    }
                }

                // keep our license
                if(this.conflicts.containsKey("LICENSE.md")) {
                    Logger.i("PullTargetTranslationTask", "Reverting to local license");
                    try {
                        git.checkout()
                                .setStage(CheckoutCommand.Stage.OURS)
                                .addPath("LICENSE.md")
                                .call();
                    } catch(CheckoutConflictException e) {
                        Logger.e(this.getClass().getName(), "Failed to reset license: " + e.getMessage(), e);
                    }
                }
            } else {
                this.status = Status.UP_TO_DATE;
            }

            return "message";
        } catch (TransportException e) {
            Logger.e(this.getClass().getName(), e.getMessage(), e);
            Throwable cause = e.getCause();
            if(cause != null) {
                Throwable subException = cause.getCause();
                if(subException != null) {
                    String detail = subException.getMessage();
                    if ("Auth fail".equals(detail)) {
                        this.status = Status.AUTH_FAILURE; // we do special handling for auth failure
                    }
                } else if(cause instanceof NoRemoteRepositoryException) {
                    this.status = Status.NO_REMOTE_REPO;
                }
            }
            return null;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if(cause instanceof NoRemoteRepositoryException) {
                this.status = Status.NO_REMOTE_REPO;
            }
            Logger.e(this.getClass().getName(), e.getMessage(), e);
            return null;
        } catch (OutOfMemoryError e) {
            Logger.e(this.getClass().getName(), e.getMessage(), e);
            this.status = Status.OUT_OF_MEMORY;
            return null;
        } catch (Throwable e) {
            Logger.e(this.getClass().getName(), e.getMessage(), e);
            return null;
        }
    }

    public String getMessage() {
        return message;
    }

    public Status getStatus() {
        return status;
    }

    public enum Status {
        UP_TO_DATE,
        MERGE_CONFLICTS,
        OUT_OF_MEMORY,
        AUTH_FAILURE,
        NO_REMOTE_REPO,
        UNKNOWN
    }
}
