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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import fr.xebia.cloud.cloudinit.FreemarkerUtils;

public class ContinuousDeliveryInfrastructureCreator {

    private static final String ROLE_JENKINS_RUNDECK = "jenkins,rundeck";
    private static final String ROLE_TOMCAT = "tomcat";
    private static final String ROLE_NEXUS = "nexus";

    public static void main(String[] args) {

        ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator();
        try {
            Instance nexusServer = creator.buildNexus();
            System.out.println("NEXUS");
            System.out.println("------");
            System.out.println("login/password: deployment/deployment123");
            System.out.println(" * http://nexus.xebia-tech-event.info:8081/nexus/");
            System.out.println(" * http://" + nexusServer.getPublicDnsName() + "8081/nexus/" + " (server public dns name)");
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Collection<String> identifiers = Lists.newArrayList("clc");
            // Collections.transform() returns a 'transient' collection - two
            // consecutive calls to iterator re-instantiate different
            // TeamInfrastructure
            List<TeamInfrastructure> teamsInfrastructures = Lists.newArrayList(Collections2.transform(identifiers,
                    TeamInfrastructure.FUNCTION_TEAM_IDENTIFIER_TO_TEAM_INFRASTRUCTURE));
            creator.buildJenkins(teamsInfrastructures);
            creator.buildTomcatServers(teamsInfrastructures, "dev", 1);
            creator.buildTomcatServers(teamsInfrastructures, "valid", 2);

            for (TeamInfrastructure teamInfrastructure : teamsInfrastructures) {
                System.out.println("TEAM " + teamInfrastructure.getIdentifier());
                System.out.println("--------------------------");
                System.out.println(" * Jenkins: " + teamInfrastructure.getJenkinsUrl());
                System.out.println(" * Rundeck (login=admin, password=admin): " + teamInfrastructure.getRundeckUrl());
                System.out.println(" * Tomcat: ");
                System.out.println(" ** login/password: tomcat/tomcat");
                System.out.println(" ** dev: " + "http://" + teamInfrastructure.getDevTomcat().getPublicDnsName() + ":8080/");
                System.out.println(" ** valid1: " + "http://" + teamInfrastructure.getValidTomcat1().getPublicDnsName() + ":8080/");
                System.out.println(" ** valid2: " + "http://" + teamInfrastructure.getValidTomcat2().getPublicDnsName() + ":8080/");

                System.out.println(teamInfrastructure);
                System.out.println();

            }

            for (TeamInfrastructure infrastructure : teamsInfrastructures) {
                Map<String, Object> rootMap = Maps.newHashMap();
                rootMap.put("infrastructure", infrastructure);
                String page = FreemarkerUtils.generate(rootMap, "/fr/xebia/workshop/continuousdelivery/continuous-delivery-lab.fmt");
                System.out.println(page);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected AmazonEC2 ec2;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public ContinuousDeliveryInfrastructureCreator() {
        AWSCredentials credentials = AmazonAwsUtils.loadAwsCredentials();
        ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
    }

    public void buildJenkins(List<TeamInfrastructure> teamsInfrastructures) {
        logger.info("CREATE JENKINS/RUNDECK SERVERS");

        AmazonAwsUtils.terminateInstancesByRole(ROLE_JENKINS_RUNDECK, ec2);

        // CLOUD CONFIG
        String cloudConfigFilePath = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-jenkins-rundeck.txt";

        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        // CREATE EC2 INSTANCES
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.M1Small.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST) //
                .withMinCount(teamsInfrastructures.size()) //
                .withMaxCount(teamsInfrastructures.size()) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("continuous-delivery-workshop") //
                .withUserData(userData) //

        ;
        RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
        List<Instance> jenkinsInstances = runInstances.getReservation().getInstances();

        // TAG EC2 INSTANCES
        {
            Iterator<TeamInfrastructure> teamsInfrastructuresIterator = teamsInfrastructures.iterator();
            for (Instance jenkinsInstance : jenkinsInstances) {
                CreateTagsRequest createTagsRequest = new CreateTagsRequest();
                TeamInfrastructure teamInfrastructure = teamsInfrastructuresIterator.next();
                String identifier = teamInfrastructure.getIdentifier();
                createTagsRequest.withResources(jenkinsInstance.getInstanceId()) //
                        .withTags(//
                                new Tag("Name", "jenkins-" + identifier), //
                                new Tag("TeamIdentifier", identifier), //
                                new Tag("Workshop",  "continuous-delivery-workshop"), //
                                new Tag("Role", ROLE_JENKINS_RUNDECK));
                ec2.createTags(createTagsRequest);

                // wait for jenkins instance initialization
                jenkinsInstance = AmazonAwsUtils.awaitForEc2Instance(jenkinsInstance, ec2);

                teamInfrastructure.setJenkins(jenkinsInstance);
            }
            if (teamsInfrastructuresIterator.hasNext()) {
                logger.warn("Remaining identifiers " + Lists.newArrayList(teamsInfrastructuresIterator));
            }
        }

        // CONFIGURE JENKINS
        {
            for (TeamInfrastructure teamInfrastructure : teamsInfrastructures) {
                Instance jenkinsInstance = teamInfrastructure.getJenkins();
                String jenkinsUrl = "http://" + jenkinsInstance.getPublicDnsName() + ":8080/";
                teamInfrastructure.setJenkinsUrl(jenkinsUrl);

                AmazonAwsUtils.awaitForHttpAvailability(jenkinsUrl);
                try {
                    PetclinicProjectInstance petClinicProjectInstance = new PetclinicProjectInstance(teamInfrastructure.getIdentifier());
                    new PetclinicJenkinsJobCreator(jenkinsUrl).create(petClinicProjectInstance).warmUp();
                } catch (Exception e) {
                    logger.warn("Silently skip " + e, e);
                }

                String rundeckUrl = "http://" + jenkinsInstance.getPublicDnsName() + ":4440/";
                teamInfrastructure.setRundeckUrl(rundeckUrl);
            }
        }

        logger.info("Created jenkins servers {}", teamsInfrastructures);

    }

    public Instance buildNexus() {
        logger.info("CREATE NEXUS SERVER");

        // TERMINATE EXISTING NEXUS SERVERS IF EXIST
        AmazonAwsUtils.terminateInstancesByRole(ROLE_NEXUS, ec2);

        // CREATE NEXUS INSTANCE
        String cloudConfigFilePath = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-nexus.txt";
        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST) //
                .withMinCount(1) //
                .withMaxCount(1) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("continuous-delivery-workshop") //
                .withUserData(userData) //

        ;

        // START NEXUS INSTANCE
        RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
        Instance instance = Iterables.getOnlyElement(runInstances.getReservation().getInstances());

        // TAG NEXUS INSTANCES
        CreateTagsRequest createTagsRequest = new CreateTagsRequest();
        createTagsRequest.withResources(instance.getInstanceId()) //
                .withTags(//
                        new Tag("Name", "continuous-delivery-workshop-nexus"), //
                        new Tag("Role", ROLE_NEXUS));
        ec2.createTags(createTagsRequest);

        String publicIp = "46.137.168.248";

        // ASSOCIATE NEXUS INSTANCE WITH PUBLIC IP
        Address address = Iterables.getOnlyElement(ec2.describeAddresses(new DescribeAddressesRequest().withPublicIps(publicIp))
                .getAddresses());
        String currentlyAssociatedId = address.getInstanceId();
        if (currentlyAssociatedId == null) {
            logger.debug("Public IP {} is not currently associated with an instance", publicIp);
        } else {
            logger.info("Public IP {} is currently associated instance '{}'. Disassociate it first.", publicIp, currentlyAssociatedId);
            ec2.disassociateAddress(new DisassociateAddressRequest(publicIp));
        }

        instance = AmazonAwsUtils.awaitForEc2Instance(instance, ec2);
        ec2.associateAddress(new AssociateAddressRequest(instance.getInstanceId(), publicIp));

        logger.info("Created nexus server {} associated with {}", instance, publicIp);

        AmazonAwsUtils.awaitForHttpAvailability("http://" + publicIp + ":8081/nexus/");
        AmazonAwsUtils.awaitForHttpAvailability("http://nexus.xebia-tech-event.info:8081/nexus/");
        return instance;

    }

    public void buildTomcatServers(List<TeamInfrastructure> teamInfrastructures, String environment, int numberOfInstances) {
        logger.info("CREATE TOMCAT '{}' SERVERS", environment);

        String role = ROLE_TOMCAT + "-" + environment;
        AmazonAwsUtils.terminateInstancesByRole(role, ec2);

        // CLOUD CONFIG
        String cloudConfigFilePath = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-tomcat.txt";

        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        // CREATE EC2 INSTANCES
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST) //
                .withMinCount(teamInfrastructures.size() * numberOfInstances) //
                .withMaxCount(teamInfrastructures.size() * numberOfInstances) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("continuous-delivery-workshop") //
                .withUserData(userData) //

        ;
        RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
        List<Instance> tomcatInstances = runInstances.getReservation().getInstances();

        // TAG EC2 INSTANCES
        {
            Iterator<TeamInfrastructure> teamInfrastructureIterator = teamInfrastructures.iterator();
            Iterator<Instance> tomcatInstancesIterator = tomcatInstances.iterator();

            while (tomcatInstancesIterator.hasNext()) {
                TeamInfrastructure teamInfrastructure = teamInfrastructureIterator.next();
                for (int i = 0; i < numberOfInstances; i++) {
                    Instance tomcatInstance = tomcatInstancesIterator.next();
                    String identifier = teamInfrastructure.getIdentifier();
                    CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
                            .withResources(tomcatInstance.getInstanceId()) //
                            .withTags(//
                                    new Tag("Name", "tomcat-" + identifier + "-" + environment + "-" + (i+1)), //
                                    new Tag("Workshop",  "continuous-delivery-workshop"), //
                                    new Tag("TeamIdentifier", identifier), //
                                    new Tag("Role", role));
                    ec2.createTags(createTagsRequest);
                    tomcatInstance = AmazonAwsUtils.awaitForEc2Instance(tomcatInstance, ec2);
                    teamInfrastructure.addTomcat(environment, tomcatInstance);
                }
            }
            if (teamInfrastructureIterator.hasNext()) {
                logger.warn("Remaining identifiers " + Lists.newArrayList(teamInfrastructureIterator));
            }
        }
        AmazonAwsUtils.awaitForEc2Instances(tomcatInstances, ec2);
        logger.info("Created tomcat '{}' servers {}", environment, tomcatInstances);

    }
}
