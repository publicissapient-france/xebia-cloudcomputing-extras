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

import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Iterables;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;

public class CreateNexusInstance extends InfrastructureCreationStep {

    private static final String KEY_PAIR_NAME = "continuous-delivery-workshop";

    @Override
    public void execute(AmazonEC2 ec2, WorkshopInfrastructure workshopInfrastructure) throws Exception {
        logger.info("STARTING CREATE NEXUS SERVER");

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
                .withUserData(userData);
        
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

        logger.info("1 NEXUS SERVER {} SUCCESSFULLY CREATED AND ASSOCIATED WITH {}: {}", new Object[] { nexusInstance.getInstanceId(),
                publicIp, nexusInstance });
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

    @Override
    public String toString() {
        return "Nexus instance creation";
    }
}
