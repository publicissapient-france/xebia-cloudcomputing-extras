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
package fr.xebia.workshop.monitoring;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * <p>
 * Team infrastructure for the lab.
 * </p>
 */
public class TeamInfrastructure implements Comparable {

    @Nullable
    public static String getNagiosUrl(@Nullable Instance nagios) throws IllegalStateException {
        if (nagios == null) {
            return null;
        }
        Preconditions.checkState(nagios.getPublicDnsName() != null && !nagios.getPublicDnsName().isEmpty(),
                "Given nagios is not yet initialized, it publicDnsName is null: %s", nagios);
        return "http://" + nagios.getPublicDnsName() + "/nagios";
    }


    @Nullable
    public static String getGraphiteUrl(@Nullable Instance graphite) throws IllegalStateException {
        if (graphite == null) {
            return null;
        }
        Preconditions.checkState(graphite.getPublicDnsName() != null && !graphite.getPublicDnsName().isEmpty(),
                "Given graphite is not yet initialized, it publicDnsName is null: %s", graphite);
        return "http://" + graphite.getPublicDnsName() + "/";
    }

    static final String ROLE_NAGIOS = "nagios";

    static final String ROLE_GRAPHITE = "graphite";

    private final WorkshopInfrastructure workshopInfrastructure;

    private final String identifier;

    private Instance nagios;

    private String nagiosName = "#rundeck#";

    private Instance graphite;

    private String graphiteName = "#rundeck#";

    public TeamInfrastructure(WorkshopInfrastructure workshopInfrastructure, String identifier) {
        super();
        this.workshopInfrastructure = workshopInfrastructure;
        this.identifier = Preconditions.checkNotNull(identifier);
    }

    public void addInstance(Instance instance, Map<String, String> tags) {
        String name = tags.get("Name");
        String role = tags.get("Role");

        if (ROLE_NAGIOS.equals(role)) {
            this.nagios = instance;
            this.nagiosName = name;
        } else if (ROLE_GRAPHITE.equals(role)) {
            this.graphite = instance;
            this.graphiteName = name;

        }
    }

    /**
     * The Amazon EC2 instance of the nagios dev server
     */
    public Instance getNagios() {
        return replaceIfNull(nagios);
    }

    public void setNagios(Instance nagios) {
        this.nagios = nagios;
    }

    /**
     * The Amazon EC2 Name of the nagios dev server
     */
    public String getNagiosName() {
        return nagiosName;
    }

    public void setNagiosName(String nagiosName) {
        this.nagiosName = nagiosName;
    }

    /**
     * The Amazon EC2 instance of the nagios dev server
     */
    public Instance getGraphite() {
        return replaceIfNull(graphite);
    }

    public void setGraphite(Instance graphite) {
        this.graphite = graphite;
    }

    /**
     * The Amazon EC2 Name of the nagios dev server
     */
    public String getGraphiteName() {
        return graphiteName;
    }

    public void setGraphiteName(String graphiteName) {
        this.graphiteName = graphiteName;
    }

    /**
     * Team identifier like a trigram or a number ("clc", "team-1", etc).
     */
    public String getIdentifier() {
        return identifier;
    }


    private static Instance replaceIfNull(Instance instance) {
        return instance != null ? instance : new NullInstance();
    }


    @Override
    public String toString() {
        return Objects.toStringHelper(this) //
                .add("id", identifier) //
                .add(nagiosName, nagios) //
                .add(graphiteName, graphite) //
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeamInfrastructure that = (TeamInfrastructure) o;

        if (!identifier.equals(that.identifier)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof TeamInfrastructure) {
            return getIdentifier().compareTo(((TeamInfrastructure) o).getIdentifier());
        }
        return 0;
    }

    // quick fix for to please FreeMarker
    public static class NullInstance extends Instance {
        @Override
        public String getPrivateDnsName() {
            return "";
        }

        @Override
        public String getPrivateIpAddress() {
            return "";
        }

        @Override
        public String getPublicDnsName() {
            return "";
        }

        @Override
        public String getPublicIpAddress() {
            return "";
        }
    }
}
