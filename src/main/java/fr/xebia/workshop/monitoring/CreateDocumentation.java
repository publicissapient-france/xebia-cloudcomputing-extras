package fr.xebia.workshop.monitoring;

import com.amazonaws.services.ec2.AmazonEC2;

import java.util.Collection;

public class CreateDocumentation implements InfrastructureCreationListener {

    @Override
    public void infrastructureCreated(AmazonEC2 ec2, WorkshopInfrastructure infra) throws Exception {
        Collection<TeamInfrastructure> teamsInfrastructures = new InfrastructureTopologyScanner(ec2, infra).scan();

        new DocumentationGenerator().generateDocs(teamsInfrastructures, "/tmp/monitoring/");
    }
}
