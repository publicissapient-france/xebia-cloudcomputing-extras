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
package fr.xebia.training.troubleshooting;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceAttributeRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.CreateDBInstanceRequest;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceNotFoundException;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import fr.xebia.cloud.cloudinit.FreemarkerUtils;

public class TroubleshootingTrainingInfrastructureCreator {

    AmazonEC2 ec2;
    AmazonRDS rds;

    public static void main(String[] args) {
        TroubleshootingTrainingInfrastructureCreator creator = new TroubleshootingTrainingInfrastructureCreator();
        creator.createTrainingInfrastructure();
    }

    public TroubleshootingTrainingInfrastructureCreator() {
        try {
            InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("AwsCredentials.properties");
            Preconditions.checkNotNull(credentialsAsStream, "File 'AwsCredentials.properties' NOT found in the classpath");
            AWSCredentials credentials = new PropertiesCredentials(credentialsAsStream);
            ec2 = new AmazonEC2Client(credentials);
            ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
            rds = new AmazonRDSClient(credentials);
            rds.setEndpoint("rds.eu-west-1.amazonaws.com");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void generateDocs() {
        Filter filter = new Filter("tag:TrainingSession", Lists.newArrayList("Troubleshooting"));
        List<Reservation> reservations = ec2.describeInstances(new DescribeInstancesRequest().withFilters(filter)).getReservations();
        Iterable<Instance> instances = AmazonAwsUtils.toEc2Instances(reservations);

        Iterable<Instance> runningInstances = Iterables.filter(instances, AmazonAwsUtils.PREDICATE_RUNNING_OR_PENDING_INSTANCE);
        runningInstances = AmazonAwsUtils.awaitForEc2Instances(runningInstances, ec2);
        
        Map<String, Instance> runningInstancesByInstanceId = Maps.uniqueIndex(runningInstances,
                AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID);
        
        List<String> runningInstanceIds = Lists.newArrayList(Iterables.transform(runningInstances,
                AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID));
        
        List<TagDescription> tags = ec2.describeTags(new DescribeTagsRequest().withFilters(new Filter("resource-id", runningInstanceIds)))
                .getTags();
        Map<String, Map<String, String>> tagsByInstanceId = new MapMaker().makeComputingMap(new Function<String, Map<String, String>>() {
            @Override
            public Map<String, String> apply(String instanceId) {
                return Maps.newHashMap();
            }
        });

        for (TagDescription tag : tags) {
            tagsByInstanceId.get(tag.getResourceId()).put(tag.getKey(), tag.getValue());
        }

        Map<String, Map<String, Object>> tomcatTagsPerTeamIdentifier = Maps.newHashMap();
        
        for(Map.Entry<String, Map<String, String>> entry: tagsByInstanceId.entrySet()) {
            String instanceId = entry.getKey();
            Map<String, String> instanceTags = entry.getValue();
            
            String teamIdentifier = instanceTags.get("TeamIdentifier");
            Instance tomcatInstance = runningInstancesByInstanceId.get(instanceId);
            
            Map<String, Object> tomcatTags = Maps.newHashMap();
            tomcatTags.putAll(instanceTags);
            tomcatTags.put("PublicDnsName", tomcatInstance.getPublicDnsName());
            tomcatTags.put("Instance", tomcatInstance);
            
            
            tomcatTagsPerTeamIdentifier.put(teamIdentifier, tomcatTags);
        }
        
        Map<String, Object> rootMap = Maps.newHashMap();
        rootMap.put("infrastructures", tomcatTagsPerTeamIdentifier);
        String wikiPage = FreemarkerUtils.generate(rootMap, "/fr/xebia/training/troubleshooting/wiki-page.fmt");
        System.out.println(wikiPage);
    }

    public void createTrainingInfrastructure() {
        String applicationId = "petclinic";
        String dbInstanceIdentifier = applicationId;
        String dbName = applicationId;
        String jdbcUsername = applicationId;
        String jdbcPassword = applicationId;

        // CREATE DATABASE
        DBInstance petclinicDatabase = createMySqlDatabaseInstanceIfNotExists(dbInstanceIdentifier, dbName, jdbcUsername, jdbcPassword);
        petclinicDatabase = AmazonAwsUtils.awaitForDbInstanceCreation(petclinicDatabase, rds);

        logger.info("MySQL instance: {}", petclinicDatabase);

        Collection<String> teamIdentifiers = Lists.newArrayList("clc", "plo");

        // CREATE TOMCAT SERVERS
        List<Instance> tomcatInstances = launchTomcatServers(petclinicDatabase, applicationId, jdbcUsername, jdbcPassword, teamIdentifiers);

        logger.info("Created {}", tomcatInstances);
        
        // GENERATE WIKI DOCUMENTATION PAGE
        generateDocs();

    }

    /**
     * Returns a base-64 version of the mime-multi-part cloud-init file to put
     * in the user-data attribute of the ec2 instance.
     * 
     * @param distribution
     * @param dbInstance
     * @param jdbcUsername
     * @param jdbcPassword
     * @param warUrl
     * @return
     */
    @Nonnull
    protected String buildCloudInitUserData(DBInstance dbInstance, String jdbcUsername, String jdbcPassword) {

        dbInstance = AmazonAwsUtils.awaitForDbInstanceCreation(dbInstance, rds);

        // SHELL SCRIPT
        Map<String, Object> rootMap = Maps.newHashMap();

        Map<String, String> systemProperties = Maps.newHashMap();

        String warUrl = "http://xebia-france.googlecode.com/svn/repository/maven2/fr/xebia/demo/xebia-petclinic/1.0.2/xebia-petclinic-1.0.2.war";
        String rootContext = "/petclinic";

        rootMap.put("warUrl", warUrl);
        rootMap.put("warName", rootContext + ".war");

        rootMap.put("systemProperties", systemProperties);
        String jdbcUrl = "jdbc:mysql://" + dbInstance.getEndpoint().getAddress() + ":" + dbInstance.getEndpoint().getPort() + "/"
                + dbInstance.getDBName();
        systemProperties.put("jdbc.url", jdbcUrl);
        systemProperties.put("jdbc.username", jdbcUsername);
        systemProperties.put("jdbc.password", jdbcPassword);

        String shellScript = FreemarkerUtils.generate(rootMap, "/fr/xebia/training/troubleshooting/provision_tomcat.py.fmt");

        // CLOUD CONFIG
        InputStream cloudConfigAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("fr/xebia/training/troubleshooting/cloud-config-amzn-linux-tomcat.txt");
        Preconditions.checkNotNull(cloudConfigAsStream,
                "'fr/xebia/training/troubleshooting/cloud-config-amzn-linux-tomcat.txt' not found in path");
        Readable cloudConfig = new InputStreamReader(cloudConfigAsStream);

        return CloudInitUserDataBuilder.start() //
                .addCloudConfig(cloudConfig) //
                .addShellScript(shellScript) //
                .buildBase64UserData();

    }

    public List<Instance> launchTomcatServers(DBInstance petclinicDatabase, String applicationId, String jdbcUsername, String jdbcPassword,
            Collection<String> teamIdentifiers) {

        AmazonAwsUtils.terminateInstancesByRole("training,troubleshooting,tomcat", ec2);

        String userData = buildCloudInitUserData(petclinicDatabase, jdbcUsername, jdbcPassword);

        // FIXME : use M1SMALL
        logger.warn("FIXME : use M1SMALL");

        // CREATE EC2 INSTANCES
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                .withInstanceType(InstanceType.T1Micro.toString())//
                .withImageId("ami-6c3f0d18") //
                .withMinCount(teamIdentifiers.size()) //
                .withMaxCount(teamIdentifiers.size()) //
                .withSecurityGroupIds("accept-all") //
                .withKeyName("training-troubleshooting") //
                .withUserData(userData) //

        ;
        RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
        List<Instance> instances = runInstances.getReservation().getInstances();

        // TAG EC2 INSTANCES
        Iterator<String> teamIdentifierIterator = teamIdentifiers.iterator();

        for (Instance instance : instances) {
            String teamIdentifier = teamIdentifierIterator.next();

            CreateTagsRequest createTagsRequest = new CreateTagsRequest();
            createTagsRequest.withResources(instance.getInstanceId()) //
                    .withTags(//
                            new Tag("Name", applicationId + "-" + teamIdentifier), //
                            new Tag("Role", "training,troubleshooting,tomcat"), //
                            new Tag("TeamIdentifier", teamIdentifier), //
                            new Tag("TrainingSession", "Troubleshooting"));
            ec2.createTags(createTagsRequest);

        }

        logger.info("Created {}", instances);

        return instances;

    }

    @Nonnull
    public DBInstance createMySqlDatabaseInstanceIfNotExists(String dbInstanceIdentifier, String dbName, String jdbcUsername,
            String jdbcPassword) {
        logger.info("ENFORCE DATABASE {}/{}", dbInstanceIdentifier, dbName);

        DescribeDBInstancesRequest describeDbInstanceRequest = new DescribeDBInstancesRequest()
                .withDBInstanceIdentifier(dbInstanceIdentifier);
        try {
            DescribeDBInstancesResult describeDBInstances = rds.describeDBInstances(describeDbInstanceRequest);
            if (describeDBInstances.getDBInstances().isEmpty()) {
                // unexpected, db does not exist but we expected a
                // DBInstanceNotFoundException
            } else {
                DBInstance dbInstance = Iterables.getFirst(describeDBInstances.getDBInstances(), null);
                logger.info("Database instance '" + dbInstanceIdentifier + "' already exists! Skip creation");
                return dbInstance;
            }
        } catch (DBInstanceNotFoundException e) {
            // good, db does not exist
        }

        CreateDBInstanceRequest createDBInstanceRequest = new CreateDBInstanceRequest() //
                .withDBInstanceIdentifier(dbInstanceIdentifier) //
                .withDBName(dbName) //
                .withEngine("MySQL") //
                .withEngineVersion("5.1.57") //
                .withDBInstanceClass("db.m1.small") //
                .withMasterUsername(jdbcUsername) //
                .withMasterUserPassword(jdbcPassword) //
                .withAllocatedStorage(5) //
                .withBackupRetentionPeriod(0) //
                .withDBSecurityGroups("default") //
                .withLicenseModel("general-public-license") //
        ;

        DBInstance dbInstance = rds.createDBInstance(createDBInstanceRequest);
        logger.info("Created {}", dbInstance);
        return dbInstance;
    }

}
