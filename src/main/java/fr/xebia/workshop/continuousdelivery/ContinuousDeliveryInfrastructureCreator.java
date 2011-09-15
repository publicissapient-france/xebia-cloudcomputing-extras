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

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
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
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;

public class ContinuousDeliveryInfrastructureCreator {

    private static final String ROLE_JENKINS_RUNDECK = "jenkins,rundeck";

    public static void main(String[] args) {

        ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator();
        try {
            creator.buildNexus();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            creator.buildJenkins("clc");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    AmazonEC2 ec2;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public final String ROLE_NEXUS = "nexus";

    public ContinuousDeliveryInfrastructureCreator() {
        AWSCredentials credentials = AmazonAwsUtils.loadAwsCredentials();
        ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
    }

    public List<Instance> buildJenkins(String... identifiers) {
        return buildJenkins(Sets.newHashSet(identifiers));
    }

    public List<Instance> buildJenkins(Set<String> identifiers) {
        logger.info("ENFORCE JENKINS/RUNDECK SERVERS");

        AmazonAwsUtils.terminateInstancesByRole(ROLE_JENKINS_RUNDECK, ec2);

        // CLOUD CONFIG
        String cloudConfigFilePath = "fr/xebia/workshop/continuousdelivery/cloud-config-amzn-linux-jenkins-rundeck.txt";

        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        // CREATE EC2 INSTANCES
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.M1Small.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST) //
                .withMinCount(identifiers.size()) //
                .withMaxCount(identifiers.size()) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("continuous-delivery-workshop") //
                .withUserData(userData) //

        ;
        RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
        List<Instance> instances = runInstances.getReservation().getInstances();

        // TAG EC2 INSTANCES
        {
            Iterator<String> identifiersIterator = identifiers.iterator();
            for (Instance instance : instances) {
                CreateTagsRequest createTagsRequest = new CreateTagsRequest();
                createTagsRequest.withResources(instance.getInstanceId()) //
                        .withTags(//
                                new Tag("Name", "continuous-delivery-workshop-jenkins-" + identifiersIterator.next()), //
                                new Tag("Role", ROLE_JENKINS_RUNDECK));
                ec2.createTags(createTagsRequest);
            }
            if (identifiersIterator.hasNext()) {
                logger.warn("Remaining identifiers " + Lists.newArrayList(identifiersIterator));
            }
        }
        instances = AmazonAwsUtils.awaitForEc2Instances(instances, ec2);

        logger.info("Created jenkins servers {}", instances);

        // CONFIGURE JENKINS
        {
            Iterator<String> identifiersIterator = identifiers.iterator();
            for (Instance instance : instances) {
                // TODO fix URL
                String jenkinsUrl = "http://" + instance.getPublicDnsName() + ":8080/";
                AmazonAwsUtils.awaitForHttpAvailability(jenkinsUrl);
                try {
                    new PetclinicJenkinsJobCreator(jenkinsUrl).create(new PetclinicProjectInstance(identifiersIterator.next()));
                } catch (Exception e) {
                    logger.warn("Silently skip " + e, e);
                }
            }
        }

        return instances;

    }

    public Instance buildNexus() {
        logger.info("ENFORCE NEXUS SERVER");

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
            logger.info("Public IP {} is currently associated instance {}. Disassociate it first.", publicIp, currentlyAssociatedId);
            ec2.disassociateAddress(new DisassociateAddressRequest(publicIp));
        }

        instance = AmazonAwsUtils.awaitForEc2Instance(instance, ec2);
        ec2.associateAddress(new AssociateAddressRequest(instance.getInstanceId(), publicIp));

        logger.info("Created nexus server {} associated with {}", instance, publicIp);

        AmazonAwsUtils.awaitForHttpAvailability("http://" + publicIp + ":8081/nexus/");
        AmazonAwsUtils.awaitForHttpAvailability("http://nexus.xebia-tech-event.info:8081/nexus/");
        return instance;

    }
}
