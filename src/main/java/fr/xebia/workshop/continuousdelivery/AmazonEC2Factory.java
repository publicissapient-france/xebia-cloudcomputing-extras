package fr.xebia.workshop.continuousdelivery;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

public class AmazonEC2Factory {

    public AmazonEC2 createClient() {
        AWSCredentials credentials = AmazonAwsUtils.loadAwsCredentials();
        AmazonEC2 ec2 = new AmazonEC2Client(credentials);
        ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
        return ec2;
    }
}
