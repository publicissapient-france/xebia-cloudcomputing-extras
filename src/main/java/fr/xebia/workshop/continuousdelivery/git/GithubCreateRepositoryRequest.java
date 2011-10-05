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

import com.github.api.v2.services.auth.Authentication;
import com.github.api.v2.services.auth.LoginPasswordAuthentication;
import com.github.api.v2.services.auth.OAuthAuthentication;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GithubCreateRepositoryRequest {

    public static final String DEFAULT_HOST = "github.com";

    public enum AccessType { HTTP,
        SSH };

    private String githubRepositoryUrl;

    private String repositoryName;

    private String description;

    private String homepage;

    private String accountName;

    private AccessType accessType = AccessType.SSH;

    private String host = DEFAULT_HOST;

    private GitRepositoryHandler gitRepositoryHandler;

    // auth for github
    private Authentication authentication;

    // auth for jgit
    private CredentialsProvider credentialsProvider;

    public void initWithDefaultGithubCreateRepositoryRequest(GithubCreateRepositoryRequest defaultCreateRepositoryRequest) {
        if (accessType == AccessType.SSH && defaultCreateRepositoryRequest.getAccessType() != null) {
            accessType = defaultCreateRepositoryRequest.getAccessType();
        }
        if (accountName == null) {
            accountName = defaultCreateRepositoryRequest.getAccountName();
        }
        if (description == null) {
            description = defaultCreateRepositoryRequest.getDescription();
        }
        if (homepage == null) {
            homepage = defaultCreateRepositoryRequest.getHomepage();
        }
        if (authentication == null) {
            authentication = defaultCreateRepositoryRequest.getAuthentication();
        }
        if (credentialsProvider == null) {
            credentialsProvider = defaultCreateRepositoryRequest.getCredentialsProvider();
        }
        if (DEFAULT_HOST.equals(host) && defaultCreateRepositoryRequest.getHost() != null) {
            host = defaultCreateRepositoryRequest.getHost();
        }
    }

    public GithubCreateRepositoryRequest toGithubRepositoryUrl(String repositoryUrl) {
        this.githubRepositoryUrl = repositoryUrl;
        return this;
    }

    public GithubCreateRepositoryRequest withGitRepositoryHandler(GitRepositoryHandler gitRepositoryUpdater) {
        this.gitRepositoryHandler = gitRepositoryUpdater;
        return this;
    }

    public GithubCreateRepositoryRequest toRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
        return this;
    }

    public GithubCreateRepositoryRequest withDescription(String description) {
        this.description = description;
        return this;
    }

    public GithubCreateRepositoryRequest withHomepage(String homepage) {
        this.homepage = homepage;
        return this;
    }

    public GithubCreateRepositoryRequest onAccountName(String accountName) {
        this.accountName = accountName;
        return this;
    }

    public GithubCreateRepositoryRequest withAccessType(AccessType accessType) {
        this.accessType = accessType;
        return this;
    }

    public GithubCreateRepositoryRequest atHost(String host) {
        this.host = host;
        return this;
    }

    public GithubCreateRepositoryRequest withGithubLoginPassword(String githubLogin, String githubPassword) {
        this.authentication = new LoginPasswordAuthentication(githubLogin, githubPassword);
        this.credentialsProvider = new UsernamePasswordCredentialsProvider(githubLogin, githubPassword);
         return this;
    }

    public GithubCreateRepositoryRequest withGithubOAuthToken(String githubOAuth) {
        this.authentication = new OAuthAuthentication(githubOAuth);
         return this;
    }

    public String getGithubRepositoryUrl() {
        if (githubRepositoryUrl == null) {
            githubRepositoryUrl = buildGithubRepositoryUrl(accessType);
        }
        return githubRepositoryUrl;
    }

    private String buildGithubRepositoryUrl(AccessType accessType) {
        switch (accessType) {
            case HTTP :
                return new StringBuilder()
                        .append("https://")
                        .append(accountName)
                        .append("@")
                        .append(host)
                        .append("/")
                        .append(accountName)
                        .append("/")
                        .append(repositoryName)
                        .append(".git")
                        .toString();
            case SSH :
                return new StringBuilder()
                        .append("git@")
                        .append(host)
                        .append(":")
                        .append(accountName)
                        .append("/")
                        .append(repositoryName)
                        .append(".git")
                        .toString();
            default:
                throw new IllegalArgumentException("access type not defined");
        }
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getDescription() {
        return description;
    }

    public String getHomepage() {
        return homepage;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public String getHost() {
        return host;
    }

    public GitRepositoryHandler getGitRepositoryHandler() {
        return gitRepositoryHandler;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public CredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }
}
