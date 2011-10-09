package fr.xebia.workshop.continuousdelivery;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Creates and holds the properties of a "Petclinic" project for a given team ID.
 */
public class PetclinicProjectInstance {

    public final String accountName;
    public final String groupId;
    public final String artifactId;
    public final String projectName;

    public PetclinicProjectInstance(String accountName, String teamId) {
        checkArgument(accountName != null && !accountName.trim().isEmpty(), "AccountName must not be blank");
        checkArgument(teamId != null && !teamId.trim().isEmpty(), "Team ID must not be blank");

        this.accountName = accountName;
        groupId = "fr.xebia.demo.petclinic-" + teamId;
        artifactId = "xebia-petclinic";
        projectName = artifactId + "-" + teamId;
    }
}
