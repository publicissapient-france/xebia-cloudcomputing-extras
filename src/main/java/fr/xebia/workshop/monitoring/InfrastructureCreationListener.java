package fr.xebia.workshop.monitoring;

import com.amazonaws.services.ec2.AmazonEC2;

public interface InfrastructureCreationListener {

    void infrastructureCreated(AmazonEC2 ec2, WorkshopInfrastructure workshopInfrastructure) throws Exception;
}