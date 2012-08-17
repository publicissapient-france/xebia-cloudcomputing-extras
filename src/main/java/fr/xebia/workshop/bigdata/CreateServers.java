/*
 * Copyright 2008-2012 Xebia and the original author or authors.
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
package fr.xebia.workshop.bigdata;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.model.GetHostedZoneRequest;
import com.amazonaws.services.route53.model.HostedZone;
import com.google.common.collect.Maps;

import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

public abstract class CreateServers implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	protected AmazonEC2 ec2;

	protected AmazonRoute53 route53;

	protected HostedZone hostedZoneId;

	protected WorkshopInfrastructure workshopInfrastructure;

	public static final String XEBIA_TECH_EVENT_INFO_DOMAIN_NAME = "Z28O5PDK1WPCSR";

	protected CreateServers(AmazonEC2 ec2, AmazonRoute53 route53,
			WorkshopInfrastructure workshopInfrastructure) {
		this.ec2 = ec2;
		this.route53 = route53;
		this.workshopInfrastructure = workshopInfrastructure;
		this.hostedZoneId = route53.getHostedZone(
				new GetHostedZoneRequest(XEBIA_TECH_EVENT_INFO_DOMAIN_NAME))
				.getHostedZone();
	}

	protected abstract String getAMI();

	protected abstract String getTagRole();

	protected abstract String getCnamePrefix();

	protected void tagInstances(Map<String, List<Instance>> instancesByTeamId,
			AmazonEC2 ec2) {

		for (Map.Entry<String, List<Instance>> entry : instancesByTeamId
				.entrySet()) {

			String teamId = entry.getKey();
			List<Instance> instances = entry.getValue();

			for (int instanceIndex = 0; instanceIndex < instances.size(); instanceIndex++) {

				String serverName = getCnamePrefix() + teamId + "-instance"
						+ instanceIndex;
				Instance instance = instances.get(instanceIndex);
				logger.info("Tagging {} - {}", serverName,
						instance.getInstanceId());

				CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
						.withResources(instance.getInstanceId()) //
						.withTags(
								//
								new Tag("Name", serverName), //
								new Tag("TeamIdentifier", teamId), //
								new Tag("Workshop", "flume-hadoop"), //
								new Tag("Role", getTagRole()), //
								new Tag("CNAME", buildCname(teamId,
										instanceIndex))//
						);

				AmazonAwsUtils.createTags(instance, createTagsRequest, ec2);
			}
		}
	}

	protected String buildCname(String teamId, int instanceId) {
		return getCnamePrefix() + teamId + "-instance" + instanceId + "."
				+ hostedZoneId.getName();
	}

	protected Map<String, List<Instance>> associateInstancesToTeamIds(
			List<Instance> instances, List<String> teamIdentifiers,
			int numberOfServersPerTeam) {

		Map<String, List<Instance>> instancesByTeamId = Maps.newHashMap();
		int teamIdIndex = 0;
		for (String teamId : teamIdentifiers) {

			List<Instance> instancesForTeam = createListOfInstancesForTheTeam(
					instances, teamIdIndex, numberOfServersPerTeam);
			instancesByTeamId.put(teamId, instancesForTeam);
			teamIdIndex += numberOfServersPerTeam;
		}

		return instancesByTeamId;
	}

	private List<Instance> createListOfInstancesForTheTeam(
			List<Instance> instances, int teamId, int numberOfServersPerTeam) {
		return instances.subList(teamId, teamId + numberOfServersPerTeam);
	}

	protected void bindInstancesToDnsCnames(
			Map<String, List<Instance>> instancesByTeamId, AmazonRoute53 route53) {

		logger.info("Process {}", hostedZoneId);

		Map<String, Instance> cnamesToInstance = Maps.newHashMap();

		for (Map.Entry<String, List<Instance>> entry : instancesByTeamId
				.entrySet()) {
			String teamId = entry.getKey();
			List<Instance> instances = entry.getValue();

			for (int instanceIndex = 0; instanceIndex < instances.size(); instanceIndex++) {
				String cname = buildCname(teamId, instanceIndex);
				cnamesToInstance.put(cname, instances.get(instanceIndex));
			}
		}

		AmazonAwsUtils.deleteCnameIfExist(cnamesToInstance.keySet(),
				hostedZoneId, route53);
		AmazonAwsUtils.createCnamesForInstances(cnamesToInstance, hostedZoneId,
				route53);

		logger.info("Syslog servers creation SUCCESSFUL");
	}

	protected List<Instance> createNewInstances(AmazonEC2 ec2,
			WorkshopInfrastructure infra) {

		// String userData = CloudInitUserDataBuilder.start()
		// .addCloudConfigFromFilePath(CLOUD_CONFIG_FILE_PATH)
		// .buildBase64UserData();

		int instanceCount = infra.getTeamCount()
				* infra.getNumberOfServersPerTeam();

		RunInstancesRequest runInstancesRequest = new RunInstancesRequest() //
				.withInstanceType(InstanceType.T1Micro.toString()) //
				.withImageId(getAMI()) //
				.withMinCount(instanceCount) //
				.withMaxCount(instanceCount) //
				.withSecurityGroupIds("accept-all") //
				.withKeyName(infra.getKeyPairName());

		List<Instance> instances = AmazonAwsUtils.reliableEc2RunInstances(
				runInstancesRequest, ec2);

		if (instances.size() != instanceCount) {
			logger.warn(
					"Unexpected number of instances created: {} instead of {} expected",
					instances.size(), instanceCount);
		}

		return instances;
	}

	@Override
	public void run() {
		AmazonAwsUtils.terminateInstancesByWorkshop("flume-hadoop", ec2);

		List<Instance> instances = createNewInstances(ec2,
				workshopInfrastructure);
		Map<String, List<Instance>> instancesByTeamId = associateInstancesToTeamIds(
				instances, workshopInfrastructure.getTeamIdentifiers(),
				workshopInfrastructure.getNumberOfServersPerTeam());

		tagInstances(instancesByTeamId, ec2);

		bindInstancesToDnsCnames(instancesByTeamId, route53);
	}
}
