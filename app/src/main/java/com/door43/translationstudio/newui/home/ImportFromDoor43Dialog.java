package com.door43.translationstudio.newui.home;

import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.door43.tools.reporting.Logger;
import com.door43.translationstudio.AppContext;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Profile;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TargetTranslationMigrator;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.dialogs.CustomAlertDialog;
import com.door43.translationstudio.tasks.CloneRepositoryTask;
import com.door43.translationstudio.tasks.RegisterSSHKeysTask;
import com.door43.translationstudio.tasks.SearchGogsRepositoriesTask;
import com.door43.translationstudio.tasks.SearchGogsUsersTask;
import com.door43.util.tasks.GenericTaskWatcher;
import com.door43.util.tasks.ManagedTask;
import com.door43.util.tasks.TaskManager;
import com.door43.widget.ViewUtil;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.gogsclient.Repository;
import org.unfoldingword.gogsclient.User;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by joel on 5/10/16.
 */
public class ImportFromDoor43Dialog extends DialogFragment implements GenericTaskWatcher.OnFinishedListener {
    private static final String STATE_REPOSITORIES = "state_repositories";
    private GenericTaskWatcher taskWatcher;
    private RestoreFromCloudAdapter adapter;
    private Translator translator;
    private List<Repository> repositories = new ArrayList<>();
    private String cloneHtmlUrl;
    private File cloneDestDir;
    private EditText repoEditText;
    private EditText userEditText;
    private List<User> users = new ArrayList<>();

    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = inflater.inflate(R.layout.dialog_import_from_door43, container, false);

        this.taskWatcher = new GenericTaskWatcher(getActivity(), R.string.loading);
        this.taskWatcher.setOnFinishedListener(this);

        this.translator = AppContext.getTranslator();

        Button dismissButton = (Button) v.findViewById(R.id.dismiss_button);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                taskWatcher.stop();
                SearchGogsRepositoriesTask task = (SearchGogsRepositoriesTask) TaskManager.getTask(SearchGogsRepositoriesTask.TASK_ID);
                if (task != null) {
                    task.stop();
                    TaskManager.cancelTask(task);
                    TaskManager.clearTask(task);
                }
                dismiss();
            }
        });
        userEditText = (EditText)v.findViewById(R.id.username);
        repoEditText = (EditText)v.findViewById(R.id.translation_id);

        v.findViewById(R.id.search_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = userEditText.getText().toString();
                String query = repoEditText.getText().toString();
                // if username has changed search for the user then the query
                // if no query is given then list the repos from the user
                // if no username but has query then just search repos
                // if nothing then do nothing
                boolean usernameChanged = true;
                if(!username.isEmpty() && usernameChanged) {
                    Profile profile = AppContext.getProfile();
                    if(profile != null && profile.gogsUser != null) {
                        SearchGogsUsersTask userTask = new SearchGogsUsersTask(profile.gogsUser, username, 3);
                        taskWatcher.watch(userTask);
                        TaskManager.addTask(userTask, SearchGogsUsersTask.TASK_ID);
                    }
                } else if(!query.isEmpty()) {
                    SearchGogsRepositoriesTask reposTask = new SearchGogsRepositoriesTask(0, query);
                    taskWatcher.watch(reposTask);
                    TaskManager.addTask(reposTask, SearchGogsRepositoriesTask.TASK_ID);
                }
            }
        });

        ListView list = (ListView) v.findViewById(R.id.list);
        adapter = new RestoreFromCloudAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Repository repo = adapter.getItem(position);
                String repoName = repo.getFullName().replace("/", "-");
                cloneDestDir = new File(AppContext.context().getCacheDir(), repoName + System.currentTimeMillis() + "/");
                cloneHtmlUrl = repo.getHtmlUrl();
                CloneRepositoryTask task = new CloneRepositoryTask(cloneHtmlUrl, cloneDestDir);
                taskWatcher.watch(task);
                TaskManager.addTask(task, CloneRepositoryTask.TASK_ID);
            }
        });

        // restore state
        if(savedInstanceState != null) {
            String[] repoJsonArray = savedInstanceState.getStringArray(STATE_REPOSITORIES);
            if(repoJsonArray != null) {
                for (String json : repoJsonArray) {
                    try {
                        Repository repo = Repository.fromJSON(new JSONObject(json));
                        if (json != null) {
                            repositories.add(repo);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                adapter.setRepositories(repositories);
            }
        }

        // connect to existing task
        SearchGogsRepositoriesTask reposTask = (SearchGogsRepositoriesTask) TaskManager.getTask(SearchGogsRepositoriesTask.TASK_ID);
        CloneRepositoryTask cloneTask = (CloneRepositoryTask) TaskManager.getTask(CloneRepositoryTask.TASK_ID);
        if (reposTask != null) {
            taskWatcher.watch(reposTask);
        } else if (cloneTask != null) {
            taskWatcher.watch(cloneTask);
        }

        return v;
    }

    @Override
    public void onFinished(ManagedTask task) {
        taskWatcher.stop();
        TaskManager.clearTask(task);

        if (task instanceof SearchGogsUsersTask) {
            this.users = ((SearchGogsUsersTask)task).getUsers();
            for(User user:users) {
                String query = this.repoEditText.getText().toString().trim();
                // TODO: 5/11/16 query for repos. group together. id task with user id.
                // TODO: 5/11/16 it may be better to create a wrapper task that will handle searching for users and their repos.
                // then we just need to worry about one task here. It would also keep the loading dialog working like expected.
                

//                SearchGogsRepositoriesTask reposTask = new SearchGogsRepositoriesTask(user.getId(), query);
//                taskWatcher.watch(reposTask);
//                TaskManager.addTask(reposTask, SearchGogsRepositoriesTask.TASK_ID);
            }

            // TODO: 5/11/16 begin searching for repos for the top 2 users
        } else if (task instanceof SearchGogsRepositoriesTask) {
            this.repositories = ((SearchGogsRepositoriesTask) task).getRepositories();
            adapter.setRepositories(repositories);

        } else if (task instanceof CloneRepositoryTask) {
            if (!task.isCanceled()) {
                CloneRepositoryTask.Status status = ((CloneRepositoryTask)task).getStatus();
                File tempPath = ((CloneRepositoryTask) task).getDestDir();
                String cloneUrl = ((CloneRepositoryTask) task).getCloneUrl();

                if(status == CloneRepositoryTask.Status.SUCCESS) {
                    Logger.i(this.getClass().getName(), "Repository cloned from " + cloneUrl);
                    tempPath = TargetTranslationMigrator.migrate(tempPath);
                    TargetTranslation tempTargetTranslation = TargetTranslation.open(tempPath);
                    boolean importFailed = false;
                    if (tempTargetTranslation != null) {
                        TargetTranslation existingTargetTranslation = translator.getTargetTranslation(tempTargetTranslation.getId());
                        if (existingTargetTranslation != null) {
                            // merge target translation
                            try {
                                existingTargetTranslation.merge(tempPath);
                            } catch (Exception e) {
                                Logger.e(this.getClass().getName(), "Failed to merge the target translation", e);
                                notifyImportFailed();
                                importFailed = true;
                            }
                        } else {
                            // restore the new target translation
                            try {
                                translator.restoreTargetTranslation(tempTargetTranslation);
                            } catch (IOException e) {
                                Logger.e(this.getClass().getName(), "Failed to import the target translation " + tempTargetTranslation.getId(), e);
                                notifyImportFailed();
                                importFailed = true;
                            }
                        }
                    } else {
                        Logger.e(this.getClass().getName(), "Failed to open the online backup");
                        notifyImportFailed();
                        importFailed = true;
                    }
                    FileUtils.deleteQuietly(tempPath);

                    if(!importFailed) {
                        Handler hand = new Handler(Looper.getMainLooper());
                        hand.post(new Runnable() {
                            @Override
                            public void run() {
                                // todo: terrible hack. We should instead register a listener with the dialog
                                ((HomeActivity) getActivity()).notifyDatasetChanged();

                                Snackbar snack = Snackbar.make(ImportFromDoor43Dialog.this.getView(), R.string.success, Snackbar.LENGTH_SHORT);
                                ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
                                snack.show();
                            }
                        });
                    }
                } else if(status == CloneRepositoryTask.Status.AUTH_FAILURE) {
                    Logger.i(this.getClass().getName(), "Authentication failed");
                    // if we have already tried ask the user if they would like to try again
                    if(AppContext.context().hasSSHKeys()) {
                        showAuthFailure();
                        return;
                    }

                    RegisterSSHKeysTask keyTask = new RegisterSSHKeysTask(false);
                    taskWatcher.watch(keyTask);
                    TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID);
                } else {
                    notifyImportFailed();
                }
            }

        } else if(task instanceof RegisterSSHKeysTask) {
            if(((RegisterSSHKeysTask)task).isSuccess()) {
                Logger.i(this.getClass().getName(), "SSH keys were registered with the server");
                // try to clone again
                CloneRepositoryTask pullTask = new CloneRepositoryTask(cloneHtmlUrl, cloneDestDir);
                taskWatcher.watch(pullTask);
                TaskManager.addTask(pullTask, CloneRepositoryTask.TASK_ID);
            } else {
                notifyImportFailed();
            }
        }
    }

    public void showAuthFailure() {
        CustomAlertDialog.Create(getActivity())
                .setTitle(R.string.error).setMessage(R.string.auth_failure_retry)
                .setPositiveButton(R.string.yes, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        RegisterSSHKeysTask keyTask = new RegisterSSHKeysTask(true);
                        taskWatcher.watch(keyTask);
                        TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID);
                    }
                })
                .setNegativeButton(R.string.no, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        notifyImportFailed();
                    }
                })
                .show("auth-failed");
    }

    public void notifyImportFailed() {
        CustomAlertDialog.Create(getActivity())
                .setTitle(R.string.error)
                .setMessage(R.string.restore_failed)
                .setPositiveButton(R.string.dismiss, null)
                .show("publish-failed");
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        List<String> repoJsonList = new ArrayList<>();
        for (Repository r : repositories) {
            repoJsonList.add(r.toJSON().toString());
        }
        out.putStringArray(STATE_REPOSITORIES, repoJsonList.toArray(new String[repoJsonList.size()]));
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroy() {
        taskWatcher.stop();
        super.onDestroy();
    }
}
