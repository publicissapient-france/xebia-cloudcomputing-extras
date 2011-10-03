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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import fr.xebia.cloud.cloudinit.FreemarkerUtils;

public class ContinuousDeliveryInfrastructureCreator {

    public static void main(String[] args) {

        boolean createNexus = true;
        boolean createJenkins = true;
        boolean createTomcatDev = true;
        boolean createTomcatValid = true;
        final ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator();
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        final Collection<String> teamIdentifiers = Lists.newArrayList("clc", "1");

        Callable<Instance> createNexusTask = new Callable<Instance>() {

            @Override
            public Instance call() throws Exception {
                try {
                    return creator.buildNexus();
                } catch (Exception e) {
                    logger.error("Exception creating nexus", e);
                    throw e;
                }
            }
        };
        if (createNexus) {
            executorService.submit(createNexusTask);
        }

        Callable<Map<String, Instance>> createJenkinsTask = new Callable<Map<String, Instance>>() {

            @Override
            public Map<String, Instance> call() throws Exception {
                try {
                    return creator.buildJenkins(teamIdentifiers);
                } catch (Exception e) {
                    logger.error("Exception creating jenkins", e);
                    throw e;
                }
            }
        };
        if (createJenkins) {
            executorService.submit(createJenkinsTask);
        }

        Callable<List<Instance>> createTomcatDevTask = new Callable<List<Instance>>() {

            @Override
            public List<Instance> call() throws Exception {
                try {
                    return creator.buildTomcatServers(teamIdentifiers, "dev", 1);
                } catch (Exception e) {
                    logger.error("Exception creating tomcat 'dev'", e);
                    throw e;
                }
            }
        };
        if (createTomcatDev) {
            executorService.submit(createTomcatDevTask);
        }
        Callable<List<Instance>> createTomcatValidTask = new Callable<List<Instance>>() {

            @Override
            public List<Instance> call() throws Exception {
                try {
                    return creator.buildTomcatServers(teamIdentifiers, "valid", 2);
                } catch (Exception e) {
                    logger.error("Exception creating valid", e);
                    throw e;
                }
            }
        };
        if (createTomcatValid) {
            executorService.submit(createTomcatValidTask);
        }
        executorService.shutdown();

        try {
            executorService.awaitTermination(10, TimeUnit.MINUTES);

            Collection<TeamInfrastructure> teamsInfrastructures = creator.discoverInfrasturctureTopology();

            creator.generateDocs(teamsInfrastructures, "/tmp/continuous-delivery/");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void generateDocs(Collection<TeamInfrastructure> teamsInfrastructures, String baseWikiFolder) throws IOException {
        File wikiBaseFolder = new File(baseWikiFolder);
        if (wikiBaseFolder.exists()) {
            logger.debug("Delete wiki folder {}", wikiBaseFolder);
            wikiBaseFolder.delete();
        }
        wikiBaseFolder.mkdirs();

        List<String> generatedWikiPageNames = Lists.newArrayList();

        for (TeamInfrastructure infrastructure : teamsInfrastructures) {
            try {
                Map<String, Object> rootMap = Maps.newHashMap();
                rootMap.put("infrastructure", infrastructure);
                String page = FreemarkerUtils.generate(rootMap, "/fr/xebia/workshop/continuousdelivery/continuous-delivery-lab.fmt");
                String wikiPageName = "ContinuousDeliveryWorkshopLab_" + infrastructure.getIdentifier();
                wikiPageName = wikiPageName.replace('-', '_');
                generatedWikiPageNames.add(wikiPageName);
                File wikiPageFile = new File(wikiBaseFolder, wikiPageName + ".wiki");
                Files.write(page, wikiPageFile, Charsets.UTF_8);
                logger.debug("Generated file {}", wikiPageFile);
            } catch (Exception e) {
                logger.error("Exception generating doc for {}", infrastructure, e);
            }

        }
        StringWriter indexPageStringWriter = new StringWriter();
        PrintWriter indexPageWriter = new PrintWriter(indexPageStringWriter);

        indexPageWriter.println("= Labs Per Team =");
        for (String wikiPage : generatedWikiPageNames) {
            indexPageWriter.println(" * [" + wikiPage + "]");
        }
        String indexPageName = "ContinuousDeliveryWorkshopLab";
        Files.write(indexPageStringWriter.toString(), new File(baseWikiFolder, indexPageName + ".wiki"), Charsets.UTF_8);

        System.out.println("WIKI PAGES GENERATED");
        System.out.println("====================");
        System.out.println();
        System.out.println("Base folder: " + baseWikiFolder);
        System.out.println("Index: " + indexPageName);
        System.out.println("Per team pages: \n\t" + Joiner.on("\n\t").join(generatedWikiPageNames));
        System.out.println("https://xebia-france.googlecode.com/svn/wiki");
    }

    protected AmazonEC2 ec2;

    private static final Logger logger = LoggerFactory.getLogger(ContinuousDeliveryInfrastructureCreator.class);

    public ContinuousDeliveryInfrastructureCreator() {
        AWSCredentials credentials = AmazonAwsUtils.loadAwsCredentials();
        ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
    }

    public Collection<TeamInfrastructure> discoverInfrasturctureTopology() {
        Filter filter = new Filter("tag:Workshop", Lists.newArrayList("continuous-delivery-workshop"));
        List<Reservation> reservations = ec2.describeInstances(new DescribeInstancesRequest().withFilters(filter)).getReservations();

        Iterable<Instance> instances = AmazonAwsUtils.toEc2Instances(reservations);

        Iterable<Instance> runningInstances = Iterables.filter(instances, AmazonAwsUtils.PREDICATE_RUNNING_OR_PENDING_INSTANCE);
        runningInstances = AmazonAwsUtils.awaitForEc2Instances(runningInstances, ec2);

        Map<String, Instance> runningInstancesByInstanceId = Maps.uniqueIndex(runningInstances,
                AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID);

        List<String> runningInstanceIds = Lists.newArrayList(Iterables.transform(runningInstances,
                AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID));

        List<TagDescription> tags = ec2.describeTags(new DescribeTagsRequest().withFilters(new Filter("resource-id", runningInstanceIds)))
                .getTags();
        Map<String, Map<String, String>> tagsByInstanceId = new MapMaker().makeComputingMap(new Function<String, Map<String, String>>() {
            @Override
            public Map<String, String> apply(String instanceId) {
                return Maps.newHashMap();
            }
        });

        for (TagDescription tag : tags) {
            tagsByInstanceId.get(tag.getResourceId()).put(tag.getKey(), tag.getValue());
        }

        Map<String, TeamInfrastructure> teamInfrastructureByTeamIdentifier = new MapMaker()
                .makeComputingMap(new Function<String, TeamInfrastructure>() {
                    @Override
                    public TeamInfrastructure apply(String teamIdentifier) {
                        return new TeamInfrastructure(teamIdentifier);
                    }
                });

        Instance nexusServer = null;

        for (Map.Entry<String, Map<String, String>> entry : tagsByInstanceId.entrySet()) {
            Map<String, String> instanceTags = entry.getValue();
            String instanceId = entry.getKey();
            Instance instance = runningInstancesByInstanceId.get(instanceId);
            String teamIdentifier = instanceTags.get("TeamIdentifier");

            if (teamIdentifier == null) {
                if (TeamInfrastructure.ROLE_NEXUS.equals(instanceTags.get("Role"))) {
                    nexusServer = instance;
                } else {
                    // not a per team server (e.g. Nexus server)
                }
            } else {

                TeamInfrastructure teamInfrastructure = teamInfrastructureByTeamIdentifier.get(teamIdentifier);
                teamInfrastructure.addInstance(instance, instanceTags);
            }
        }
        Collection<TeamInfrastructure> teamInfrastructures = teamInfrastructureByTeamIdentifier.values();
        for (TeamInfrastructure teamInfrastructure : teamInfrastructures) {
            teamInfrastructure.setNexus(nexusServer);
        }
        return teamInfrastructures;
    }

    /**
     * 
     * @param teamsIdentifiers
     * @return jenkins instances by teamIdentifier
     */
    public Map<String, Instance> buildJenkins(Collection<String> teamsIdentifiers) {
        logger.info("CREATE JENKINS/RUNDECK SERVERS");

        AmazonAwsUtils.terminateInstancesByRole(TeamInfrastructure.ROLE_JENKINS_RUNDECK, ec2);

        // CLOUD CONFIG
        String cloudConfigFilePath = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-jenkins-rundeck.txt";

        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        // CREATE EC2 INSTANCES
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.M1Small.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST) //
                .withMinCount(teamsIdentifiers.size()) //
                .withMaxCount(teamsIdentifiers.size()) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("continuous-delivery-workshop") //
                .withUserData(userData) //

        ;
        List<Instance> jenkinsInstances = AmazonAwsUtils.reliableEc2RunInstances(runInstancesRequest, ec2);

        Map<String, Instance> jenkinsInstancesByTeamIdentifier = Maps.newHashMap();
        // TAG EC2 INSTANCES
        {
            Iterator<String> teamsIdentifiersIterator = teamsIdentifiers.iterator();
            for (Instance jenkinsInstance : jenkinsInstances) {
                CreateTagsRequest createTagsRequest = new CreateTagsRequest();
                String identifier = teamsIdentifiersIterator.next();
                String serverName = "jenkins-" + identifier;
                logger.info("Tag {} - {}", serverName, jenkinsInstance.getInstanceId());
                createTagsRequest.withResources(jenkinsInstance.getInstanceId()) //
                        .withTags(//
                                new Tag("Name", serverName), //
                                new Tag("TeamIdentifier", identifier), //
                                new Tag("Workshop", "continuous-delivery-workshop"), //
                                new Tag("Role", TeamInfrastructure.ROLE_JENKINS_RUNDECK));
                try {
                    ec2.createTags(createTagsRequest);
                } catch (AmazonServiceException e) {
                    try {
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    ec2.createTags(createTagsRequest);
                }

                // wait for jenkins instance initialization
                jenkinsInstancesByTeamIdentifier.put(identifier, jenkinsInstance);

            }
            if (teamsIdentifiersIterator.hasNext()) {
                logger.warn("Unexpected remaining identifiers " + Lists.newArrayList(teamsIdentifiersIterator));
            }
        }

        // CONFIGURE JENKINS (CREATE JOBS, ETC)
        {
            for (Map.Entry<String, Instance> entry : jenkinsInstancesByTeamIdentifier.entrySet()) {
                Instance jenkins = entry.getValue();
                String teamIdentifier = entry.getKey();
                String jenkinsUrl = TeamInfrastructure.getJenkinsUrl(jenkins);
                logger.info("Configure jenkins (create jobs, etc) '{}' - {}", teamIdentifier, jenkins.getInstanceId());

                AmazonAwsUtils.awaitForHttpAvailability(jenkinsUrl);
                try {
                    PetclinicProjectInstance petClinicProjectInstance = new PetclinicProjectInstance(teamIdentifier);
                    new PetclinicJenkinsJobCreator(jenkinsUrl).create(petClinicProjectInstance).triggerBuild();
                } catch (Exception e) {
                    logger.warn("Silently skip " + e, e);
                }

            }
        }

        logger.info(
                "{} JENKINS SERVERS SUCCESSFULLY CREATED: {}",
                new Object[] { jenkinsInstances.size(),
                        Collections2.transform(jenkinsInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID) });

        return jenkinsInstancesByTeamIdentifier;
    }

    public Instance buildNexus() {
        logger.info("START CREATE NEXUS SERVER");

        // TERMINATE EXISTING NEXUS SERVERS IF EXIST
        AmazonAwsUtils.terminateInstancesByRole(TeamInfrastructure.ROLE_NEXUS, ec2);

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
        List<Instance> nexusInstances = AmazonAwsUtils.reliableEc2RunInstances(runInstancesRequest, ec2);
        Instance nexusInstance = Iterables.getOnlyElement(nexusInstances);

        // TAG NEXUS INSTANCES
        CreateTagsRequest createTagsRequest = new CreateTagsRequest();
        createTagsRequest.withResources(nexusInstance.getInstanceId()) //
                .withTags(//
                        new Tag("Name", "nexus"), //
                        new Tag("Workshop", "continuous-delivery-workshop"), //
                        new Tag("Role", TeamInfrastructure.ROLE_NEXUS));
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

        ec2.associateAddress(new AssociateAddressRequest(nexusInstance.getInstanceId(), publicIp));

        AmazonAwsUtils.awaitForHttpAvailability("http://" + publicIp + ":8081/nexus/");
        AmazonAwsUtils.awaitForHttpAvailability("http://nexus.xebia-tech-event.info:8081/nexus/");

        logger.info("1 NEXUS SERVER {} SUCCESSFULLY CREATED AND ASSOCIATED WITH {}: {}", new Object[] { nexusInstance.getInstanceId(),
                publicIp, nexusInstance });

        return nexusInstance;

    }

    public List<Instance> buildTomcatServers(Collection<String> teamIdentifiers, String environment, int numberOfInstances) {
        logger.info("CREATE TOMCAT '{}' SERVERS", environment);

        String role = TeamInfrastructure.ROLE_TOMCAT + "-" + environment;
        AmazonAwsUtils.terminateInstancesByRole(role, ec2);

        // CLOUD CONFIG
        String cloudConfigFilePath = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-tomcat.txt";

        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        // CREATE EC2 INSTANCES
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST) //
                .withMinCount(teamIdentifiers.size() * numberOfInstances) //
                .withMaxCount(teamIdentifiers.size() * numberOfInstances) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("continuous-delivery-workshop") //
                .withUserData(userData) //

        ;
        List<Instance> tomcatInstances = AmazonAwsUtils.reliableEc2RunInstances(runInstancesRequest, ec2);

        // TAG EC2 INSTANCES
        {
            Iterator<String> teamInfrastructureIterator = teamIdentifiers.iterator();
            Iterator<Instance> tomcatInstancesIterator = tomcatInstances.iterator();

            while (tomcatInstancesIterator.hasNext()) {
                String identifier = teamInfrastructureIterator.next();
                for (int i = 0; i < numberOfInstances; i++) {
                    Instance tomcatInstance = tomcatInstancesIterator.next();
                    // "AWS Error Code: InvalidInstanceID.NotFound, AWS Error Message: The instance ID 'i-d1638198' does not exist"
                    AmazonAwsUtils.awaitForEc2Instance(tomcatInstance, ec2);
                    String serverName = "tomcat-" + identifier + "-" + environment + "-" + (i + 1);
                    CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
                            .withResources(tomcatInstance.getInstanceId()) //
                            .withTags(//
                                    new Tag("Name", serverName), //
                                    new Tag("Workshop", "continuous-delivery-workshop"), //
                                    new Tag("TeamIdentifier", identifier), //
                                    new Tag("Role", role));

                    ec2.createTags(createTagsRequest);

                }
            }
            if (teamInfrastructureIterator.hasNext()) {
                logger.warn("Unexpected remaining identifiers " + Lists.newArrayList(teamInfrastructureIterator));
            }
        }
        logger.info(
                "{} TOMCAT '{}' SERVERS SUCCESSFULLY CREATED: {}",
                new Object[] { tomcatInstances.size(), environment,
                        Collections2.transform(tomcatInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID) });
        return tomcatInstances;

    }
}
