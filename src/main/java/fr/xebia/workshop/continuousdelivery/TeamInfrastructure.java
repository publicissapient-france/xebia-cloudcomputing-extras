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

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * <p>
 * Team infrastructure for the lab.
 * </p>
 * 
 */
public class TeamInfrastructure {
    public static final Function<String, TeamInfrastructure> FUNCTION_TEAM_IDENTIFIER_TO_TEAM_INFRASTRUCTURE = new Function<String, TeamInfrastructure>() {

        @Override
        public TeamInfrastructure apply(String teamIdentifier) {
            return new TeamInfrastructure(teamIdentifier);
        }
    };

    /**
     * The Jenkins server url (e.g. http://my-ec2-server:8080/) or
     * <code>null</code> if the given ec2 instance is <code>null</code>.
     * 
     * @throws IllegalStateException
     *             if the given jenkins instance is not initialized and has a
     *             <code>null</code> 'publicDnsName'.
     */
    @Nullable
    public static String getJenkinsUrl(@Nullable Instance jenkins) throws IllegalStateException {
        if (jenkins == null) {
            return null;
        }
        Preconditions.checkState(jenkins.getPublicDnsName() != null && !jenkins.getPublicDnsName().isEmpty(),
                "Given jenkins is not yet initialized, it publicDnsName is null: %s", jenkins);
        return "http://" + jenkins.getPublicDnsName() + ":8080/";
    }

    /**
     * The Tomcat server url (e.g. http://my-ec2-server:8080/) or
     * <code>null</code> if the given ec2 instance is <code>null</code>.
     * 
     * @throws IllegalStateException
     *             if the given tomcat instance is not initialized and has a
     *             <code>null</code> 'publicDnsName'.
     */
    @Nullable
    public static String getTomcatUrl(@Nullable Instance tomcat) throws IllegalStateException {
        if (tomcat == null) {
            return null;
        }
        Preconditions.checkState(tomcat.getPublicDnsName() != null && !tomcat.getPublicDnsName().isEmpty(),
                "Given tomcat is not yet initialized, it publicDnsName is null: %s", tomcat);
        return "http://" + tomcat.getPublicDnsName() + ":8080/";
    }

    /**
     * The Rundeck server url (e.g. http://my-ec2-server:4440/) or
     * <code>null</code> if the given ec2 instance is <code>null</code>.
     * 
     * @throws IllegalStateException
     *             if the given rundeck instance is not initialized and has a
     *             <code>null</code> 'publicDnsName'.
     */
    @Nullable
    public static String getRundeckUrl(Instance rundeck) {
        Preconditions.checkState(rundeck.getPublicDnsName() != null, "Given rundeck is not yet initialized, it publicDnsNAme is null: %s",
                rundeck);
        return "http://" + rundeck.getPublicDnsName() + ":4440/";
    }

    static final String ROLE_JENKINS_RUNDECK = "jenkins,rundeck";

    static final String ROLE_NEXUS = "nexus";

    static final String ROLE_TOMCAT = "tomcat";

    private Instance devTomcat;

    private String devTomcatName = "#devTomcat#";

    private final String identifier;

    private Instance jenkins;

    private String jenkinsName = "#jenkins#";

    private Instance nexus;

    private Instance rundeck;

    private String rundeckName = "#rundeck#";

    private Instance validTomcat1;

    private String validTomcat1Name = "#validTomcat1#";

    private Instance validTomcat2;

    private String validTomcat2Name = "#validTomcat2#";

    public TeamInfrastructure(String identifier) {
        super();
        this.identifier = Preconditions.checkNotNull(identifier);
    }

    public void addInstance(Instance instance, Map<String, String> tags) {
        String name = tags.get("Name");
        String role = tags.get("Role");

        if (ROLE_JENKINS_RUNDECK.equals(role)) {
            this.jenkins = instance;
            this.rundeck = instance;
            this.jenkinsName = name;
            this.rundeckName = name;
        } else if (role.startsWith((ROLE_TOMCAT + "-"))) {
            String environment = role.substring((ROLE_TOMCAT + "-").length());
            if ("dev".equals(environment)) {
                devTomcat = instance;
                devTomcatName = name;
            } else if ("valid".equals(environment)) {
                if (validTomcat1 == null) {
                    validTomcat1 = instance;
                    validTomcat1Name = name;
                } else if (validTomcat2 == null) {
                    validTomcat2 = instance;
                    validTomcat2Name = name;
                } else {
                    throw new IllegalStateException("Valid tomcats already set");
                }
            } else {
                throw new IllegalStateException("Dev tomcat already set");
            }
        }
    }

    /**
     * 
     * FIXME cleanup this dirty code (CLC)
     * 
     * @param environment
     *            like "dev" or "valid"
     */
    public void addTomcat(@Nonnull String environment, @Nonnull Instance tomcatInstance, @Nonnull String tomcatServerName) {
        if ("dev".equals(environment)) {
            devTomcat = tomcatInstance;
            devTomcatName = tomcatServerName;
        } else if ("valid".equals(environment)) {
            if (validTomcat1 == null) {
                validTomcat1 = tomcatInstance;
                validTomcat1Name = tomcatServerName;
            } else if (validTomcat2 == null) {
                validTomcat2 = tomcatInstance;
                validTomcat2Name = tomcatServerName;
            } else {
                throw new IllegalStateException("Valid tomcats already set");
            }
        } else {
            throw new IllegalStateException("Dev tomcat already set");
        }
    }

    /**
     * Constant: "http://nexus.xebia-tech-event.info:8081/nexus/"
     */
    public String getNexusUrl() {
        return "http://nexus.xebia-tech-event.info:8081/nexus/";
    }

    /**
     * The Amazon EC2 instance of the Tomcat dev server
     */
    public Instance getDevTomcat() {
        return devTomcat;
    }

    /**
     * The Amazon EC2 Name of the Tomcat dev server
     */
    public String getDevTomcatName() {
        return devTomcatName;
    }

    /**
     * Team identifier like a trigram or a number ("clc", "team-1", etc).
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * The Amazon EC2 instance of the Jenkins server
     */
    public Instance getJenkins() {
        return jenkins;
    }

    /**
     * Amazon EC2 Name of the Jenkins server
     */
    public String getJenkinsName() {
        return jenkinsName;
    }

    /**
     * <p>
     * The GitHub repository url of the team. If the identifier is blank, the
     * default xebia-petclinic project repository url is returned.
     * </p>
     * <p>
     * e.g. "https://github.com/xebia-france-training/xebia-petclinic-" +
     * identifier
     * </p>
     */
    @Nonnull
    public String getGithubRepositoryUrl() {
        String xebiaPetclinicRepositoryUrl = "https://github.com/xebia-france-training/xebia-petclinic";
        if (!Strings.isNullOrEmpty(identifier)) {
            xebiaPetclinicRepositoryUrl += "-" + identifier;
        }
        return xebiaPetclinicRepositoryUrl;
    }

    /**
     * URL of the jenkins server like "http://my-server:8080/" or
     * <code>null</code> if the underlying jenkins instance is <code>null</code>
     * .
     * 
     * @throws IllegalStateException
     * 
     */
    public String getJenkinsUrl() throws IllegalStateException {
        return TeamInfrastructure.getJenkinsUrl(jenkins);
    }

    /**
     * Amazon EC2 instance of the Nexus server
     */
    public Instance getNexus() {
        return nexus;
    }

    /**
     * <p>
     * The Maven project groupId. If the identifier is blank, the default
     * project groupId "fr.xebia.demo.petclinic" is returned.
     * </p>
     * <p>
     * e.g. "fr.xebia.demo.petclinic-" + identifier
     * </p>
     */
    @Nonnull
    public String getProjectMavenGroupId() {
        String projectMavenGroupId = "fr.xebia.demo.petclinic";
        if (!Strings.isNullOrEmpty(identifier)) {
            projectMavenGroupId += "-" + identifier;
        }

        return projectMavenGroupId;
    }

    /**
     * Amazon EC2 instance of the Nexus server
     */
    public Instance getRundeck() {
        return rundeck;
    }

    /**
     * Amazon EC2 name of the Nexus server
     */
    public String getRundeckName() {
        return rundeckName;
    }

    /**
     * URL of the rundeck server like "http://my-server:4440/" or
     * <code>null</code> if underlying rundeck is <code>null</code>.
     * 
     * @throws IllegalStateException
     *             the underlying jenkins instance is <code>null</code>.
     */
    @Nullable
    public String getRundeckUrl() throws IllegalStateException {
        return getRundeckUrl(rundeck);
    }

    /**
     * Amazon EC2 instance of the Valid Tomcat 1 server
     */
    public Instance getValidTomcat1() {
        return validTomcat1;
    }

    /**
     * Amazon EC2 name of the Valid Tomcat 1 server
     */
    public String getValidTomcat1Name() {
        return validTomcat1Name;
    }

    /**
     * Amazon EC2 instance of the Valid Tomcat 2 server
     */
    public Instance getValidTomcat2() {
        return validTomcat2;
    }

    /**
     * The Tomcat server url (e.g. http://my-ec2-server:8080/) or
     * <code>null</code> if the underlying ec2 instance is <code>null</code>.
     * 
     * @throws IllegalStateException
     *             if the underlying tomcat instance is not initialized and has a
     *             <code>null</code> 'publicDnsName'.
     */
    @Nullable
    public String getValidTomcat2Url() throws IllegalStateException {
        return TeamInfrastructure.getTomcatUrl(validTomcat2);
    }
    
    /**
     * The Tomcat server url (e.g. http://my-ec2-server:8080/) or
     * <code>null</code> if the underlying ec2 instance is <code>null</code>.
     * 
     * @throws IllegalStateException
     *             if the underlying tomcat instance is not initialized and has a
     *             <code>null</code> 'publicDnsName'.
     */
    @Nullable
    public String getValidTomcat1Url() throws IllegalStateException {
        return TeamInfrastructure.getTomcatUrl(validTomcat1);
    }
    
    /**
     * The Tomcat server url (e.g. http://my-ec2-server:8080/) or
     * <code>null</code> if the underlying ec2 instance is <code>null</code>.
     * 
     * @throws IllegalStateException
     *             if the underlying tomcat instance is not initialized and has a
     *             <code>null</code> 'publicDnsName'.
     */
    @Nullable
    public String getDevTomcatUrl() throws IllegalStateException {
        return TeamInfrastructure.getTomcatUrl(devTomcat);
    }

    /**
     * Amazon EC2 name of the Valid Tomcat 2 server
     */
    public String getValidTomcat2Name() {
        return validTomcat2Name;
    }

    public void setDevTomcat(Instance devTomcat) {
        this.devTomcat = devTomcat;
    }

    public void setDevTomcatName(String devTomcatName) {
        this.devTomcatName = devTomcatName;
    }

    public void setJenkins(Instance jenkins) {
        this.jenkins = jenkins;
    }

    public void setJenkinsName(String jenkinsName) {
        this.jenkinsName = jenkinsName;
    }

    public void setNexus(Instance nexus) {
        this.nexus = nexus;
    }

    public void setRundeck(Instance rundeck) {
        this.rundeck = rundeck;
    }

    public void setRundeckName(String rundeckName) {
        this.rundeckName = rundeckName;
    }

    public void setValidTomcat1(Instance validTomcat1) {
        this.validTomcat1 = validTomcat1;
    }

    public void setValidTomcat1Name(String validTomcat1Name) {
        this.validTomcat1Name = validTomcat1Name;
    }

    public void setValidTomcat2(Instance validTomcat2) {
        this.validTomcat2 = validTomcat2;
    }

    public void setValidTomcat2Name(String validTomcat2Name) {
        this.validTomcat2Name = validTomcat2Name;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this) //
                .add("id", identifier) //
                .add(jenkinsName, jenkins) //
                .add(devTomcatName, devTomcat) //
                .add(validTomcat1Name, validTomcat1) //
                .add(validTomcat2Name, validTomcat2) //
                .add("nexus", nexus) //
                .toString();
    }
}