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
package fr.xebia.workshop.continuousdelivery;

import com.amazonaws.services.ec2.AmazonEC2;

import fr.xebia.workshop.git.GithubCreateRepositoryRequest;
import fr.xebia.workshop.git.GithubRepositoriesCreator;
import fr.xebia.workshop.git.GithubRepositoriesDeleter;
import fr.xebia.workshop.git.UpdatePomFileAndCommit;

public class CreateGithubRepositories extends InfrastructureCreationStep {

    private final boolean deleteRepoWhenAlreadyExisting;

    public CreateGithubRepositories(boolean deleteRepoWhenAlreadyExisting) {
        this.deleteRepoWhenAlreadyExisting = deleteRepoWhenAlreadyExisting;
    }

    @Override
    public void execute(AmazonEC2 ec2, WorkshopInfrastructure infra) throws Exception {
        if (deleteRepoWhenAlreadyExisting) {
            final GithubRepositoriesDeleter deleter = new GithubRepositoriesDeleter()
                    .withGithubLoginPassword(infra.getGithubGuestAccountUsername(), infra.getGithubGuestAccountPassword());

            for (String teamId : infra.getTeamIdentifiers()) {
                deleter.githubRepositoryName(infra.getGithubRepositoryNameForTeam(teamId));
            }

            deleter.deleteRepositories();
        }

        final GithubRepositoriesCreator creator = new GithubRepositoriesCreator()
                .fromGithubRepository(infra.getGithubSourceRepository())
                .onAccountName(infra.getGithubGuestAccountName())
                .withAccessType(GithubCreateRepositoryRequest.AccessType.HTTP)
                .withGithubLoginPassword(infra.getGithubGuestAccountUsername(), infra.getGithubGuestAccountPassword());

        for (String teamId : infra.getTeamIdentifiers()) {
            creator.addGithubCreateRepositoryRequest(new GithubCreateRepositoryRequest()
                    .toRepositoryName(infra.getGithubRepositoryNameForTeam(teamId))
                    .withGitRepositoryHandler(new UpdatePomFileAndCommit(teamId)));
        }
        
        creator.createRepositories();
    }

}
