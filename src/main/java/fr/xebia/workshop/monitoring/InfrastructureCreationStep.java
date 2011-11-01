package fr.xebia.workshop.monitoring;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class InfrastructureCreationStep {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String NAGIOS_IMAGE_ID = "ami-ff1a278b";
    public static final String GRAPHITE_IMAGE_ID = "ami-e51d2091";

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
