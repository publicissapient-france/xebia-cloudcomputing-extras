package fr.xebia.workshop.continuousdelivery;

import com.amazonaws.services.ec2.AmazonEC2;

public interface InfrastructureCreationListener {

    void infrastructureCreated(AmazonEC2 ec2, WorkshopInfrastructure workshopInfrastructure) throws Exception;
}