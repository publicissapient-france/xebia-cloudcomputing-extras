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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

public abstract class InfrastructureCreationStep {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected static final String WORKSHOP_IMAGE_ID = AmazonAwsUtils.AMI_AMZN_LINUX_EU_WEST;

    public abstract void execute(AmazonEC2 ec2, WorkshopInfrastructure workshopInfrastructure) throws Exception;

    protected void createTags(Instance instance, CreateTagsRequest createTagsRequest, AmazonEC2 ec2) {
        // "AWS Error Code: InvalidInstanceID.NotFound, AWS Error Message: The instance ID 'i-d1638198' does not exist"
        AmazonAwsUtils.awaitForEc2Instance(instance, ec2);

        try {
            ec2.createTags(createTagsRequest);
        } catch (AmazonServiceException e) {
            // retries 5s later
            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            ec2.createTags(createTagsRequest);
        }
    }

}
