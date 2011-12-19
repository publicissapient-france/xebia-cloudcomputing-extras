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

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.google.common.collect.Maps;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.Lists.transform;

public class CreateNagiosInstances extends InfrastructureCreationStep {

    @Override
    public void execute(AmazonEC2 ec2, WorkshopInfrastructure infra) throws Exception {
        logger.info("CREATING NAGIOS SERVERS");

        AmazonAwsUtils.terminateInstancesByRoleAndTeam(TeamInfrastructure.ROLE_NAGIOS, infra.getTeamIdentifiers(), ec2);

        List<Instance> nagiosInstance = createNewInstances(ec2, infra);

        Map<String, Instance> nagiosInstancesByTeamId = associateInstancesToTeamIds(nagiosInstance, infra.getTeamIdentifiers());

        tagInstances(nagiosInstancesByTeamId, ec2);

        logger.info("{} NAGIOS SERVERS SUCCESSFULLY CREATED: {}", nagiosInstance.size(),
                transform(nagiosInstance, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID));
    }

    private List<Instance> createNewInstances(AmazonEC2 ec2, WorkshopInfrastructure infra) {
        int instanceCount = infra.getTeamCount();

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId(NAGIOS_IMAGE_ID) //
                .withMinCount(instanceCount) //
                .withMaxCount(instanceCount) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName(infra.getKeyPairName());

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

            String serverName = "nagios-" + identifier;
            logger.info("Tagging {} - {}", serverName, instance.getInstanceId());

            CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
                    .withResources(instance.getInstanceId()) //
                    .withTags(//
                            new Tag("Name", serverName), //
                            new Tag("TeamIdentifier", identifier), //
                            new Tag("Workshop", "monitoring"), //
                            new Tag("Role", TeamInfrastructure.ROLE_NAGIOS));

            createTags(instance, createTagsRequest, ec2);
        }
    }
}
