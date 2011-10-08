/*
 * Copyright 2008-2010 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.workshop.git;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.api.v2.services.GitHubException;
import com.github.api.v2.services.GitHubServiceFactory;
import com.github.api.v2.services.RepositoryService;
import com.github.api.v2.services.auth.Authentication;
import com.github.api.v2.services.auth.LoginPasswordAuthentication;
import com.github.api.v2.services.auth.OAuthAuthentication;

/**
 * Delete github repositories.
 *
 */
public class GithubRepositoriesDeleter {

    private static final Logger logger = LoggerFactory.getLogger(GithubRepositoriesDeleter.class);

    private final GitHubServiceFactory gitHubServiceFactory;

    private final List<String> githubRepositoryNames = new ArrayList<String>();

    // auth for github
    private Authentication authentication;

    public GithubRepositoriesDeleter() {
        gitHubServiceFactory = GitHubServiceFactory.newInstance();
    }

    public GithubRepositoriesDeleter(GitHubServiceFactory factory) {
        gitHubServiceFactory = factory;
    }

    /**
     * Show use cases
     * @param args
     */
    public static void main(String[] args) {
        final GithubRepositoriesDeleter deleter = new GithubRepositoriesDeleter()
                .withGithubLoginPassword("xebia-guest", args[0]);

        for (String team : asList("1", "2", "3")) {
            deleter.githubRepository("xebia-petclinic-" + team);

        }
        deleter.deleteRepositories();
    }

    public GithubRepositoriesDeleter githubRepository(String gitHubRepositoryUrl) {
        this.githubRepositoryNames .add(gitHubRepositoryUrl);
        return this;
    }

    public GithubRepositoriesDeleter withGithubLoginPassword(String login, String password) {
        this.authentication = new LoginPasswordAuthentication(login, password);
        return this;
    }

    public GithubRepositoriesDeleter withGithubOAuthToken(String githubOAuthToken) {
        this.authentication = new OAuthAuthentication(githubOAuthToken);
        return this;
    }

    public void deleteRepositories() {
        RepositoryService repositoryService = gitHubServiceFactory.createRepositoryService();

        repositoryService.setAuthentication(authentication);
        for (String githubRepositoryName : githubRepositoryNames) {
            logger.info("Github repository {} is deleting", githubRepositoryName);
            try {
                repositoryService.deleteRepository(githubRepositoryName);
            } catch (GitHubException e) {
                logger.error("Cannot delete github repository", e);
            }
        }
        githubRepositoryNames.clear();
    }
}

