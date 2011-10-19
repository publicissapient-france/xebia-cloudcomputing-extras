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

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.io.Files;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import fr.xebia.cloud.cloudinit.FreemarkerUtils;
import fr.xebia.workshop.git.*;

public class ContinuousDeliveryInfrastructureCreator {

    private static final String KEY_PAIR_NAME = "continuous-delivery-workshop";
    
    private static final String TEMPLATE_ROOT_PATH = "/fr/xebia/workshop/continuousdelivery/lab/";
    
    private static final String SETUP_TEMPLATE_NAME="setup";
    
    private static final List<String> TEMPLATE_LAB_NAMES = Arrays.asList(
    		"apache-tomcat-maven-plugin",
    		"jenkins-remote-ssh",
    		"rundeck",
    		"deployit");

    public static void main(String[] args) {
        final WorkshopInfrastructure workshopInfrastructure = WorkshopInfrastructure.create()
                .withGithubGuestInfo("xebia-guest", "xebia-guest", "xebia42.*") // "xebia-continuous-delivery-tech-event" / "1645faface"
                .withNexusPublicIp("46.137.168.248")
                .withNexusDomainName("nexus.xebia-tech-event.info")
                .build();

        boolean createNexus = false;
        boolean createRepositories = false;
        boolean createJenkins = true;
        boolean createTomcatDev = true;
        boolean createTomcatValid = true;

        final ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator();

        //Check for Key in classpath : prevent to launch instances if not present
        InputStream keyFile = Thread.currentThread().getContextClassLoader().getResourceAsStream(KEY_PAIR_NAME+".pem");
        Preconditions.checkState(keyFile != null, "File '" + KEY_PAIR_NAME + ".pem' NOT found in the classpath");

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        
        final Collection<String> teamIdentifiers = Arrays.asList("9");

        Callable<Instance> createNexusTask = new Callable<Instance>() {

            @Override
            public Instance call() throws Exception {
                try {
                    return creator.buildNexus(workshopInfrastructure);
                } catch (Exception e) {
                    logger.error("Exception creating nexus", e);
                    throw e;
                }
            }
        };
        if (createNexus) {
            executorService.submit(createNexusTask);
        }

        Runnable createRepositoriesTask = new Runnable() {
            @Override
            public void run() {
                try {
                     creator.deleteGithubRepositories(workshopInfrastructure, teamIdentifiers);
                    creator.buildGithubRepositories(workshopInfrastructure, teamIdentifiers);
                } catch (Exception e) {
                    logger.error("Exception creating Github repositories", e);
                    throw new RuntimeException(e);
                }
            }
        };
        if (createRepositories) {
            executorService.submit(createRepositoriesTask);
        }

        Callable<Map<String, Instance>> createJenkinsTask = new Callable<Map<String, Instance>>() {

            @Override
            public Map<String, Instance> call() throws Exception {
                try {
                    return creator.buildJenkins(workshopInfrastructure, teamIdentifiers);
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

            Collection<TeamInfrastructure> teamsInfrastructures = creator.discoverInfrastructureTopology(workshopInfrastructure);

            creator.generateDocs(teamsInfrastructures, "/tmp/continuous-delivery/");
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(0);
    }

    private static Collection<String> createIdentifiersForNumberOfTeams(int teamCount) {
        Collection<String> teamIdentifiers = new ArrayList<String>(teamCount);
        for (int i = 1; i <= teamCount; i++) {
            teamIdentifiers.add(String.valueOf(i));
        }
        return teamIdentifiers;
    }

    public void generateDocs(Collection<TeamInfrastructure> teamsInfrastructures, String baseWikiFolder) throws IOException {
        File wikiBaseFolder = new File(baseWikiFolder);
        if (wikiBaseFolder.exists()) {
            logger.debug("Delete wiki folder {}", wikiBaseFolder);
            wikiBaseFolder.delete();
        }
        wikiBaseFolder.mkdirs();

        List<String> generatedWikiPageNames = Lists.newArrayList();
        List<String> setupGeneratedWikiPageNames = Lists.newArrayList();
        HashMap<TeamInfrastructure,List<String>> teamsPages = Maps.newHashMap();
        

        for (TeamInfrastructure infrastructure : teamsInfrastructures) {
        	List<String> generatedForTeam = Lists.newArrayList();
        	for(String template:TEMPLATE_LAB_NAMES){
        		try {
                    Map<String, Object> rootMap = Maps.newHashMap();
                    rootMap.put("infrastructure", infrastructure);
                    String templatePath = TEMPLATE_ROOT_PATH+ template+".fmt";
                    rootMap.put("generator", "This page has been generaterd by '{{{" + getClass() + "}}}' with template '{{{" + templatePath + "}}}' on the " + new DateTime());
                    String page = FreemarkerUtils.generate(rootMap, templatePath);
                    String wikiPageName = "ContinuousDeliveryWorkshopLab_" + infrastructure.getIdentifier()+"_"+template;
                    wikiPageName = wikiPageName.replace('-', '_');
                    generatedWikiPageNames.add(wikiPageName);
                    generatedForTeam.add(wikiPageName);
                    File wikiPageFile = new File(wikiBaseFolder, wikiPageName + ".wiki");
                    Files.write(page, wikiPageFile, Charsets.UTF_8);
                    logger.debug("Generated file {}", wikiPageFile);
                } catch (Exception e) {
                    logger.error("Exception generating doc for {}", infrastructure, e);
                }	
        	}
        	//SETUP WITH LINKS TO DIFFERENT LABS WIKI PAGE
        	try {
                Map<String, Object> rootMap = Maps.newHashMap();
                rootMap.put("infrastructure", infrastructure);
                String templatePath = TEMPLATE_ROOT_PATH+ SETUP_TEMPLATE_NAME+".fmt";
                rootMap.put("generator", "This page has been generaterd by '{{{" + getClass() + "}}}' with template '{{{" + templatePath + "}}}' on the " + new DateTime());
                rootMap.put("generatedWikiPageNames",generatedForTeam);
                String page = FreemarkerUtils.generate(rootMap, templatePath);
                String wikiPageName = "ContinuousDeliveryWorkshopLab_" + infrastructure.getIdentifier()+"_"+SETUP_TEMPLATE_NAME;
                wikiPageName = wikiPageName.replace('-', '_');
                
                File wikiPageFile = new File(wikiBaseFolder, wikiPageName + ".wiki");
                Files.write(page, wikiPageFile, Charsets.UTF_8);
                
                generatedWikiPageNames.add(wikiPageName);
                setupGeneratedWikiPageNames.add(wikiPageName);
                
                logger.debug("Generated file {}", wikiPageFile);
            } catch (Exception e) {
                logger.error("Exception generating doc for {}", infrastructure, e);
            }
            teamsPages.put(infrastructure,generatedForTeam);
        }
        
        
        
        StringWriter indexPageStringWriter = new StringWriter();
        PrintWriter indexPageWriter = new PrintWriter(indexPageStringWriter);

        indexPageWriter.println("= Labs Per Team =");
        List<TeamInfrastructure> teams = new ArrayList<TeamInfrastructure>(teamsPages.keySet());
        Collections.sort(teams);
        for(TeamInfrastructure teamInfrastructure : teams){
            indexPageWriter.println(" # [ContinuousDeliveryWorkshopLab_"
                    + teamInfrastructure.getIdentifier()+"_"+SETUP_TEMPLATE_NAME + "]");
            for (String wikiPage : teamsPages.get(teamInfrastructure)) {
                indexPageWriter.println("  * [" + wikiPage + "]");
            }
        }
        /*
        for (String wikiPage : setupGeneratedWikiPageNames) {
            indexPageWriter.println(" # [" + wikiPage + "]");
        }
        */
        String indexPageName = "ContinuousDeliveryWorkshopLab";
        Files.write(indexPageStringWriter.toString(), new File(baseWikiFolder, indexPageName + ".wiki"), Charsets.UTF_8);

        System.out.println("GENERATED WIKI PAGES TO BE COMMITTED IN XEBIA-FRANCE GOOGLE CODE");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Base folder: " + baseWikiFolder);
        System.out.println("All the files in " + baseWikiFolder + " must be committed in https://xebia-france.googlecode.com/svn/wiki");
        System.out.println("Index page: " + indexPageName);
        System.out.println("Per team pages: \n\t" + Joiner.on("\n\t").join(generatedWikiPageNames));
    }

    protected AmazonEC2 ec2;

    private static final Logger logger = LoggerFactory.getLogger(ContinuousDeliveryInfrastructureCreator.class);

    public ContinuousDeliveryInfrastructureCreator() {
        AWSCredentials credentials = AmazonAwsUtils.loadAwsCredentials();
        ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
    }

    public Collection<TeamInfrastructure> discoverInfrastructureTopology(final WorkshopInfrastructure workshopInfrastructure) {
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
                        return new TeamInfrastructure(workshopInfrastructure, teamIdentifier);
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

    public void buildGithubRepositories(WorkshopInfrastructure infra, Iterable<String> teamIdentifiers) {
        final GithubRepositoriesCreator creator = new GithubRepositoriesCreator()
                .fromGithubRepository("http://github.com/xebia-france-training/xebia-petclinic-lite.git")
                .onAccountName(infra.getGithubGuestAccountName())
                .withAccessType(GithubCreateRepositoryRequest.AccessType.HTTP)
                .withGithubLoginPassword(infra.getGithubGuestAccountUsername(), infra.getGithubGuestAccountPassword());

        for (String team : teamIdentifiers) {
            creator.addGithubCreateRepositoryRequest(new GithubCreateRepositoryRequest()
                    .toRepositoryName("xebia-petclinic-lite-" + team)
                    .withGitRepositoryHandler(new UpdatePomFileAndCommit(team)));
        }
        creator.createRepositories();
    }

    public void deleteGithubRepositories(WorkshopInfrastructure infra, Iterable<String> teamIdentifiers) {
        final GithubRepositoriesDeleter deleter = new GithubRepositoriesDeleter()
                .withGithubLoginPassword(infra.getGithubGuestAccountUsername(), infra.getGithubGuestAccountPassword());

        for (String team : teamIdentifiers) {
            deleter.githubRepositoryName("xebia-petclinic-lite-" + team);
        }
        deleter.deleteRepositories();
    }

    /**
     * @param teamsIdentifiers
     * @return jenkins instances by teamIdentifier
     */
    public Map<String, Instance> buildJenkins(WorkshopInfrastructure workshopInfrastructure, Collection<String> teamsIdentifiers) {
        logger.info("CREATE JENKINS/RUNDECK SERVERS");

        for(String teamIdentifier:teamsIdentifiers){
        	AmazonAwsUtils.terminateInstancesByRoleAndTeam(TeamInfrastructure.ROLE_JENKINS_RUNDECK,teamIdentifier, ec2);	
        }

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
                .withKeyName(KEY_PAIR_NAME) //
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
                if (jenkinsUrl == null) {
                    continue;
                }

                logger.info("Configure jenkins (create jobs, etc) '{}' - {}", teamIdentifier, jenkins.getInstanceId());

                try {
                    AmazonAwsUtils.awaitForHttpAvailability(jenkinsUrl);
                    PetclinicProjectInstance petClinicProjectInstance = new PetclinicProjectInstance(workshopInfrastructure.getGithubGuestAccountName(), teamIdentifier);
                    new PetclinicJenkinsJobCreator(jenkinsUrl).create(petClinicProjectInstance).triggerBuild();
                } catch (Exception e) {
                    logger.warn("Silently skip " + e, e);
                }

            }
        }

        //Test Availabily of Deployit
        {
            for (Map.Entry<String, Instance> entry : jenkinsInstancesByTeamIdentifier.entrySet()) {
                Instance deployit = entry.getValue();
                String teamIdentifier = entry.getKey();
                String deployitUrl = TeamInfrastructure.getDeployitUrl(deployit);
                if (deployitUrl == null) {
                    continue;
                }

                logger.info("Configure deployit '{}' - {}", teamIdentifier, deployit.getInstanceId());

                try {
                    AmazonAwsUtils.awaitForHttpAvailability(deployitUrl);
                } catch (Exception e) {
                    logger.warn("Silently skip " + e, e);
                }

            }
        }


        logger.info(
                "{} JENKINS SERVERS SUCCESSFULLY CREATED: {}",
                new Object[]{jenkinsInstances.size(),
                        Collections2.transform(jenkinsInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID)});

        return jenkinsInstancesByTeamIdentifier;
    }

    public Instance buildNexus(WorkshopInfrastructure workshopInfrastructure) throws InterruptedException {
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
                .withKeyName(KEY_PAIR_NAME) //
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

        // first waits for Nexus availability, otherwise the following elastic IP assignment will break its installation
        waitForNexusAvailability(nexusInstance);

        final String publicIp = workshopInfrastructure.getNexusPublicIp();

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

        try {
            AmazonAwsUtils.awaitForHttpAvailability(workshopInfrastructure.getNexusUrlWithIp());
            AmazonAwsUtils.awaitForHttpAvailability(workshopInfrastructure.getNexusUrlWithDomainName());
        } catch (Exception e) {
            logger.warn("Silently skipped " + e, e);
        }

        logger.info("1 NEXUS SERVER {} SUCCESSFULLY CREATED AND ASSOCIATED WITH {}: {}", new Object[]{nexusInstance.getInstanceId(),
                publicIp, nexusInstance});

        return nexusInstance;

    }

    private void waitForNexusAvailability(Instance nexusInstance) throws InterruptedException {
        int maxTries = 3;
        int tries = 0;
        boolean success = false;
        while (!success && tries < maxTries) {
            try {
                AmazonAwsUtils.awaitForHttpAvailability("http://" + nexusInstance.getPublicIpAddress() + ":8081/nexus/");
                success = true;
            } catch (Exception e) {
                logger.warn("Silently skipped " + e, e);
                tries++;
                Thread.sleep(3000);
            }
        }
    }

    public List<Instance> buildTomcatServers(Collection<String> teamIdentifiers, String environment, int numberOfInstances) {
        logger.info("CREATE TOMCAT '{}' SERVERS", environment);

        String role = TeamInfrastructure.ROLE_TOMCAT + "-" + environment;
        for(String teamIdentifier:teamIdentifiers){
        	AmazonAwsUtils.terminateInstancesByRoleAndTeam(role,teamIdentifier, ec2);	
        }
        

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
                .withKeyName(KEY_PAIR_NAME)
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
                new Object[]{tomcatInstances.size(), environment,
                        Collections2.transform(tomcatInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID)});
        return tomcatInstances;

    }
}
