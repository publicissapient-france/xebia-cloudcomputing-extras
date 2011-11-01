package fr.xebia.workshop.monitoring;

import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class WorkshopInfrastructure {

    private final List<String> teamIdentifiers = newArrayList();
    private String keyPairName;

    private WorkshopInfrastructure() {
    }

    public static Builder create() {
        return new Builder();
    }

    public List<String> getTeamIdentifiers() {
        return teamIdentifiers;
    }

    public int getTeamCount() {
        return teamIdentifiers.size();
    }

    public String getKeyPairName() {
        return keyPairName;
    }


    public static class Builder {

        private WorkshopInfrastructure infra = new WorkshopInfrastructure();

        public WorkshopInfrastructure build() {
            return infra;
        }


        public Builder withTeamIdentifiers(Collection<String> teamIdentifiers) {
            infra.teamIdentifiers.addAll(teamIdentifiers);
            return this;
        }


        public Builder withKeyPairName(String keyPairName) {
            infra.keyPairName = keyPairName;
            return this;
        }
    }
}
