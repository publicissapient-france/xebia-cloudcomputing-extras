package fr.xebia.workshop.continuousdelivery;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Creates and holds the properties of a "Petclinic" project for a given team ID.
 */
public class PetclinicJobInstance {

    private final String githubAccountName;
    private final String groupId;
    private final String artifactId;
    private final String projectName;

    public PetclinicJobInstance(WorkshopInfrastructure infrastructure, String teamId) {
        githubAccountName = infrastructure.getGithubGuestAccountName();
        
        checkArgument(githubAccountName != null && !githubAccountName.trim().isEmpty(), "AccountName must not be blank");
        checkArgument(teamId != null && !teamId.trim().isEmpty(), "Team ID must not be blank");

        groupId = "fr.xebia.demo.petclinic-" + teamId;
        artifactId = "xebia-petclinic-lite";
        projectName = artifactId + "-" + teamId;
    }

    public String getGithubAccountName() {
        return githubAccountName;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getProjectName() {
        return projectName;
    }
}
