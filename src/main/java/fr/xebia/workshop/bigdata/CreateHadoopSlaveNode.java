package fr.xebia.workshop.bigdata;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.route53.AmazonRoute53;

public class CreateHadoopSlaveNode extends CreateHadoopServers {

    public static final String FLUME_HADOOP_SLAVE_TEAM = "flume-hadoop-slave-team-";

    public CreateHadoopSlaveNode(AmazonEC2 ec2, AmazonRoute53 route53, WorkshopInfrastructure workshopInfrastructure) {
        super(ec2, route53, workshopInfrastructure);
    }

    @Override
    protected String getAMI() {
        return "ami-aba9afdf";
    }

    @Override
    protected String getTagRole() {
        return "flume-hadoop";
    }

    @Override
    protected String getCnamePrefix() {
        return FLUME_HADOOP_SLAVE_TEAM;
    }
}
