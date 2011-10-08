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
package fr.xebia.demo.amazon.aws;

import java.util.Collection;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

public class AmazonAwsUtilsTest {

    @Ignore
    @Test
    public void test_awaitForHttpAvailability() {
        AmazonAwsUtils.awaitForHttpAvailability("http://google.com/");
    }

    @Ignore
    @Test
    public void test_terminateInstancesByRole() {
        AWSCredentials credentials = AmazonAwsUtils.loadAwsCredentials();
        AmazonEC2 ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
        AmazonAwsUtils.terminateInstancesByRole("nexus", ec2);
    }

    @Ignore
    @Test
    public void test_reliableEc2RunInstances() {
        AWSCredentials credentials = AmazonAwsUtils.loadAwsCredentials();
        AmazonEC2 ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.M1Small.toString()) //
                .withImageId(AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST) //
                .withMinCount(3) //
                .withMaxCount(3) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("cleclerc");

        List<Instance> runningInstances = AmazonAwsUtils.reliableEc2RunInstances(runInstancesRequest, ec2);
        Collection<String> runningInstanceIds = Collections2.transform(runningInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID);

        ec2.createTags(new CreateTagsRequest().withResources(runningInstanceIds).withTags(new Tag("Name", "test_reliableEc2RunInstances")));
        System.out.println("Successfully started instances " + Joiner.on(", ").join(runningInstanceIds));
        System.out.println(Joiner.on(",\n").join(runningInstances));
        ec2.terminateInstances(new TerminateInstancesRequest().withInstanceIds(runningInstanceIds));
    }

    ;

}
