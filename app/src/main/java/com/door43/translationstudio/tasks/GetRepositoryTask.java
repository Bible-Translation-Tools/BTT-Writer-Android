package com.door43.translationstudio.tasks;

import com.door43.translationstudio.App;
import com.door43.translationstudio.core.TargetTranslation;

import org.unfoldingword.gogsclient.Repository;
import org.unfoldingword.gogsclient.User;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import java.util.List;
import java.util.Objects;

public class GetRepositoryTask extends ManagedTask {
    public static final String TASK_ID = "fetch_repository_task";

    private final User user;
    private final TargetTranslation translation;
    private Repository repository = null;

    public GetRepositoryTask(User user, TargetTranslation translation) {
        this.user = user;
        this.translation = translation;
    }

    @Override
    public void start() {
        if(App.isNetworkAvailable()) {
            publishProgress(-1, "Getting repository");

            // Create repository
            // If it exists, will do nothing
            CreateRepositoryTask createRepository = new CreateRepositoryTask(translation);
            delegate(createRepository);

            // Search for repository
            // There could be more than one repo, which name can contain requested repo name.
            // For example: en_ulb_mat_txt, custom_en_ulb_mat_text, en_ulb_mat_text_l3, etc.
            // Setting limit to 100 should be enough to cover most of the cases.
            SearchGogsRepositoriesTask searchReposTask = new SearchGogsRepositoriesTask(
                    user,
                    user.getId(),
                    translation.getId(),
                    100
            );
            delegate(searchReposTask);

            List<Repository> repositories = searchReposTask.getRepositories();

            if (repositories.size() > 0) {
                for (Repository repo: repositories) {
                    // Filter repos to have exact user and repo name
                    if (Objects.equals(repo.getOwner().getUsername(), user.getUsername()) &&
                            Objects.equals(repo.getName(), translation.getId())) {
                        this.repository = repo;
                        break;
                    }
                }
            }
        }
    }

    public Repository getRepository() {
        return this.repository;
    }
}
