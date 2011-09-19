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

import javax.annotation.Nullable;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * <p>Team infrastructure for the lab.</p>
 * 
 */
public class TeamInfrastructure {
    public static final Function<String, TeamInfrastructure> FUNCTION_TEAM_IDENTIFIER_TO_TEAM_INFRASTRUCTURE = new Function<String, TeamInfrastructure>() {

        @Override
        public TeamInfrastructure apply(String teamIdentifier) {
            return new TeamInfrastructure(teamIdentifier);
        }
    };
    private final String identifier;

    private Instance jenkins;

    private Instance nexus;

    private Instance tomcatDev;

    private Instance tomcatValid;
    
    private String jenkinsUrl;

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
    }

    private TeamInfrastructure(String identifier) {
        super();
        this.identifier = Preconditions.checkNotNull(identifier);
    }

    /**
     * Team identifier like a trigram or a number.
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * The Jenkins server
     */
    @Nullable
    public Instance getJenkins() {
        return jenkins;
    }

    public Instance getNexus() {
        return nexus;
    }

    /**
     * The Tomcat server for the Development environment
     * @return
     */
    @Nullable
    public Instance getTomcatDev() {
        return tomcatDev;
    }

    public Instance getTomcatValid() {
        return tomcatValid;
    }
    public void setJenkins(Instance jenkins) {
        this.jenkins = jenkins;
    }
    public void setNexus(Instance nexus) {
        this.nexus = nexus;
    }
    public void setTomcatDev(Instance tomcatDev) {
        this.tomcatDev = tomcatDev;
    }

    public void setTomcatValid(Instance tomcatValid) {
        this.tomcatValid = tomcatValid;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this) //
                .add("id", identifier) //
                .add("jenkins", jenkins) //
                .add("jenkinsUrl", jenkinsUrl) //
                .add("tomcatDev", tomcatDev) //
                .add("tomcatValid", tomcatValid) //
                .add("nexus", nexus) //
                .toString();
    }
}