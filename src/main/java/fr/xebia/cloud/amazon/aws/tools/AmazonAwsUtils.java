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
package fr.xebia.cloud.amazon.aws.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceNotFoundException;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class AmazonAwsUtils {

    /**
     * private constructor for utils class.
     */
    private AmazonAwsUtils() {

    }

    public final static String AMI_AMZN_LINUX_EU_WEST = "ami-47cefa33";

    /**
     * Load {@link AWSCredentials} from '<code>AwsCredentials.properties</code>
     * '.
     */
    @Nonnull
    public static AWSCredentials loadAwsCredentials() {
        String credentialsFilePath = "AwsCredentials.properties";
        InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(credentialsFilePath);
        Preconditions.checkState(credentialsAsStream != null, "File '" + credentialsFilePath + "' NOT found in the classpath");
        try {
            return new PropertiesCredentials(credentialsAsStream);
        } catch (IOException e) {
            throw new IllegalStateException("Exception loading '" + credentialsFilePath + "'", e);
        }
    }

    /**
     * <p>
     * Wait for the ec2 instances startup and returns a list of {@link Instance}
     * with up to date values.
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
    public static List<Instance> awaitForEc2Instances(@Nonnull Iterable<Instance> instances, @Nonnull AmazonEC2 ec2) {

        List<Instance> result = Lists.newArrayList();
        for (Instance instance : instances) {
            instance = awaitForEc2Instance(instance, ec2);
            result.add(instance);
        }
        return result;
    }

    /**
     * <p>
     * Wait for the ec2 instance startup and returns it up to date
     * </p>
     * <p>
     * Note: some information are missing of the {@link Instance} returned by
     * {@link AmazonEC2#describeInstances(DescribeInstancesRequest)} as long as
     * the instance is not "running" (e.g. {@link Instance#getPublicDnsName()}).
     * </p>
     * 
     * @param instance
     * @return up to date instances
     */
    @Nonnull
    public static Instance awaitForEc2Instance(@Nonnull Instance instance, @Nonnull AmazonEC2 ec2) {
        int counter = 0;
        while (InstanceStateName.Pending.equals(instance.getState()) || (instance.getPublicIpAddress() == null)
                || (instance.getPublicDnsName() == null)) {
            logger.trace("Wait for startup of {}", instance);
            try {
                // 3s because ec2 instance creation < 10 seconds
                Thread.sleep(3 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instance.getInstanceId());
            DescribeInstancesResult describeInstances = ec2.describeInstances(describeInstancesRequest);

            instance = Iterables.getOnlyElement(toEc2Instances(describeInstances.getReservations()));
            counter ++;
            if(counter >= 20) {
                logger.warn("Wait Timeout for {}", instance);
                return instance;
            }
        }
        logger.debug("Instance {} is started", instance);
        return instance;
    }

    /**
     * Awaits for the given healthcheck url to return 200/OK. If the url did not
     * return 200/OK within 240 secs, an {@link IllegalStateException} is
     * raised.
     * 
     * @param healthCheckUrl
     * @throws IllegalStateException
     *             if the healthCheckUrl did not return 200/OK within 240
     *             seconds.
     */
    public static void awaitForHttpAvailability(@Nonnull String healthCheckUrl) throws IllegalStateException {

        RuntimeException cause = null;
        for (int i = 0; i < 4 * 60; i++) {
            try {
                HttpURLConnection healthCheckHttpURLConnection = (HttpURLConnection) new URL(healthCheckUrl).openConnection();
                
                healthCheckHttpURLConnection.setReadTimeout(1000);

                int responseCode = healthCheckHttpURLConnection.getResponseCode();
                if (HttpURLConnection.HTTP_OK == responseCode) {
                    logger.info("URL {} is available", healthCheckUrl);
                    return;
                } else {
                    logger.trace("URL {} is not yet available, responseCode={}", healthCheckUrl, responseCode);

                    cause = new IllegalStateException("'" + healthCheckUrl + "' returned response code " + responseCode);
                }
            } catch (IOException e) {
                cause = new IllegalStateException("Exception invoking '" + healthCheckUrl + "'", e);
                logger.trace("Exception invoking healthcheck URL {}", healthCheckUrl, e);
            }
            try {
                Thread.sleep(1 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }

        throw cause;
    }

    /**
     * Terminate all instances matching the given "Role" tag.
     * 
     * @param role
     *            searched value of the "Role" tag
     * @param ec2
     */
    public static void terminateInstancesByRole(@Nonnull String role, @Nonnull AmazonEC2 ec2) {
        terminateInstancesByTagValue("Role", role, ec2);
    }

    /**
     * Terminate all instances matching the given tag.
     * 
     * @param tagName
     * @param tagValue
     *            if <code>null</code>, terminate all the instances having the
     *            given tag defined.
     * @param ec2
     */
    public static void terminateInstancesByTagValue(@Nonnull String tagName, @Nullable String tagValue, @Nonnull AmazonEC2 ec2) {

        Filter filter;
        if (tagValue == null) {
            filter = new Filter("tag:" + tagName);
        } else {
            filter = new Filter("tag:" + tagName, Arrays.asList(tagValue));
        }
        DescribeInstancesRequest describeInstancesWithRoleRequest = new DescribeInstancesRequest(). //
                withFilters(filter);
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesWithRoleRequest);

        Iterable<Instance> exstingInstances = AmazonAwsUtils.toEc2Instances(describeInstancesResult.getReservations());
        List<String> instanceIds = Lists
                .newArrayList(Iterables.transform(exstingInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID));

        if (instanceIds.isEmpty()) {
            logger.debug("No server tagged with '{}'='{}' to terminate", tagName, tagValue);
        } else {
            logger.info("Terminate servers tagged with '{}'='{}': {}", new Object[] { tagName, tagValue, instanceIds });

            ec2.terminateInstances(new TerminateInstancesRequest(instanceIds));
        }
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

    private static final Logger logger = LoggerFactory.getLogger(AmazonAwsUtils.class);

    /**
     * <p>
     * Wait for the database creation and returns a {@link DBInstance} with up
     * to date values.
     * </p>
     * <p>
     * Note: some information are missing of the {@link DBInstance} returned by
     * {@link AmazonRDS#describeDBInstances(DescribeDBInstancesRequest)} as long
     * as the instance is "creating" rather than "available" (e.g.
     * {@link DBInstance#getEndpoint()} that holds the ip address).
     * </p>
     * 
     * @param dbInstance
     * @param rds
     * @return up to date 'available' dbInstance
     * @throws DBInstanceNotFoundException
     *             the given <code>dbInstance</code> no longer exists (e.g. it
     *             has been destroyed, etc).
     */
    @Nonnull
    public static DBInstance awaitForDbInstanceCreation(@Nonnull DBInstance dbInstance, @Nonnull AmazonRDS rds)
            throws DBInstanceNotFoundException {
        logger.info("Get Instance " + dbInstance.getDBInstanceIdentifier() + "/" + dbInstance.getDBName() + " status");

        while (!"available".equals(dbInstance.getDBInstanceStatus())) {
            logger.info("Instance " + dbInstance.getDBInstanceIdentifier() + "/" + dbInstance.getDBName() + " not yet available, sleep...");
            try {
                // 20 s because MySQL creation takes much more than 60s
                Thread.sleep(20 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            DescribeDBInstancesRequest describeDbInstanceRequest = new DescribeDBInstancesRequest().withDBInstanceIdentifier(dbInstance
                    .getDBInstanceIdentifier());
            DescribeDBInstancesResult describeDBInstancesResult = rds.describeDBInstances(describeDbInstanceRequest);

            List<DBInstance> dbInstances = describeDBInstancesResult.getDBInstances();
            Preconditions.checkState(dbInstances.size() == 1, "Exactly 1 db instance expected : %S", dbInstances);
            dbInstance = Iterables.getFirst(dbInstances, null);

        }
        return dbInstance;
    }
}
