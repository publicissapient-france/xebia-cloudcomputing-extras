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

import javax.annotation.Nonnull;

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
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import fr.xebia.demo.amazon.aws.PetclinicJenkinsJobCreator;
import fr.xebia.demo.amazon.aws.PetclinicProjectInstance;

public class ContinuousDeliveryInfrastructureCreator {

    public static void main(String[] args) {

    }

    AmazonEC2 ec2;

    public ContinuousDeliveryInfrastructureCreator() {
        try {
            InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("AwsCredentials.properties");
            Preconditions.checkNotNull(credentialsAsStream, "File 'AwsCredentials.properties' NOT found in the classpath");
            AWSCredentials credentials = new PropertiesCredentials(credentialsAsStream);
            ec2 = new AmazonEC2Client(credentials);
            ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private Logger logger = LoggerFactory.getLogger(getClass());

    List<Instance> buildJenkins(Set<String> identifiers) {
        logger.info("ENFORCE JENKINS/RUNDECK SERVERS");

        // CLOUD CONFIG
        String cloudConfigFilePath = "fr/xebia/continuousdelivery/cloud-config-amzn-linux-jenkins-rundeck.txt";

        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        // CREATE EC2 INSTANCES
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId("ami-47cefa33") //
                .withMinCount(identifiers.size()) //
                .withMaxCount(identifiers.size()) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("continuous-delivery-workshop") //
                .withUserData(userData) //

        ;
        RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
        List<Instance> instances = runInstances.getReservation().getInstances();

        instances = awaitForEc2Instances(instances);

        // TAG EC2 INSTANCES
        {
            Iterator<String> identifiersIterator = identifiers.iterator();
            for (Instance instance : instances) {
                CreateTagsRequest createTagsRequest = new CreateTagsRequest();
                createTagsRequest.withResources(instance.getInstanceId()) //
                        .withTags(//
                                new Tag("Name", "jenkins-" + identifiersIterator.next()), //
                                new Tag("Role", "jenkins,rundeck"));
                ec2.createTags(createTagsRequest);
            }
            if (identifiersIterator.hasNext()) {
                logger.warn("Remaining identifiers " + Lists.newArrayList(identifiersIterator));
            }
        }

        logger.info("Created jenkins servers {}", instances);

        // TODO CONFIGURE JENKINS: create job

        Iterator<String> identifiersIterator = identifiers.iterator();
        for (Instance instance : instances) {
            // TODO fix URL
            String jenkinsUrl = "http://" + instance.getPublicIpAddress() + "8081/";
            new PetclinicJenkinsJobCreator(jenkinsUrl).create(new PetclinicProjectInstance(identifiersIterator.next()));
        }

        return instances;

    }

    /**
     * <p>
     * Wait for the ec2 instances creation and returns a list of
     * {@link Instance} with up to date values.
     * </p>
     * <p>
     * Note: some information are missing of the {@link Instance} returned by
     * {@link AmazonEC2#describeInstances(DescribeInstancesRequest)} as long as
     * the instance is not "running" (e.g. {@link Instance#getPublicDnsName()}).
     * </p>
     * 
     * @param instances
     * @return up to date instances
     */
    @Nonnull
    public List<Instance> awaitForEc2Instances(@Nonnull Iterable<Instance> instances) {

        List<Instance> result = Lists.newArrayList();
        for (Instance instance : instances) {
            while (InstanceStateName.Pending.equals(instance.getState())) {
                try {
                    // 3s because ec2 instance creation < 10 seconds
                    Thread.sleep(3 * 1000);
                } catch (InterruptedException e) {
                    throw Throwables.propagate(e);
                }
                DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instance.getImageId());
                DescribeInstancesResult describeInstances = ec2.describeInstances(describeInstancesRequest);

                instance = Iterables.getOnlyElement(toEc2Instances(describeInstances.getReservations()));
            }
            result.add(instance);
        }
        return result;
    }

    /**
     * Converts a collection of {@link Reservation} into a collection of their
     * underlying
     * 
     * @param reservations
     * @return
     */
    @Nonnull
    public static Iterable<Instance> toEc2Instances(@Nonnull Iterable<Reservation> reservations) {
        Iterable<List<Instance>> collectionOfListOfInstances = Iterables.transform(reservations,
                new Function<Reservation, List<Instance>>() {
                    @Override
                    public List<Instance> apply(Reservation reservation) {
                        return reservation.getInstances();
                    }
                });
        return Iterables.concat(collectionOfListOfInstances);
    }

    Instance buildNexus() {
        logger.info("ENFORCE NEXUS SERVER");

        // CREATE NEXUS INSTANCE
        String cloudConfigFilePath = "fr/xebia/continuousdelivery/cloud-config-amzn-linux-nexus.txt";
        String userData = CloudInitUserDataBuilder.start().addCloudConfigFromFilePath(cloudConfigFilePath).buildBase64UserData();

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString()) //
                .withImageId("ami-47cefa33") //
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
                        new Tag("Name", "nexus"), //
                        new Tag("Role", "nexus"));
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

        ec2.associateAddress(new AssociateAddressRequest(instance.getInstanceId(), publicIp));

        logger.info("Created nexus server {} associated with {}", instance, publicIp);

        return instance;

    }
}
