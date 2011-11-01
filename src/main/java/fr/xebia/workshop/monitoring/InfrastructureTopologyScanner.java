package fr.xebia.workshop.monitoring;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsFunctions;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;

public class InfrastructureTopologyScanner {

    private final WorkshopInfrastructure workshopInfrastructure;
    private final AmazonEC2 ec2;

    public InfrastructureTopologyScanner(AmazonEC2 ec2, WorkshopInfrastructure infra) {
        this.ec2 = ec2;
        this.workshopInfrastructure = infra;
    }

    public Collection<TeamInfrastructure> scan() {
        Filter filter = new Filter("tag:Workshop", newArrayList("monitoring"));
        List<Reservation> reservations = ec2.describeInstances(new DescribeInstancesRequest().withFilters(filter)).getReservations();

        Iterable<Instance> instances = AmazonAwsUtils.toEc2Instances(reservations);

        Iterable<Instance> runningInstances = Iterables.filter(instances, AmazonAwsUtils.PREDICATE_RUNNING_OR_PENDING_INSTANCE);
        runningInstances = AmazonAwsUtils.awaitForEc2Instances(runningInstances, ec2);

        Map<String, Instance> runningInstancesByInstanceId = Maps.uniqueIndex(runningInstances,
                AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID);

        List<String> runningInstanceIds = newArrayList(Iterables.transform(runningInstances,
                AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID));

        List<TagDescription> tags = ec2.describeTags(new DescribeTagsRequest().withFilters(new Filter("resource-id", runningInstanceIds)))
                .getTags();
        Map<String, Map<String, String>> tagsByInstanceId = new MapMaker().makeComputingMap(new Function<String, Map<String, String>>() {
            @Override
            public Map<String, String> apply(String instanceId) {
                return Maps.newHashMap();
            }
        });

        for (TagDescription tag : tags) {
            tagsByInstanceId.get(tag.getResourceId()).put(tag.getKey(), tag.getValue());
        }

        Map<String, TeamInfrastructure> teamInfrastructureByTeamIdentifier = new MapMaker()
                .makeComputingMap(new Function<String, TeamInfrastructure>() {
                    @Override
                    public TeamInfrastructure apply(String teamIdentifier) {
                        return new TeamInfrastructure(workshopInfrastructure, teamIdentifier);
                    }
                });

        Instance nexusServer = null;

        for (Map.Entry<String, Map<String, String>> entry : tagsByInstanceId.entrySet()) {
            Map<String, String> instanceTags = entry.getValue();
            String instanceId = entry.getKey();
            Instance instance = runningInstancesByInstanceId.get(instanceId);
            String teamIdentifier = instanceTags.get("TeamIdentifier");

            if (teamIdentifier == null) {

                // not a per team server (e.g. Nexus server)

            } else {

                TeamInfrastructure teamInfrastructure = teamInfrastructureByTeamIdentifier.get(teamIdentifier);
                teamInfrastructure.addInstance(instance, instanceTags);
            }
        }
        Collection<TeamInfrastructure> teamInfrastructures = teamInfrastructureByTeamIdentifier.values();
        return teamInfrastructures;
    }

}
