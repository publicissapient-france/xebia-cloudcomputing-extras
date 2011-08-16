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
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.commons.lang.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.AppCookieStickinessPolicy;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLBCookieStickinessPolicyRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerPolicyRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.DisableAvailabilityZonesForLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.EnableAvailabilityZonesForLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.LBCookieStickinessPolicy;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException;
import com.amazonaws.services.elasticloadbalancing.model.Policies;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.SetLoadBalancerPoliciesOfListenerRequest;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.CreateDBInstanceRequest;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceNotFoundException;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;
import fr.xebia.cloud.cloudinit.FreemarkerUtils;

/**
 * <p>
 * Builds a java petclinic infrastructure on Amazon EC2.
 * </p>
 * <p>
 * creates:
 * <ul>
 * <li>1 MySQL database</li>
 * <li>several Tomcat / xebia-pet-clinic servers connected to the mysql database
 * (connected via the injection of the jdbc parameters in catalina.properties
 * via cloud-init)</li>
 * <li>1 load balancer</li>
 * </ul>
 * </p>
 * 
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class AmazonAwsPetclinicInfrastructureEnforcer {

    public enum Distribution {
        /**
         * <a href="http://aws.amazon.com/amazon-linux-ami/">Amazon Linux
         * AMI</a>
         */
        AMZN_LINUX("ami-47cefa33", "cloud-config-amzn-linux.txt", "/usr/share/tomcat6", InstanceType.T1Micro), //
        /**
         * <a href="http://cloud.ubuntu.com/ami/">Ubuntu Natty (11.04) AMI</a>
         */
        UBUNTU_11_04("ami-359ea941", "cloud-config-ubuntu-11.04.txt", "/var/lib/tomcat6", InstanceType.M1Small), //
        /**
         * <a href="http://cloud.ubuntu.com/ami/">Ubuntu Oneiric (11.10) AMI</a>
         */
        UBUNTU_11_10("ami-0aa7967e", "cloud-config-ubuntu-11.10.txt", "/var/lib/tomcat7", InstanceType.M1Small);

        private final static Map<String, Distribution> DISTRIBUTIONS_BY_AMI_ID = Maps.uniqueIndex(Arrays.asList(Distribution.values()),
                new Function<Distribution, String>() {
                    @Override
                    public String apply(Distribution distribution) {
                        return distribution.getAmiId();
                    }
                });

        @Nonnull
        public static Distribution fromAmiId(@Nonnull String amiId) throws NullPointerException, IllegalArgumentException {
            Distribution distribution = DISTRIBUTIONS_BY_AMI_ID.get(Preconditions.checkNotNull(amiId, "amiId is null"));
            Preconditions.checkArgument(distribution != null, "No distribution found for amiId '%s'", amiId);
            return distribution;
        }

        private final String amiId;

        private final String catalinaBase;

        private final String cloudConfigFilePath;

        private final InstanceType instanceType;

        /**
         * ID of the AMI in the eu-west-1 region.
         */
        private Distribution(@Nonnull String amiId, @Nonnull String cloudConfigFilePath, @Nonnull String catalinaBase,
                @Nonnull InstanceType instanceType) {
            this.amiId = amiId;
            this.cloudConfigFilePath = cloudConfigFilePath;
            this.catalinaBase = catalinaBase;
            this.instanceType = instanceType;
        }

        /**
         * ID of the AMI in the eu-west-1 region.
         */
        @Nonnull
        public String getAmiId() {
            return amiId;
        }

        /**
         * <p>
         * "catalina_base" folder.
         * </p>
         * <p>
         * Differs between redhat/ubuntu and between versions.
         * </p>
         * <p>
         * e.g."/var/lib/tomcat7", "/usr/share/tomcat6".
         * </p>
         */
        @Nonnull
        public String getCatalinaBase() {
            return catalinaBase;
        }

        /**
         * <p>
         * Classpath relative path to the "cloud-config" file.
         * </p>
         * <p>
         * "cloud-config" files differ between distributions due to the
         * different name of the packages and the different versions available.
         * </p>
         * <p>
         * e.g."cloud-config-ubuntu-10.04.txt", "cloud-config-redhat-5.txt".
         * </p>
         */
        @Nonnull
        public String getCloudConfigFilePath() {
            return cloudConfigFilePath;
        }

        @Nonnull
        public InstanceType getInstanceType() {
            return instanceType;
        }

    }

    protected final static String AMI_CUSTOM_LINUX_SUN_JDK6_TOMCAT7 = "ami-44506030";

    /**
     * Extract the {@link Placement#getAvailabilityZone()} zone of the given ec2
     * <code>instance</code> .
     */
    public static final Function<Instance, String> EC2_INSTANCE_TO_AVAILABILITY_ZONE = new Function<Instance, String>() {
        @Override
        public String apply(Instance instance) {
            return instance.getPlacement().getAvailabilityZone();
        }
    };

    /**
     * Extract the {@link Instance#getInstanceId()} of the given ec2
     * <code>instance</code>.
     */
    public final static Function<Instance, String> EC2_INSTANCE_TO_INSTANCE_ID = new Function<Instance, String>() {
        @Override
        public String apply(Instance instance) {
            return instance.getInstanceId();
        }
    };

    /**
     * Extract the
     * {@link com.amazonaws.services.elasticloadbalancing.model.Instance#getInstanceId()}
     * of the given elb <code>instance</code>.
     */
    public final static Function<com.amazonaws.services.elasticloadbalancing.model.Instance, String> ELB_INSTANCE_TO_INSTANCE_ID = new Function<com.amazonaws.services.elasticloadbalancing.model.Instance, String>() {
        @Override
        public String apply(com.amazonaws.services.elasticloadbalancing.model.Instance instance) {
            return instance.getInstanceId();
        }
    };

    /**
     * Converts the given <code>instanceId</code> into an elb
     * {@link com.amazonaws.services.elasticloadbalancing.model.Instance}.
     */
    public final static Function<String, com.amazonaws.services.elasticloadbalancing.model.Instance> INSTANCE_ID_TO_ELB_INSTANCE = new Function<String, com.amazonaws.services.elasticloadbalancing.model.Instance>() {
        @Override
        public com.amazonaws.services.elasticloadbalancing.model.Instance apply(String instanceId) {
            return new com.amazonaws.services.elasticloadbalancing.model.Instance(instanceId);
        }
    };

    public static void main(String[] args) throws Exception {
        AmazonAwsPetclinicInfrastructureEnforcer infrastructureMaker = new AmazonAwsPetclinicInfrastructureEnforcer();
        infrastructureMaker.createPetclinicInfrastructure(Distribution.AMZN_LINUX);

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

    protected final AmazonEC2 ec2;

    protected final AmazonElasticLoadBalancing elb;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final AmazonRDS rds;

    @VisibleForTesting
    protected AmazonAwsPetclinicInfrastructureEnforcer(AmazonEC2 ec2, AmazonElasticLoadBalancing elb, AmazonRDS rds) {
        super();
        this.ec2 = ec2;
        this.elb = elb;
        this.rds = rds;
    }

    public AmazonAwsPetclinicInfrastructureEnforcer() {
        try {
            InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("AwsCredentials.properties");
            Preconditions.checkNotNull(credentialsAsStream, "File 'AwsCredentials.properties' NOT found in the classpath");
            AWSCredentials credentials = new PropertiesCredentials(credentialsAsStream);
            ec2 = new AmazonEC2Client(credentials);
            ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
            rds = new AmazonRDSClient(credentials);
            rds.setEndpoint("rds.eu-west-1.amazonaws.com");
            elb = new AmazonElasticLoadBalancingClient(credentials);
            elb.setEndpoint("elasticloadbalancing.eu-west-1.amazonaws.com");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

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
     * @return up to date 'available' dbInstance
     * @throws DBInstanceNotFoundException
     *             the given <code>dbInstance</code> no longer exists (e.g. it
     *             has been destroyed, etc).
     */
    @Nonnull
    public DBInstance awaitForDbInstanceCreation(@Nonnull DBInstance dbInstance) throws DBInstanceNotFoundException {
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
    protected String buildCloudInitUserData(Distribution distribution, DBInstance dbInstance, String jdbcUsername, String jdbcPassword,
            String warUrl, String rootContext) {

        // SHELL SCRIPT
        Map<String, Object> rootMap = Maps.newHashMap();
        rootMap.put("catalinaBase", distribution.getCatalinaBase());
        rootMap.put("warUrl", warUrl);
        rootMap.put("warName", rootContext + ".war");

        Map<String, String> systemProperties = Maps.newHashMap();
        rootMap.put("systemProperties", systemProperties);
        String jdbcUrl = "jdbc:mysql://" + dbInstance.getEndpoint().getAddress() + ":" + dbInstance.getEndpoint().getPort() + "/"
                + dbInstance.getDBName();
        systemProperties.put("jdbc.url", jdbcUrl);
        systemProperties.put("jdbc.username", jdbcUsername);
        systemProperties.put("jdbc.password", jdbcPassword);

        String shellScript = FreemarkerUtils.generate(rootMap, "/provision_tomcat.py.fmt");

        // CLOUD CONFIG
        InputStream cloudConfigAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(distribution.getCloudConfigFilePath());
        Preconditions.checkNotNull(cloudConfigAsStream, "'" + distribution.getCloudConfigFilePath() + "' not found in path");
        Readable cloudConfig = new InputStreamReader(cloudConfigAsStream);

        return CloudInitUserDataBuilder.start() //
                .addShellScript(shellScript) //
                .addCloudConfig(cloudConfig) //
                .buildBase64UserData();

    }

    @Nonnull
    public DBInstance createMySqlDatabaseInstanceIfNotExists(String dbInstanceIdentifier, String dbName, String jdbcUserName,
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
                .withMasterUsername(jdbcUserName) //
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

    public void createPetclinicInfrastructure(Distribution... distributions) {

        String applicationId = "petclinic";
        String rootContext = "/petclinic";
        String warUrl = "http://xebia-france.googlecode.com/svn/repository/maven2/fr/xebia/demo/xebia-petclinic/1.0.2/xebia-petclinic-1.0.2.war";
        String healthCheckUri = rootContext + "/healthcheck.jsp";

        createTomcatMySqlInfrastructure(applicationId, rootContext, warUrl, healthCheckUri, distributions);

    }

    void createTomcatMySqlInfrastructure(String applicationId, String rootContext, String warUrl, String healthCheckUri,
            Distribution... distributions) {
        String dbInstanceIdentifier = applicationId;
        String dbName = applicationId;
        String jdbcUsername = applicationId;
        String jdbcPassword = applicationId;

        DBInstance dbInstance = createMySqlDatabaseInstanceIfNotExists(dbInstanceIdentifier, dbName, jdbcUsername, jdbcPassword);
        dbInstance = awaitForDbInstanceCreation(dbInstance);
        logger.info("MySQL instance: " + dbInstance);

        List<Instance> tomcatInstances = createTomcatServers(dbInstance, applicationId, jdbcUsername, jdbcPassword, warUrl, rootContext,
                distributions);
        logger.info("EC2 instances: " + tomcatInstances);
        LoadBalancerDescription loadBalancerDescription = createOrUpdateElasticLoadBalancer(healthCheckUri, applicationId);
        logger.info("Load Balancer DNS name: " + loadBalancerDescription.getDNSName());

        // PRINT INFRASTRUCTURE
        System.out.println("DATABASE");
        System.out.println("========");
        String jdbcUrl = "jdbc:mysql://" + dbInstance.getEndpoint().getAddress() + ":" + dbInstance.getEndpoint().getPort() + "/"
                + dbInstance.getDBName();
        System.out.println("jdbc.url= " + jdbcUrl);
        System.out.println("jdbc.username= " + jdbcUsername);
        System.out.println("jdbc.password= " + jdbcPassword);
        System.out.println();

        System.out.println("TOMCAT SERVERS");
        System.out.println("==============");
        tomcatInstances = awaitForEc2Instances(tomcatInstances);
        for (Instance instance : tomcatInstances) {
            System.out.println("http://" + instance.getPublicDnsName() + ":8080" + rootContext);
        }

        System.out.println("LOAD BALANCER");
        System.out.println("=============");
        System.out.println("http://" + loadBalancerDescription.getDNSName() + rootContext);
    }

    /**
     * 
     * @param healthCheckUri
     *            start with slash. E.g. "/myapp/healthcheck.jsp
     * @param applicationIdentifier
     *            used to name the load balancer and to filter the instances on
     *            their "Role" tag.
     * @return created load balancer description
     */
    @Nonnull
    public LoadBalancerDescription createOrUpdateElasticLoadBalancer(@Nonnull String healthCheckUri, @Nonnull String applicationIdentifier) {
        logger.info("ENFORCE LOAD BALANCER");

        DescribeInstancesRequest describeInstancesWithRoleRequest = new DescribeInstancesRequest(). //
                withFilters(new Filter("tag:Role", Arrays.asList(applicationIdentifier)));
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesWithRoleRequest);

        Iterable<Instance> expectedEc2Instances = toEc2Instances(describeInstancesResult.getReservations());

        Set<String> expectedAvailabilityZones = Sets.newHashSet(Iterables
                .transform(expectedEc2Instances, EC2_INSTANCE_TO_AVAILABILITY_ZONE));
        Listener expectedListener = new Listener("HTTP", 80, 8080);

        String loadBalancerName = applicationIdentifier;

        LoadBalancerDescription actualLoadBalancerDescription;
        try {
            DescribeLoadBalancersResult describeLoadBalancers = elb.describeLoadBalancers(new DescribeLoadBalancersRequest(Arrays
                    .asList(loadBalancerName)));
            if (describeLoadBalancers.getLoadBalancerDescriptions().isEmpty()) {
                // unexpected, this should have been a
                // LoadBalancerNotFoundException
                actualLoadBalancerDescription = null;
            } else {
                // re-query to get updated config

                actualLoadBalancerDescription = Iterables.getFirst(describeLoadBalancers.getLoadBalancerDescriptions(), null);
            }
        } catch (LoadBalancerNotFoundException e) {
            actualLoadBalancerDescription = null;
        }

        Set<String> actualAvailabilityZones;
        Set<String> actualInstanceIds;
        Policies actualPolicies;
        HealthCheck actualHealthCheck;
        ListenerDescription actualListenerDescription = null;
        if (actualLoadBalancerDescription == null) {
            CreateLoadBalancerRequest createLoadBalancerRequest = new CreateLoadBalancerRequest() //
                    .withLoadBalancerName(loadBalancerName) //
                    .withAvailabilityZones(expectedAvailabilityZones) //
                    .withListeners(expectedListener);
            elb.createLoadBalancer(createLoadBalancerRequest);

            actualListenerDescription = new ListenerDescription().withListener(expectedListener);
            actualAvailabilityZones = expectedAvailabilityZones;
            actualInstanceIds = Collections.emptySet();
            actualHealthCheck = new HealthCheck();
            actualPolicies = new Policies();
        } else {
            // check listeners
            List<ListenerDescription> actualListenerDescriptions = actualLoadBalancerDescription.getListenerDescriptions();
            boolean loadBalancerMustBeRecreated;

            if (actualListenerDescriptions.size() == 1) {
                actualListenerDescription = Iterables.getOnlyElement(actualListenerDescriptions);
                Listener actualListener = actualListenerDescription.getListener();
                if (ObjectUtils.equals(expectedListener.getProtocol(), actualListener.getProtocol()) && //
                        ObjectUtils.equals(expectedListener.getLoadBalancerPort(), actualListener.getLoadBalancerPort()) && //
                        ObjectUtils.equals(expectedListener.getInstancePort(), actualListener.getInstancePort())) {
                    loadBalancerMustBeRecreated = false;
                } else {
                    loadBalancerMustBeRecreated = true;
                }
            } else {
                loadBalancerMustBeRecreated = true;
            }

            if (loadBalancerMustBeRecreated) {
                logger.info("Recreate miss configured load balancer actualListeners:{}, expectedListener:{}", actualListenerDescriptions,
                        expectedListener);
                elb.deleteLoadBalancer(new DeleteLoadBalancerRequest(loadBalancerName));
                return createOrUpdateElasticLoadBalancer(healthCheckUri, applicationIdentifier);
            }

            //
            actualAvailabilityZones = Sets.newHashSet(actualLoadBalancerDescription.getAvailabilityZones());
            actualInstanceIds = Sets.newHashSet(Iterables.transform(actualLoadBalancerDescription.getInstances(),
                    ELB_INSTANCE_TO_INSTANCE_ID));

            actualHealthCheck = actualLoadBalancerDescription.getHealthCheck();

            actualPolicies = actualLoadBalancerDescription.getPolicies();
        }

        // HEALTH CHECK
        if (!healthCheckUri.startsWith("/")) {
            healthCheckUri = "/" + healthCheckUri;
        }

        HealthCheck expectedHealthCheck = new HealthCheck() //
                .withTarget("HTTP:8080" + healthCheckUri) //
                .withHealthyThreshold(2) //
                .withUnhealthyThreshold(2) //
                .withInterval(30) //
                .withTimeout(2);
        if (Objects.equal(expectedHealthCheck.getTarget(), actualHealthCheck.getTarget()) && //
                Objects.equal(expectedHealthCheck.getHealthyThreshold(), actualHealthCheck.getHealthyThreshold()) && //
                Objects.equal(expectedHealthCheck.getInterval(), actualHealthCheck.getInterval()) && //
                Objects.equal(expectedHealthCheck.getTimeout(), actualHealthCheck.getTimeout()) && //
                Objects.equal(expectedHealthCheck.getUnhealthyThreshold(), actualHealthCheck.getHealthyThreshold())) {
            // health check is ok
        } else {
            logger.info("Set Healthcheck: " + expectedHealthCheck);
            elb.configureHealthCheck(new ConfigureHealthCheckRequest(loadBalancerName, expectedHealthCheck));
        }

        // AVAILABILITY ZONES
        // enable
        Iterable<String> availabilityZonesToEnable = Sets.difference(expectedAvailabilityZones, actualAvailabilityZones);
        logger.info("Enable availability zones: " + availabilityZonesToEnable);
        if (!Iterables.isEmpty(availabilityZonesToEnable)) {
            elb.enableAvailabilityZonesForLoadBalancer(new EnableAvailabilityZonesForLoadBalancerRequest(loadBalancerName, Lists
                    .newArrayList(availabilityZonesToEnable)));
        }

        // disable
        Iterable<String> availabilityZonesToDisable = Sets.difference(actualAvailabilityZones, expectedAvailabilityZones);
        logger.info("Disable availability zones: " + availabilityZonesToDisable);
        if (!Iterables.isEmpty(availabilityZonesToDisable)) {
            elb.disableAvailabilityZonesForLoadBalancer(new DisableAvailabilityZonesForLoadBalancerRequest(loadBalancerName, Lists
                    .newArrayList(availabilityZonesToDisable)));
        }

        // STICKINESS
        List<AppCookieStickinessPolicy> appCookieStickinessPoliciesToDelete = actualPolicies.getAppCookieStickinessPolicies();
        logger.info("Delete app cookie stickiness policies:" + appCookieStickinessPoliciesToDelete);
        for (AppCookieStickinessPolicy appCookieStickinessPolicyToDelete : appCookieStickinessPoliciesToDelete) {
            elb.deleteLoadBalancerPolicy(new DeleteLoadBalancerPolicyRequest(loadBalancerName, appCookieStickinessPolicyToDelete
                    .getPolicyName()));
        }

        final LBCookieStickinessPolicy expectedLbCookieStickinessPolicy = new LBCookieStickinessPolicy(applicationIdentifier
                + "-stickiness-policy", null);
        Predicate<LBCookieStickinessPolicy> isExpectedPolicyPredicate = new Predicate<LBCookieStickinessPolicy>() {
            @Override
            public boolean apply(LBCookieStickinessPolicy lbCookieStickinessPolicy) {
                return Objects.equal(expectedLbCookieStickinessPolicy.getPolicyName(), lbCookieStickinessPolicy.getPolicyName()) && //
                        Objects.equal(expectedLbCookieStickinessPolicy.getCookieExpirationPeriod(),
                                lbCookieStickinessPolicy.getCookieExpirationPeriod());
            }
        };
        Collection<LBCookieStickinessPolicy> lbCookieStickinessPoliciesToDelete = Collections2.filter(
                actualPolicies.getLBCookieStickinessPolicies(), Predicates.not(isExpectedPolicyPredicate));
        logger.info("Delete lb cookie stickiness policies: " + lbCookieStickinessPoliciesToDelete);
        for (LBCookieStickinessPolicy lbCookieStickinessPolicy : lbCookieStickinessPoliciesToDelete) {
            elb.deleteLoadBalancerPolicy(new DeleteLoadBalancerPolicyRequest(loadBalancerName, lbCookieStickinessPolicy.getPolicyName()));
        }

        Collection<LBCookieStickinessPolicy> matchingLbCookieStyckinessPolicy = Collections2.filter(
                actualPolicies.getLBCookieStickinessPolicies(), isExpectedPolicyPredicate);
        if (matchingLbCookieStyckinessPolicy.isEmpty()) {
            // COOKIE STICKINESS
            CreateLBCookieStickinessPolicyRequest createLbCookieStickinessPolicy = new CreateLBCookieStickinessPolicyRequest() //
                    .withLoadBalancerName(loadBalancerName) //
                    .withPolicyName(expectedLbCookieStickinessPolicy.getPolicyName()) //
                    .withCookieExpirationPeriod(expectedLbCookieStickinessPolicy.getCookieExpirationPeriod());
            logger.info("Create LBCookieStickinessPolicy: " + createLbCookieStickinessPolicy);
            elb.createLBCookieStickinessPolicy(createLbCookieStickinessPolicy);

        } else {
            // what ?
        }

        // TODO verify load balancer policy is associated with the listener
        List<String> expectedListenerDescriptionPolicyNames = Lists.newArrayList(expectedLbCookieStickinessPolicy.getPolicyName());

        boolean mustOverWriteListenerPolicy = !ObjectUtils.equals(expectedListenerDescriptionPolicyNames,
                actualListenerDescription.getPolicyNames());

        if (mustOverWriteListenerPolicy) {

            SetLoadBalancerPoliciesOfListenerRequest setLoadBalancerPoliciesOfListenerRequest = new SetLoadBalancerPoliciesOfListenerRequest() //
                    .withLoadBalancerName(loadBalancerName) //
                    .withLoadBalancerPort(expectedListener.getLoadBalancerPort()) //
                    .withPolicyNames(expectedLbCookieStickinessPolicy.getPolicyName());
            logger.debug("setLoadBalancerPoliciesOfListener: {}", setLoadBalancerPoliciesOfListenerRequest);
            elb.setLoadBalancerPoliciesOfListener(setLoadBalancerPoliciesOfListenerRequest);
        }

        // INSTANCES
        Set<String> expectedEc2InstanceIds = Sets.newHashSet(Iterables.transform(expectedEc2Instances, EC2_INSTANCE_TO_INSTANCE_ID));
        // register
        Iterable<String> instanceIdsToRegister = Sets.difference(expectedEc2InstanceIds, actualInstanceIds);
        logger.info("Register " + applicationIdentifier + " instances: " + instanceIdsToRegister);
        if (!Iterables.isEmpty(instanceIdsToRegister)) {
            elb.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(loadBalancerName, Lists
                    .newArrayList(Iterables.transform(instanceIdsToRegister, INSTANCE_ID_TO_ELB_INSTANCE))));
        }

        // deregister
        Iterable<String> instanceIdsToDeregister = Sets.difference(actualInstanceIds, expectedEc2InstanceIds);
        logger.info("Deregister " + applicationIdentifier + " instances: " + instanceIdsToDeregister);
        if (!Iterables.isEmpty(instanceIdsToDeregister)) {
            elb.deregisterInstancesFromLoadBalancer(new DeregisterInstancesFromLoadBalancerRequest(loadBalancerName, Lists
                    .newArrayList(Iterables.transform(instanceIdsToDeregister, INSTANCE_ID_TO_ELB_INSTANCE))));
        }

        // QUERY TO GET UP TO DATE LOAD BALANCER DESCRIPTION
        LoadBalancerDescription elasticLoadBalancerDescription = Iterables.getOnlyElement(elb.describeLoadBalancers(
                new DescribeLoadBalancersRequest(Arrays.asList(loadBalancerName))).getLoadBalancerDescriptions());

        return elasticLoadBalancerDescription;
    }

    public List<Instance> createTomcatServers(DBInstance dbInstance, String applicationIdentifier, String jdbcUsername,
            String jdbcPassword, String warUrl, String rootContext, Distribution... distributions) {
        logger.info("ENFORCE TOMCAT SERVERS");

        List<Instance> instances = Lists.newArrayList();
        for (Distribution distribution : distributions) {
            String userData = buildCloudInitUserData(distribution, dbInstance, jdbcUsername, jdbcPassword, warUrl, rootContext);

            // CREATE EC2 INSTANCES
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
                    .withInstanceType(distribution.getInstanceType().toString()) //
                    .withImageId(distribution.getAmiId()) //
                    .withMinCount(1) //
                    .withMaxCount(1) //
                    .withSecurityGroupIds("tomcat") //
                    .withKeyName("xebia-france") //
                    .withUserData(userData) //

            ;
            RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
            instances.addAll(runInstances.getReservation().getInstances());
        }

        // TAG EC2 INSTANCES
        int idx = 1;
        for (Instance instance : instances) {
            CreateTagsRequest createTagsRequest = new CreateTagsRequest();
            createTagsRequest.withResources(instance.getInstanceId()) //
                    .withTags(//
                            new Tag("Name", applicationIdentifier + "-" + idx), //
                            new Tag("Role", applicationIdentifier), //
                            new Tag("Distribution", Distribution.fromAmiId(instance.getImageId()).name()));
            ec2.createTags(createTagsRequest);

            idx++;
        }

        logger.info("Created {}", instances);

        return instances;
    }

    public void listDbInstances() {
        DescribeDBInstancesResult describeDBInstancesResult = rds.describeDBInstances();
        logger.info("db instances:");
        for (DBInstance dbInstance : describeDBInstancesResult.getDBInstances()) {
            logger.info(dbInstance.toString());
        }
    }
}
