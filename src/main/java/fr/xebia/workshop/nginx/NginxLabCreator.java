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

package fr.xebia.workshop.nginx;

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

/**
 * This class creates one Nginx instance per team and two BeanStalk cocktail-app per team
 *
 *
 *
 * User: slm
 * Date: 02/11/12
 * Time: 17:14
 */
public class NginxLabCreator {

    private static final Logger log = LoggerFactory.getLogger(NginxLabCreator.class);

    public static void main (String [] args){

        AWSCredentials awsCredentials = AmazonAwsUtils.loadAwsCredentials();
        AmazonEC2 ec2 = new AmazonEC2Client(awsCredentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

        AmazonRoute53 route53 = new AmazonRoute53Client(awsCredentials);

        WorkshopInfrastructure workshopInfrastructure = new WorkshopInfrastructure()
                .withTeamIdentifiers("1"/*, "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"*/)
                .withAwsAccessKeyId(awsCredentials.getAWSAccessKeyId())
                .withAwsSecretKey(awsCredentials.getAWSSecretKey())
                .withKeyPairName("nginx-workshop")
                .withBeanstalkNotificationEmail("slemesle@xebia.fr");

        CreateNginxProxyServers job = new CreateNginxProxyServers(ec2, route53, workshopInfrastructure);
        job.run();

        log.info("All Nginx instance created DNS is http://www-nginx-${team}.aws.xebiatechevent.info/");



        AWSElasticBeanstalk beanstalk = new AWSElasticBeanstalkClient(awsCredentials);
        beanstalk.setEndpoint("elasticbeanstalk.eu-west-1.amazonaws.com");
        CreateTomcat createTomcat = new CreateTomcat(beanstalk, workshopInfrastructure);
        createTomcat.createServers();

        log.info("All BeanStalk instance created Route53 is http://xfr-cocktail-nginx-${team}-[1/2].elasticbeanstalk.com/");
    }


}
