package fr.xebia.workshop.bigdata;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.route53.AmazonRoute53;

public class CreateHadoopMasterNode extends CreateHadoopServers {

    public static final String FLUME_HADOOP_MASTER_TEAM = "flume-hadoop-master-team-";

    public CreateHadoopMasterNode(AmazonEC2 ec2, AmazonRoute53 route53, WorkshopInfrastructure workshopInfrastructure) {
        super(ec2, route53, workshopInfrastructure);
    }

    @Override
    protected String getAMI() {
        return "ami-31464745";
    }

    @Override
    protected String getTagRole() {
        return "flume-hadoop";
    }

    @Override
    protected String getCnamePrefix() {
        return FLUME_HADOOP_MASTER_TEAM;
    }
}
