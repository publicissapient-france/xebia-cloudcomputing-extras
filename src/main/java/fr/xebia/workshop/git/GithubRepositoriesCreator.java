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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.api.v2.schema.Repository;
import com.github.api.v2.services.GitHubServiceFactory;
import com.github.api.v2.services.RepositoryService;

/**
 * Creates github repositories. You can specify a source github repository and a GitRepositoryHandler to modify the
 * repository before to push it.
 * <p/>
 * 
 * If you use HTTP way, you have to use a login/password access.<p/>
 * If you use SSH way, you can use oauth token for github access and you have to configure a private key which has
 * access to the target account.<p/>
 * To configure ssh key :<br/>
 * http://help.github.com/linux-set-up-git/<br/>
 * http://help.github.com/ssh-issues/<br/>
 * http://help.github.com/multiple-ssh-keys/<br/>
 * 
 */
public class GithubRepositoriesCreator {

    private static final Logger logger = LoggerFactory.getLogger(GithubRepositoriesCreator.class);

    public static final String GIT_TMP_REPO_PREFIX = "git-tmp-repo-";
    public static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
    public static final String DEFAULT_TMPDIR = "temp";

    private final GitHubServiceFactory gitHubServiceFactory;

    private String sourceGitHubRepositoryUrl;

    private List<GithubCreateRepositoryRequest> createRequests = new ArrayList<GithubCreateRepositoryRequest>();

    private GithubCreateRepositoryRequest defaultGithubCreateRepositoryRequest = new GithubCreateRepositoryRequest();
    
    public GithubRepositoriesCreator() {
        gitHubServiceFactory = GitHubServiceFactory.newInstance();
    }

    public GithubRepositoriesCreator(GitHubServiceFactory factory) {
        gitHubServiceFactory = factory;
    }

    /**
     * Use cases just for demo
     *
     * @param args
     */
    public static void main(String[] args) {
        String[] teams = {"team1", "team2"};

        if (GithubCreateRepositoryRequest.AccessType.HTTP.name().equals(args[0])) {
            String passwd = args[1];

            final GithubRepositoriesCreator creator = new GithubRepositoriesCreator()
                    .fromGithubRepository("git://github.com/xebia-france-training/xebia-petclinic-lite.git")
                    .onAccountName("xebia-guest")
                    .withAccessType(GithubCreateRepositoryRequest.AccessType.HTTP)
                    .withGithubLoginPassword("xebia-guest", passwd);

            for (String team : teams) {
                creator.addGithubCreateRepositoryRequest(new GithubCreateRepositoryRequest()
                        .toRepositoryName("xebia-petclinic-lite-" + team)
                        .withGitRepositoryHandler(new UpdatePomFileAndCommit(team)));
            }
            creator.createRepositories();
        }

        if (GithubCreateRepositoryRequest.AccessType.SSH.name().equals(args[0])) {
            String token = args[1];

            final GithubRepositoriesCreator jobCreator = new GithubRepositoriesCreator()
                    .fromGithubRepository("git@github.com:xebia-france-training/xebia-petclinic-lite.git")
                    .onAccountName("xebia-guest")
                    .atHost("github-xebia-guest")
                    .withGithubOAuthToken(token);

            for (String team : teams) {
                jobCreator.addGithubCreateRepositoryRequest(new GithubCreateRepositoryRequest()
                        .toRepositoryName("xebia-petclinic-lite-" + team)
                        .withGitRepositoryHandler(new UpdatePomFileAndCommit(team)));
            }
            jobCreator.createRepositories();
        }
    }

    public GithubRepositoriesCreator fromGithubRepository(String sourceGitHubRepositoryUrl) {
        this.sourceGitHubRepositoryUrl = sourceGitHubRepositoryUrl;
        return this;
    }

    public GithubRepositoriesCreator addGithubCreateRepositoryRequest(GithubCreateRepositoryRequest githubCreateRepositoryRequest) {
        this.createRequests.add(githubCreateRepositoryRequest);
        return this;
    }

    public GithubRepositoriesCreator withDescription(String defaultDescription) {
        this.defaultGithubCreateRepositoryRequest.withDescription(defaultDescription);
        return this;
    }

    public GithubRepositoriesCreator withHomepage(String defaultHomepage) {
        this.defaultGithubCreateRepositoryRequest.withHomepage(defaultHomepage);
        return this;
    }

    public GithubRepositoriesCreator onAccountName(String defaultTargetAccountName) {
        this.defaultGithubCreateRepositoryRequest.onAccountName(defaultTargetAccountName);
        return this;
    }

    public GithubRepositoriesCreator withAccessType(GithubCreateRepositoryRequest.AccessType defaultAccessType) {
        this.defaultGithubCreateRepositoryRequest.withAccessType(defaultAccessType);
        return this;
    }

    public GithubRepositoriesCreator atHost(String defaultHost) {
        this.defaultGithubCreateRepositoryRequest.atHost(defaultHost);
        return this;
    }

    public GithubRepositoriesCreator withGithubLoginPassword(String defaultLogin, String defaultPassword) {
        this.defaultGithubCreateRepositoryRequest.withGithubLoginPassword(defaultLogin, defaultPassword);
        return this;
    }

    public GithubRepositoriesCreator withGithubOAuthToken(String defaulGithubOAuthToken) {
        this.defaultGithubCreateRepositoryRequest.withGithubOAuthToken(defaulGithubOAuthToken);
        return this;
    }

    public List<Repository> createRepositories() {
        List<Repository> repositories = new ArrayList<Repository>();
        File tmpRepoDir = getTmpLocalRepositoryDir();
        Git git = initGitLocalRepository(tmpRepoDir);

        for (GithubCreateRepositoryRequest createRequest : createRequests) {
            try {
                createRequest.initWithDefaultGithubCreateRepositoryRequest(defaultGithubCreateRepositoryRequest);

                Repository repository = createGithubRepository(createRequest);

                if (createRequest.getGitRepositoryHandler() != null) {
                    processGitRepositoryHandler(git, createRequest);

                }
                repositories.add(repository);
            } catch (Exception e) {
                logger.error("Could not create repository: " + createRequest, e);
            }
        }

        // clean
        try {
            FileUtils.deleteDirectory(tmpRepoDir);
        } catch (IOException e) {
            logger.warn("cannot delete local temporary git repository", e);
        }
        createRequests.clear();

        return repositories;
    }

    private void processGitRepositoryHandler(Git git, GithubCreateRepositoryRequest createRequest) {
        try {
            RevCommit revCommit = git.log().call().iterator().next();

            //call delegate
            createRequest.getGitRepositoryHandler().updateGitRepository(git, createRequest);
            logger.info("Local repository is pushing on remote {}", createRequest.getGithubRepositoryUrl());
            git.push()
                    .setCredentialsProvider(createRequest.getCredentialsProvider())
                    .setRemote(createRequest.getGithubRepositoryUrl())
                    .call();
            git.reset()
                    .setRef(revCommit.getName())
                    .setMode(ResetCommand.ResetType.HARD)
                    .call();
        } catch (IOException e) {
            throw new RuntimeException("cannot perform git operation", e);
        } catch (GitAPIException e) {
            throw new RuntimeException("cannot perform git operation", e);
        }
    }

    private Git initGitLocalRepository(File tmpRepoDir) {
        Git git;
        if (sourceGitHubRepositoryUrl != null) {
            logger.info("Repository {} is cloning into {}", new Object[] {sourceGitHubRepositoryUrl, tmpRepoDir.getAbsolutePath()});
            git = Git.cloneRepository()
                    .setURI(sourceGitHubRepositoryUrl)
                    .setDirectory(tmpRepoDir)
                    .call();
        } else {
            logger.info("Repository is initiating into {}", tmpRepoDir);
            git = Git.init()
                    .setDirectory(tmpRepoDir)
                    .call();
        }
        return git;
    }

    private Repository createGithubRepository(GithubCreateRepositoryRequest createRepositoryRequest) {
        RepositoryService repositoryService = gitHubServiceFactory.createRepositoryService();

        repositoryService.setAuthentication(createRepositoryRequest.getAuthentication());
        logger.info("Repository {} is creating on github account {}", createRepositoryRequest.getRepositoryName(), createRepositoryRequest.getAccountName());
        return repositoryService.createRepository(createRepositoryRequest.getRepositoryName(), createRepositoryRequest.getDescription(), createRepositoryRequest.getHomepage(), Repository.Visibility.PUBLIC);
    }

    private File getTmpLocalRepositoryDir() {
        String systemTmpDirStr = System.getProperty(JAVA_IO_TMPDIR, DEFAULT_TMPDIR);
        File systemTmpDir = new File(systemTmpDirStr);
        File tmpLocalRepositoryDir = new File(systemTmpDir, GIT_TMP_REPO_PREFIX + UUID.randomUUID());
        if (!tmpLocalRepositoryDir.mkdir()) {
            throw new IllegalStateException("Cannot create temporary directory." + systemTmpDir.getAbsolutePath());
        }
        return tmpLocalRepositoryDir;
    }

}

