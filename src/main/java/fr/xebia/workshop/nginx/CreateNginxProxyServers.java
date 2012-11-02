/*
 * Copyright 2008-2012 Xebia and the original author or authors.
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.GetHostedZoneRequest;
import com.amazonaws.services.route53.model.HostedZone;
import com.google.common.collect.Maps;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:cleclerc@xebia.fr">Cyrille Le Clerc</a>
 * @author <a href="mailto:slemesle@xebia.fr">Séven Le Mesle</a>
 */
public class CreateNginxProxyServers implements Runnable {


    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String WWW_NGINX = "www-nginx-";
    private static final String CLOUD_CONFIG_FILE_PATH = "fr/xebia/workshop/nginx/cloud-config-amzn-linux-nginx.txt";

    private AmazonEC2 ec2;

    private AmazonRoute53 route53;

    private WorkshopInfrastructure workshopInfrastructure;

    public static void main(String[] args) {
        AWSCredentials awsCredentials = AmazonAwsUtils.loadAwsCredentials();
        AmazonEC2 ec2 = new AmazonEC2Client(awsCredentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

        AmazonRoute53 route53 = new AmazonRoute53Client(awsCredentials);

        WorkshopInfrastructure workshopInfrastructure = new WorkshopInfrastructure()
                .withTeamIdentifiers("1"/*, "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"*/)
                .withAwsAccessKeyId(awsCredentials.getAWSAccessKeyId())
                .withAwsSecretKey(awsCredentials.getAWSSecretKey())
                .withKeyPairName("nginx-workshop")
                .withBeanstalkNotificationEmail("slemesle@xebia.fr");

        CreateNginxProxyServers job = new CreateNginxProxyServers(ec2, route53, workshopInfrastructure);
        job.run();
        /*
        List<Reservation> reservations = ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds("i-7741eb3f")).getReservations();
        Instance instance = Iterables.getOnlyElement(Iterables.getOnlyElement(reservations).getInstances());

        Map<String, Instance> instancesByTeamId = Collections.singletonMap("clc", instance);

        job.bindInstancesToDnsCnames(instancesByTeamId, route53);
*/

    }


    private HostedZone hostedZone;

    public CreateNginxProxyServers(AmazonEC2 ec2, AmazonRoute53 route53, WorkshopInfrastructure workshopInfrastructure) {
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

    protected Map<String, Instance> associateInstancesToTeamIds(List<Instance> instances, List<String> teamIdentifiers) {
        Map<String, Instance> instancesByTeamId = Maps.newHashMap();

        Iterator<String> teamsIdIterator = teamIdentifiers.iterator();
        for (Instance instance : instances) {
            instancesByTeamId.put(teamsIdIterator.next(), instance);
        }

        return instancesByTeamId;
    }

    protected void tagInstances(Map<String, Instance> instancesByTeamId, AmazonEC2 ec2) {
        for (Map.Entry<String, Instance> entry : instancesByTeamId.entrySet()) {
            String identifier = entry.getKey();
            Instance instance = entry.getValue();

            String serverName = WWW_NGINX + identifier;
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

    public void run() {
        AmazonAwsUtils.terminateInstancesByWorkshop("nginx", ec2);

        List<Instance> instances = createNewInstances(ec2, workshopInfrastructure);
        Map<String, Instance> instancesByTeamId = associateInstancesToTeamIds(instances, workshopInfrastructure.getTeamIdentifiers());

        tagInstances(instancesByTeamId, ec2);

        bindInstancesToDnsCnames(instancesByTeamId, route53);


    }

    protected void bindInstancesToDnsCnames(Map<String, Instance> instancesByTeamId, AmazonRoute53 route53) {


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

        logger.info("Nginx servers creation SUCCESSFUL");
    }

    protected String buildCname(String teamId) {
        return WWW_NGINX + teamId + "." + hostedZone.getName();
    }

}
