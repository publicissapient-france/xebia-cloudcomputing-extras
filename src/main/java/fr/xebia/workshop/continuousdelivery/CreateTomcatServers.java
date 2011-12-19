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

import static com.google.common.collect.Lists.transform;

import java.util.Iterator;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;

public class CreateTomcatServers extends InfrastructureCreationStep {

    private static final String CLOUD_CONFIG_FILE_PATH = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-tomcat.txt";

    private final String environment;
    private final int numberOfInstances;

    public CreateTomcatServers(String environment, int numberOfInstances) {
        this.environment = environment;
        this.numberOfInstances = numberOfInstances;
    }

    @Override
    public void execute(AmazonEC2 ec2, WorkshopInfrastructure infra) throws Exception {
        logger.info("CREATING TOMCAT '{}' SERVERS", environment);

        String role = TeamInfrastructure.ROLE_TOMCAT + "-" + environment;
        AmazonAwsUtils.terminateInstancesByRoleAndTeam(role, infra.getTeamIdentifiers(), ec2);

        List<Instance> tomcatInstances = createNewInstances(ec2, infra);

        tagInstances(tomcatInstances, role, ec2, infra);

        logger.info(
                "{} TOMCAT '{}' SERVERS SUCCESSFULLY CREATED: {}",
                new Object[] { tomcatInstances.size(), environment,
                        transform(tomcatInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID) });
    }

    private void tagInstances(List<Instance> tomcatInstances, String role, AmazonEC2 ec2, WorkshopInfrastructure infra) {
        Iterator<String> teamInfrastructureIterator = infra.getTeamIdentifiers().iterator();
        Iterator<Instance> tomcatInstancesIterator = tomcatInstances.iterator();

        while (tomcatInstancesIterator.hasNext()) {
            String identifier = teamInfrastructureIterator.next();
            for (int i = 0; i < numberOfInstances && tomcatInstancesIterator.hasNext(); i++) {
                Instance tomcatInstance = tomcatInstancesIterator.next();

                String serverName = "tomcat-" + identifier + "-" + environment + "-" + (i + 1);

                CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
                        .withResources(tomcatInstance.getInstanceId()) //
                        .withTags(//
                                new Tag("Name", serverName), //
                                new Tag("Workshop", "continuous-delivery-workshop"), //
                                new Tag("TeamIdentifier", identifier), //
                                new Tag("Role", role));

                createTags(tomcatInstance, createTagsRequest, ec2);
            }
        }
    }

    private List<Instance> createNewInstances(AmazonEC2 ec2, WorkshopInfrastructure infra) {
        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(CLOUD_CONFIG_FILE_PATH).buildBase64UserData();

        int instanceCount = infra.getTeamCount() * numberOfInstances;

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId(WORKSHOP_IMAGE_ID) //
                .withMinCount(instanceCount) //
                .withMaxCount(instanceCount) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName(infra.getKeyPairName())
                .withUserData(userData);

        List<Instance> instances = AmazonAwsUtils.reliableEc2RunInstances(runInstancesRequest, ec2);

        if (instances.size() != instanceCount) {
            logger.warn("Unexpected number of instances created: {} instead of {} expected", instances.size(), instanceCount);
        }

        return instances;
    }

}
