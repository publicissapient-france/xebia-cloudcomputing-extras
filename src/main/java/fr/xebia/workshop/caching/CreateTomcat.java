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
package fr.xebia.workshop.caching;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.*;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:cleclerc@xebia.fr">Cyrille Le Clerc</a>
 */
public class CreateTomcat {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private AWSElasticBeanstalk beanstalk;

    private WorkshopInfrastructure workshopInfrastructure;


    public static void main(String[] args) throws Exception {

        AWSCredentials awsCredentials = AmazonAwsUtils.loadAwsCredentials();
        AWSElasticBeanstalk beanstalk = new AWSElasticBeanstalkClient(awsCredentials);
        beanstalk.setEndpoint("elasticbeanstalk.eu-west-1.amazonaws.com");

        WorkshopInfrastructure workshopInfrastructure = new WorkshopInfrastructure()
                .withTeamIdentifiers("clc"/*, "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"*/)
                .withAwsAccessKeyId(awsCredentials.getAWSAccessKeyId())
                .withAwsSecretKey(awsCredentials.getAWSSecretKey())
                .withKeyPairName("web-caching-workshop")
                .withBeanstalkNotificationEmail("cleclerc@xebia.fr");
        CreateTomcat createTomcat = new CreateTomcat(beanstalk, workshopInfrastructure);
        createTomcat.createServers();
    }

    public CreateTomcat(AWSElasticBeanstalk beanstalk, WorkshopInfrastructure workshopInfrastructure) {
        this.beanstalk = beanstalk;
        this.workshopInfrastructure = workshopInfrastructure;
    }

    public void createServers() {


        String applicationName = "xfr-cocktail";


        // CREATE APPLICATION
        AmazonAwsUtils.deleteBeanstalkApplicationIfExists(applicationName, beanstalk);
        CreateApplicationRequest createApplicationRequest = new CreateApplicationRequest()
                .withApplicationName(applicationName)
                .withDescription("xfr-cocktail app");

        ApplicationDescription applicationDescription = beanstalk.createApplication(createApplicationRequest).getApplication();
        logger.debug("Application {} created", applicationDescription.getApplicationName());

        // CREATE APPLICATION VERSION
        CreateApplicationVersionRequest createApplicationVersionRequest = new CreateApplicationVersionRequest()
                .withApplicationName(applicationDescription.getApplicationName())
                .withVersionLabel("1.0.0")
                .withSourceBundle(new S3Location("xfr-workshop-caching", "cocktail-app-1.0.0-SNAPSHOT.war"));
        ApplicationVersionDescription applicationVersionDescription = beanstalk.createApplicationVersion(createApplicationVersionRequest).getApplicationVersion();
        logger.debug("Application version {}:{} created", applicationVersionDescription.getApplicationName(), applicationVersionDescription.getVersionLabel());

        /*
          {"Namespace": "",
   "OptionName": "",
   "Value": "someone@example.com"}
         */
        // CREATE CONFIGURATION TEMPLATE
        CreateConfigurationTemplateRequest createConfigurationTemplateRequest = new CreateConfigurationTemplateRequest()
                .withApplicationName(applicationDescription.getApplicationName())
                .withTemplateName(applicationDescription.getApplicationName() + "-base-configuration")
                .withSolutionStackName("32bit Amazon Linux running Tomcat 7")
                .withOptionSettings(
                        new ConfigurationOptionSetting("aws:autoscaling:launchconfiguration", "InstanceType", "t1.micro"),
                        new ConfigurationOptionSetting("aws:autoscaling:launchconfiguration", "EC2KeyName", workshopInfrastructure.getKeyPairName()),

                        new ConfigurationOptionSetting("aws:elasticbeanstalk:sns:topics", "Notification Endpoint", workshopInfrastructure.getBeanstalkNotificationEmail()),

                        new ConfigurationOptionSetting("aws:elasticbeanstalk:application:environment", "AWS_ACCESS_KEY_ID", workshopInfrastructure.getAwsAccessKeyId()),
                        new ConfigurationOptionSetting("aws:elasticbeanstalk:application:environment", "AWS_SECRET_KEY", workshopInfrastructure.getAwsSecretKey()),
                        new ConfigurationOptionSetting("aws:elasticbeanstalk:application:environment", "aws_access_key", workshopInfrastructure.getAwsAccessKeyId()),
                        new ConfigurationOptionSetting("aws:elasticbeanstalk:application:environment", "aws_secret_key", workshopInfrastructure.getAwsSecretKey())
                );
        CreateConfigurationTemplateResult configurationTemplateResult = beanstalk.createConfigurationTemplate(createConfigurationTemplateRequest);
        logger.debug("Configuration {}:{} created", new Object[]{configurationTemplateResult.getApplicationName(), configurationTemplateResult.getTemplateName(), configurationTemplateResult});

        for (String teamIdentifier : workshopInfrastructure.getTeamIdentifiers()) {
            // CREATE ENVIRONMENT
            CreateEnvironmentRequest createEnvironmentRequest = new CreateEnvironmentRequest()
                    .withEnvironmentName(applicationDescription.getApplicationName() + "-" + teamIdentifier)
                    .withApplicationName(applicationDescription.getApplicationName())
                    .withVersionLabel(applicationVersionDescription.getVersionLabel())
                    .withCNAMEPrefix(applicationDescription.getApplicationName() + "-" + teamIdentifier)
                    .withTemplateName(configurationTemplateResult.getTemplateName());

            CreateEnvironmentResult createEnvironmentResult = beanstalk.createEnvironment(createEnvironmentRequest);

            logger.info("Environment {}:{}:{} created at {}", new Object[]{
                    createEnvironmentResult.getApplicationName(),
                    createEnvironmentResult.getVersionLabel(),
                    createEnvironmentResult.getEnvironmentName(),
                    createEnvironmentResult.getEndpointURL()});

        }
    }
}