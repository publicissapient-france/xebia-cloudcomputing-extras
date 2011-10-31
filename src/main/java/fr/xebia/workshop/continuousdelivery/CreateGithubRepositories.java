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
