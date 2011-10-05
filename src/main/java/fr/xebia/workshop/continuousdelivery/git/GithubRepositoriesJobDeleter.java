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
package fr.xebia.workshop.continuousdelivery.git;

import com.github.api.v2.services.GitHubServiceFactory;
import com.github.api.v2.services.RepositoryService;
import com.github.api.v2.services.auth.Authentication;
import com.github.api.v2.services.auth.LoginPasswordAuthentication;
import com.github.api.v2.services.auth.OAuthAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Delete github repositories.
 *
 */
public class GithubRepositoriesJobDeleter {

    private static final Logger logger = LoggerFactory.getLogger(GithubRepositoriesJobCreator.class);

    GitHubServiceFactory gitHubServiceFactory = GitHubServiceFactory.newInstance();

    private List<String> githubRepositoryNames = new ArrayList<String>();

    // auth for github
    private Authentication authentication;

    /**
     * Show use cases
     * @param args
     */
    public static void main(String[] args) {
        String[] teams = {"team1", "team2"};

        final GithubRepositoriesJobDeleter jobCreator = new GithubRepositoriesJobDeleter()
                .withGithubLoginPassword("xebia-guest", args[0]);

        for (String team : teams) {
            jobCreator.githubRepository("xebia-petclinic-" + team);

        }
        jobCreator.deleteRepositories();
    }

    public GithubRepositoriesJobDeleter githubRepository(String gitHubRepositoryUrl) {
        this.githubRepositoryNames .add(gitHubRepositoryUrl);
        return this;
    }

    public GithubRepositoriesJobDeleter withGithubLoginPassword(String login, String password) {
        this.authentication = new LoginPasswordAuthentication(login, password);
        return this;
    }

    public GithubRepositoriesJobDeleter withGithubOAuthToken(String githubOAuthToken) {
        this.authentication = new OAuthAuthentication(githubOAuthToken);
        return this;
    }

    public void deleteRepositories() {
        RepositoryService repositoryService = gitHubServiceFactory.createRepositoryService();

        repositoryService.setAuthentication(authentication);
        for (String githubRepositoryName : githubRepositoryNames) {
            logger.info("Github repository {} is deleting", githubRepositoryName);
            repositoryService.deleteRepository(githubRepositoryName);
        }
        githubRepositoryNames.clear();
    }
}

