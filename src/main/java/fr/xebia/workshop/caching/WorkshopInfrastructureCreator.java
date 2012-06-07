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
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:cleclerc@xebia.fr">Cyrille Le Clerc</a>
 */
public class WorkshopInfrastructureCreator {

    private final static Logger logger = LoggerFactory.getLogger(WorkshopInfrastructureCreator.class);

    public static void main(String[] args) {

        // AMAZON SERVICES
        AWSCredentials awsCredentials = AmazonAwsUtils.loadAwsCredentials();
        AmazonEC2 ec2 = new AmazonEC2Client(awsCredentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

        AWSElasticBeanstalk beanstalk = new AWSElasticBeanstalkClient(awsCredentials);
        beanstalk.setEndpoint("elasticbeanstalk.eu-west-1.amazonaws.com");

        AmazonRoute53 route53 = new AmazonRoute53Client(awsCredentials);


        ExecutorService executor = Executors.newFixedThreadPool(3);

        // WORKSHOP CONFIGURATION
        WorkshopInfrastructure workshopInfrastructure = new WorkshopInfrastructure()
                .withTeamIdentifiers("1", "2" /*, "3", "4", "5", "6", "7", "8", "9", "10", "11", "12" */)
                .withAwsAccessKeyId(awsCredentials.getAWSAccessKeyId())
                .withAwsSecretKey(awsCredentials.getAWSSecretKey())
                .withKeyPairName("web-caching-workshop")
                .withBeanstalkNotificationEmail("cleclerc@xebia.fr");

        // CREATE WORKSHOP INFRASTRUCTURE
        CreateCachingProxyServers createCachingProxyServers = new CreateCachingProxyServers(ec2, route53, workshopInfrastructure);
        executor.execute(createCachingProxyServers);

        CreateTomcat createTomcat = new CreateTomcat(beanstalk, workshopInfrastructure);
        executor.execute(createTomcat);

        executor.shutdown();

        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("Workshop infrastructure created");
    }
}
