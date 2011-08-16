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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.amazonaws.services.ec2.model.DeregisterImageRequest;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;

public class AmazonAwsShutdowner {

    private static final String TAG_DO_NOT_DEREGISTER = "do-not-deregister";

   
    public static final Function<Instance, String> TO_INSTANCE_ID_FUNCTION = new Function<Instance, String>() {
        @Override
        public String apply(Instance instance) {
            return instance.getInstanceId();
        }
    };

    public static final Function<Image, String> TO_IMAGE_ID_FUNCTION = new Function<Image, String>() {
        @Override
        public String apply(Image image) {
            return image.getImageId();
        }
    };

    private static final String TAG_DO_NOT_TERMINATE = "do-not-terminate";

    private static final String TAG_DO_NOT_STOP = "do-not-stop";

    public static void main(String[] args) throws Exception {
        AmazonAwsShutdowner amazonAwsShutdowner = new AmazonAwsShutdowner();
        amazonAwsShutdowner.test();

    }

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private AmazonEC2 ec2;

    private AmazonRDS rds;

    private AmazonElasticLoadBalancing elb;

    public AmazonAwsShutdowner() throws IOException {
        InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("AwsCredentials.properties");
        Preconditions.checkNotNull(credentialsAsStream, "File 'AwsCredentials.properties' NOT found in the classpath");
        AWSCredentials credentials = new PropertiesCredentials(credentialsAsStream);
        ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
        rds = new AmazonRDSClient(credentials);
        rds.setEndpoint("rds.eu-west-1.amazonaws.com");
        elb = new AmazonElasticLoadBalancingClient(credentials);
        elb.setEndpoint("elasticloadbalancing.eu-west-1.amazonaws.com");
    }

    public void test() {
        String ownerId = "self";
        
        boolean dryRun = true;

        // RETRIEVE TAGS
        Map<String, Map<String, String>> tagsByResourceId = new MapMaker().makeComputingMap(new Function<String, Map<String, String>>() {
            @Override
            public Map<String, String> apply(String input) {
                return Maps.newHashMap();
            }
        });

        for (TagDescription tagDescription : ec2.describeTags().getTags()) {
            tagsByResourceId.get(tagDescription.getResourceId()).put(tagDescription.getKey(), tagDescription.getValue());
        }

        // RDS INSTANCEs
        for (DBInstance dbInstance : rds.describeDBInstances().getDBInstances()) {
            Map<String, String> instanceTags = tagsByResourceId.get(dbInstance.getDBInstanceIdentifier());
            logger.debug("Tags for " + dbInstance + ": " + instanceTags);

        }

        // EC2 INSTANCES
        List<Instance> instancesAlreadyNotStarted = Lists.newArrayList();
        List<Instance> instancesToStop = Lists.newArrayList();
        List<Instance> instancesToTerminate = Lists.newArrayList();
        List<Instance> instancesToKeepUnchanged = Lists.newArrayList();
        for (Reservation reservation : ec2.describeInstances().getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                Map<String, String> instanceTags = tagsByResourceId.get(instance.getInstanceId());
                logger.debug("Tags for {}: {}", instance, instanceTags);
                if("terminated".equals(instance.getState().getName())) {
                    instancesToKeepUnchanged.add(instance);                    
                } else if (instanceTags.containsKey(TAG_DO_NOT_STOP)) {
                    instancesToKeepUnchanged.add(instance);
                } else if (instanceTags.containsKey(TAG_DO_NOT_TERMINATE)) {
                    if ("started".equals(instance.getState().getName())) {
                        instancesToStop.add(instance);
                    } else {
                        instancesAlreadyNotStarted.add(instance);
                    }
                } else {
                    instancesToTerminate.add(instance);
                }
            }
        }
        System.out.println("EC2 INSTANCES");
        if (dryRun) {
            System.out.println("DRY RUN Terminate:" + instancesToTerminate);
            System.out.println("DRY RUN Stop:" + instancesToStop);
            System.out.println("DRY RUN No need to stop:" + instancesAlreadyNotStarted);
            System.out.println("DRY RUN Keep unchanged:" + instancesToKeepUnchanged);
        } else {
            System.out.println("Terminate:" + instancesToTerminate);
            if (!instancesToTerminate.isEmpty()) {
                ec2.terminateInstances(new TerminateInstancesRequest(Lists.transform(instancesToTerminate, TO_INSTANCE_ID_FUNCTION)));
            }
            System.out.println("Stop:" + instancesToStop);
            if (!instancesToStop.isEmpty()) {
                ec2.stopInstances(new StopInstancesRequest(Lists.transform(instancesToStop, TO_INSTANCE_ID_FUNCTION)));
            }
            System.out.println("No need to stop:" + instancesAlreadyNotStarted);
            System.out.println("Keep unchanged:" + instancesToKeepUnchanged);
        }

        // AMIs
        System.out.println("AMIs");
        List<Image> imagesToDeRegister = Lists.newArrayList();
        List<Image> imagesToKeep = Lists.newArrayList();
        for (Image image : ec2.describeImages(new DescribeImagesRequest().withOwners(ownerId)).getImages()) {
            Map<String, String> imageTags = tagsByResourceId.get(image.getImageId());
            logger.debug("Tags for {}: {}", image, imageTags);
            if (imageTags.containsKey(TAG_DO_NOT_DEREGISTER)) {
                imagesToKeep.add(image);
            } else {
                imagesToDeRegister.add(image);
            }
        }
        if (dryRun) {
            System.out.println("DRY RUN Deregister:" + imagesToDeRegister);
            System.out.println("DRY RUN Keep:" + imagesToKeep);

        } else {
            System.out.println("Deregister:" + imagesToDeRegister);
            for (Image image : imagesToDeRegister) {
                ec2.deregisterImage(new DeregisterImageRequest(image.getImageId()));
            }
            System.out.println("Keep:" + imagesToKeep);
        }

        List<String> imageIdsToKeep = Lists.transform(imagesToKeep, TO_IMAGE_ID_FUNCTION);

        // SNAPSHOTS
        System.out.println("SNAPSHOTs");
        for (Snapshot snapshot : ec2.describeSnapshots(new DescribeSnapshotsRequest().withOwnerIds(ownerId)).getSnapshots()) {

            if (snapshot.getDescription().contains("Created by CreateImage")) {
                boolean associatedWithAnImageToKeep = false;
                for (String imageIdToKeep : imageIdsToKeep) {
                    if (snapshot.getDescription().contains(imageIdToKeep)) {
                        associatedWithAnImageToKeep = true;
                        break;
                    }
                }
                if (associatedWithAnImageToKeep) {
                    System.out.println("Keep: " + snapshot);
                } else {
                    if (dryRun) {
                        System.out.println("DRY RUN delete: " + snapshot);
                    } else {
                        System.out.println("Delete: " + snapshot);
                        ec2.deleteSnapshot(new DeleteSnapshotRequest(snapshot.getSnapshotId()));
                    }
                }
            }
        }
        // ELASTIC LOAD BALANCERs
        // no tags on elb
    }

}
