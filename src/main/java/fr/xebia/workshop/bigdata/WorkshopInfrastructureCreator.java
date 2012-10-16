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
package fr.xebia.workshop.bigdata;

import static com.google.common.base.Preconditions.checkState;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

public class WorkshopInfrastructureCreator {

    private static final Logger logger = LoggerFactory
            .getLogger(WorkshopInfrastructureCreator.class);

    public static void main(String args[]) {

        AWSCredentials awsCredentials = AmazonAwsUtils.loadAwsCredentials();
        AmazonEC2 ec2 = new AmazonEC2Client(awsCredentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

        AmazonRoute53 route53 = new AmazonRoute53Client(awsCredentials);

        WorkshopInfrastructure workshopInfrastructure = new WorkshopInfrastructure()
               .withTeamIdentifiers("1" )
               //.withTeamIdentifiers("1"  ,"2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
                //.withTeamIdentifiers("1", "2", "3")
                .withAwsAccessKeyId(awsCredentials.getAWSAccessKeyId())
                .withAwsSecretKey(awsCredentials.getAWSSecretKey())
                .withKeyPairName("xte-flume");

        // checks for Key in classpath: prevents launching instances if not
        // present
        checkKeyFile(workshopInfrastructure);

        AmazonAwsUtils.terminateInstancesByWorkshop("flume-hadoop", ec2);

        ExecutorService executor = Executors.newCachedThreadPool();

        executor.execute(new CreateTomcatServers(ec2, route53, workshopInfrastructure));

        executor.execute(new CreateHadoopMasterNode(ec2, route53, workshopInfrastructure));

        executor.execute(new CreateHadoopSlaveNode(ec2, route53, workshopInfrastructure));

        executor.shutdown();

        try {
            executor.awaitTermination(60, TimeUnit.MINUTES);
            executor.shutdownNow();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        logger.info("Workshop infrastructure created");

    }

    protected static void checkKeyFile(final WorkshopInfrastructure infra) {
        InputStream keyFile = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(infra.getKeyPairName() + ".pem");
        checkState(keyFile != null, "File '" + infra.getKeyPairName()
                + ".pem' NOT found in the classpath");
    }
}
