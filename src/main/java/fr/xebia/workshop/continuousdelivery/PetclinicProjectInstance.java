package fr.xebia.workshop.continuousdelivery;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Creates and holds the properties of a "Petclinic" project for a given team ID.
 */
public class PetclinicProjectInstance {

    public final String groupId;
    public final String artifactId;
    public final String projectName;

    public PetclinicProjectInstance(String teamId) {
        checkArgument(teamId != null && !teamId.trim().isEmpty(), "Team ID must not be blank");

        groupId = "fr.xebia.demo.petclinic-" + teamId;
        artifactId = "xebia-petclinic";
        projectName = artifactId + "-" + teamId;
    }
}
