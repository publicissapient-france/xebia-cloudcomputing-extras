package fr.xebia.workshop.continuousdelivery;

import static com.google.common.collect.Lists.transform;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Maps;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;

public class CreateJenkinsInstances extends InfrastructureCreationStep {

    private static final String CLOUD_CONFIG_FILE_PATH = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-jenkins-rundeck.txt";

    @Override
    public void execute(AmazonEC2 ec2, WorkshopInfrastructure infra) throws Exception {
        logger.info("CREATING JENKINS/RUNDECK SERVERS");

        AmazonAwsUtils.terminateInstancesByRoleAndTeam(TeamInfrastructure.ROLE_JENKINS_RUNDECK, infra.getTeamIdentifiers(), ec2);

        List<Instance> jenkinsInstances = createNewInstances(ec2, infra);

        Map<String, Instance> jenkinsInstancesByTeamId = associateInstancesToTeamIds(jenkinsInstances, infra.getTeamIdentifiers());

        tagInstances(jenkinsInstancesByTeamId, ec2);

        createJenkinsJobs(jenkinsInstancesByTeamId, infra);

        awaitForDeployitAvailability(jenkinsInstancesByTeamId);

        logger.info("{} JENKINS SERVERS SUCCESSFULLY CREATED: {}", jenkinsInstances.size(),
                transform(jenkinsInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID));
    }

    private List<Instance> createNewInstances(AmazonEC2 ec2, WorkshopInfrastructure infra) {
        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(CLOUD_CONFIG_FILE_PATH).buildBase64UserData();

        int instanceCount = infra.getTeamCount();

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.M1Small.toString()) //
                .withImageId(WORKSHOP_IMAGE_ID) //
                .withMinCount(instanceCount) //
                .withMaxCount(instanceCount) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName(infra.getKeyPairName()) //
                .withUserData(userData);

        List<Instance> instances = AmazonAwsUtils.reliableEc2RunInstances(runInstancesRequest, ec2);

        if (instances.size() != instanceCount) {
            logger.warn("Unexpected number of instances created: {} instead of {} expected", instances.size(), instanceCount);
        }

        return instances;
    }

    protected Map<String, Instance> associateInstancesToTeamIds(List<Instance> instances, List<String> teamIdentifiers) {
        Map<String, Instance> instancesByTeamId = Maps.newHashMap();

        Iterator<String> teamsIdIterator = teamIdentifiers.iterator();
        for (Instance instance : instances) {
            instancesByTeamId.put(teamsIdIterator.next(), instance);
        }

        return instancesByTeamId;
    }

    protected void tagInstances(Map<String, Instance> instancesByTeamId, AmazonEC2 ec2) {
        for (Entry<String, Instance> idAndInstance : instancesByTeamId.entrySet()) {
            String identifier = idAndInstance.getKey();
            Instance instance = idAndInstance.getValue();

            String serverName = "jenkins-" + identifier;
            logger.info("Tagging {} - {}", serverName, instance.getInstanceId());

            CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
                    .withResources(instance.getInstanceId()) //
                    .withTags(//
                            new Tag("Name", serverName), //
                            new Tag("TeamIdentifier", identifier), //
                            new Tag("Workshop", "continuous-delivery-workshop"), //
                            new Tag("Role", TeamInfrastructure.ROLE_JENKINS_RUNDECK));

            createTags(instance, createTagsRequest, ec2);
        }
    }

    private void createJenkinsJobs(Map<String, Instance> jenkinsInstancesByTeamId, WorkshopInfrastructure infra) {
        for (Map.Entry<String, Instance> idAndInstance : jenkinsInstancesByTeamId.entrySet()) {
            Instance instance = idAndInstance.getValue();
            String teamIdentifier = idAndInstance.getKey();

            String jenkinsUrl = TeamInfrastructure.getJenkinsUrl(instance);
            if (jenkinsUrl == null) {
                continue;
            }

            logger.info("Configuring Jenkins (creating jobs, etc) '{}' - {}", teamIdentifier, instance.getInstanceId());

            try {
                AmazonAwsUtils.awaitForHttpAvailability(jenkinsUrl);
                PetclinicJobInstance petclinicJobInstance = new PetclinicJobInstance(infra, teamIdentifier);
                new PetclinicJenkinsJobCreator(jenkinsUrl).create(petclinicJobInstance).triggerBuild();
            } catch (Exception e) {
                logger.warn("Silently skipped " + e, e);
            }
        }
    }

    private void awaitForDeployitAvailability(Map<String, Instance> jenkinsInstancesByTeamId) {
        for (Map.Entry<String, Instance> idAndInstance : jenkinsInstancesByTeamId.entrySet()) {
            Instance instance = idAndInstance.getValue();

            String deployitUrl = TeamInfrastructure.getDeployitUrl(instance);
            if (deployitUrl == null) {
                continue;
            }

            logger.info("Waiting for DeployIt availability '{}' - {}", idAndInstance.getKey(), instance.getInstanceId());

            try {
                AmazonAwsUtils.awaitForHttpAvailability(deployitUrl);
            } catch (Exception e) {
                logger.warn("Silently skipped " + e, e);
            }
        }
    }

}
