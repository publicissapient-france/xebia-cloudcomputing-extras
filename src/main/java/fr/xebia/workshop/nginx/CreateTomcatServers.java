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
package fr.xebia.workshop.nginx;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.model.GetHostedZoneRequest;
import com.amazonaws.services.route53.model.HostedZone;
import com.google.common.collect.Maps;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CreateTomcatServers implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String TOMCAT_NGINX = "xfr-cocktail-nginx-";
    private static final String CLOUD_CONFIG_FILE_PATH = "fr/xebia/workshop/nginx/cloud-config-amzn-linux-tomcat.txt";

    private AmazonEC2 ec2;
    private AmazonRoute53 route53;
    private WorkshopInfrastructure workshopInfrastructure;
    private HostedZone hostedZone;

    public CreateTomcatServers(AmazonEC2 ec2, AmazonRoute53 route53, WorkshopInfrastructure workshopInfrastructure) {
        this.ec2 = ec2;
        this.route53 = route53;
        this.workshopInfrastructure = workshopInfrastructure;
        this.hostedZone = route53.getHostedZone(new GetHostedZoneRequest("Z28O5PDK1WPCSR")).getHostedZone();
    }

    private List<Instance> createNewInstances(AmazonEC2 ec2, WorkshopInfrastructure infra) {
        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(CLOUD_CONFIG_FILE_PATH).buildBase64UserData();

        int instanceCount = infra.getTeamCount();

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST_2012_09) //
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

    private Map<String, Instance> associateInstancesToTeamIds(List<Instance> instances, List<String> teamIdentifiers) {
        Map<String, Instance> instancesByTeamId = Maps.newHashMap();

        Iterator<String> teamsIdIterator = teamIdentifiers.iterator();
        for (Instance instance : instances) {
            instancesByTeamId.put(teamsIdIterator.next(), instance);
        }

        return instancesByTeamId;
    }

    private String buildCname(String teamId) {
        return TOMCAT_NGINX + teamId + "." + hostedZone.getName();
    }

    private void tagInstances(Map<String, Instance> instancesByTeamId, AmazonEC2 ec2) {
        for (Map.Entry<String, Instance> entry : instancesByTeamId.entrySet()) {
            String identifier = entry.getKey();
            Instance instance = entry.getValue();

            String serverName = TOMCAT_NGINX + identifier;
            logger.info("Tagging {} - {}", serverName, instance.getInstanceId());

            CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
                    .withResources(instance.getInstanceId()) //
                    .withTags( //
                            new Tag("Name", serverName), //
                            new Tag("TeamIdentifier", identifier), //
                            new Tag("Workshop", "nginx"), //
                            new Tag("Role", "nginx-proxy"), //
                            new Tag("CNAME", buildCname(identifier))//
                    );

            AmazonAwsUtils.createTags(instance, createTagsRequest, ec2);
        }
    }

    private void bindInstancesToDnsCnames(Map<String, Instance> instancesByTeamId, AmazonRoute53 route53) {
        logger.info("Process {}", hostedZone);

        Map<String, Instance> cnamesToInstance = Maps.newHashMap();

        for (Map.Entry<String, Instance> entry : instancesByTeamId.entrySet()) {
            String teamId = entry.getKey();
            Instance instance = entry.getValue();

            String cname = buildCname(teamId);
            cnamesToInstance.put(cname, instance);
        }

        AmazonAwsUtils.deleteCnameIfExist(cnamesToInstance.keySet(), hostedZone, route53);
        AmazonAwsUtils.createCnamesForInstances(cnamesToInstance, hostedZone, route53);
    }

    public void run() {
        List<Instance> instances = createNewInstances(ec2, workshopInfrastructure);
        Map<String, Instance> instancesByTeamId = associateInstancesToTeamIds(instances, workshopInfrastructure.getTeamIdentifiers());

        tagInstances(instancesByTeamId, ec2);

        bindInstancesToDnsCnames(instancesByTeamId, route53);

        logger.info("Tomcat servers for Nginx creation SUCCESSFUL");
    }

}
