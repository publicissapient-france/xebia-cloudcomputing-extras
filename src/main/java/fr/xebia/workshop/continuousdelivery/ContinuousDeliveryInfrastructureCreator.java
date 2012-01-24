/*
 * Copyright 2008-2010 Xebia and the original author or authors.
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
package fr.xebia.workshop.continuousdelivery;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.AmazonEC2;

public class ContinuousDeliveryInfrastructureCreator {

    public static void main(String[] args) {

        final WorkshopInfrastructure infra = WorkshopInfrastructure.create()
                .withTeamIdentifiers(createIdentifiersForNumberOfTeams(2))
                //
                .withKeyPairName("continuous-delivery-workshop")
                //
                .withGithubGuestInfo("xebia-guest", "xebia-guest", "xebia42.*")
                .withGithubRepositoryPrefix("xebia-petclinic-lite-")
                .withGithubSourceRepository("http://github.com/xebia-france-training/xebia-petclinic-lite.git")
                //
                .withNexusPublicIp("176.34.185.30")
                .withNexusDomainName("nexus.xebia-tech-event.info")
                .build();

        // checks for Key in classpath: prevents launching instances if not present
        checkKeyFile(infra);

        final ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator(infra);
        creator.addStep(new CreateNexusInstance());
        creator.addStep(new CreateGithubRepositories(true));
        creator.addStep(new CreateJenkinsInstances());
        creator.addStep(new CreateTomcatServers("dev", 1));
        creator.addStep(new CreateTomcatServers("valid", 2));

        try {
            creator.createInfrastructure(new CreateDocumentation());
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(0);
    }

    private static Collection<String> createIdentifiersForNumberOfTeams(int teamCount) {
        Collection<String> teamIdentifiers = new ArrayList<String>(teamCount);
        for (int i = 1; i <= teamCount; i++) {
            teamIdentifiers.add(String.valueOf(i));
        }
        return teamIdentifiers;
    }

    private static void checkKeyFile(final WorkshopInfrastructure infra) {
        InputStream keyFile = Thread.currentThread().getContextClassLoader().getResourceAsStream(infra.getKeyPairName() + ".pem");
        checkState(keyFile != null, "File '" + infra.getKeyPairName() + ".pem' NOT found in the classpath");
    }

    private static final Logger logger = LoggerFactory.getLogger(ContinuousDeliveryInfrastructureCreator.class);

    private final AmazonEC2 ec2;
    private final WorkshopInfrastructure workshopInfrastructure;
    private final List<InfrastructureCreationStep> steps = newArrayList();

    public ContinuousDeliveryInfrastructureCreator(WorkshopInfrastructure workshopInfrastructure) {
        this.workshopInfrastructure = workshopInfrastructure;
        ec2 = new AmazonEC2Factory().createClient();
    }

    private void addStep(InfrastructureCreationStep step) {
        steps.add(step);
    }

    public void createInfrastructure(InfrastructureCreationListener listener) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(steps.size());

        for (final InfrastructureCreationStep step : steps) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        step.execute(ec2, workshopInfrastructure);
                    } catch (Exception e) {
                        logger.error("Exception executing step: " + step, e);
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);

        listener.infrastructureCreated(ec2, workshopInfrastructure);
    }

}
