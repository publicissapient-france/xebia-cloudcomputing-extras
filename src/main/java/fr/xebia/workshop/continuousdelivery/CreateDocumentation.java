package fr.xebia.workshop.continuousdelivery;

import java.util.Collection;

import com.amazonaws.services.ec2.AmazonEC2;

public class CreateDocumentation implements InfrastructureCreationListener {

    @Override
    public void infrastructureCreated(AmazonEC2 ec2, WorkshopInfrastructure infra) throws Exception {
        Collection<TeamInfrastructure> teamsInfrastructures = new InfrastructureTopologyScanner(ec2, infra).scan();

        new DocumentationGenerator().generateDocs(teamsInfrastructures, "/tmp/continuous-delivery/");
    }
}
