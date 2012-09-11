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

import static com.google.common.collect.Maps.newHashMap;

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
import fr.xebia.cloud.cloudinit.CloudInitUserDataBuilder;

public abstract class CreateHadoopServers implements Runnable {

    private static final String CLOUD_CONFIG_HADOOP_FLUME = "fr/xebia/workshop/bigdata/cloud-config-hadoop-flume.txt";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	protected AmazonEC2 ec2;

	protected AmazonRoute53 route53;

	protected HostedZone hostedZoneId;

	protected WorkshopInfrastructure workshopInfrastructure;

	public static final String XEBIA_TECH_EVENT_INFO_DOMAIN_NAME = "Z28O5PDK1WPCSR";

	protected CreateHadoopServers(AmazonEC2 ec2, AmazonRoute53 route53,
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


	private void tagInstance(String teamId, Instance instance, String cname) {

		String serverName = getCnamePrefix() + teamId;
		logger.info("Tagging {} - {}", serverName, instance.getInstanceId());

		CreateTagsRequest createTagsRequest = new CreateTagsRequest() //
				.withResources(instance.getInstanceId()) //
				.withTags(
				//
						new Tag("Name", serverName), //
						new Tag("TeamIdentifier", teamId), //
						new Tag("Workshop", "flume-hadoop"), //
						new Tag("Role", getTagRole()), //
						new Tag("CNAME", cname)//
				);

		AmazonAwsUtils.createTags(instance, createTagsRequest, ec2);
		
	}


	private void bindInstancesToDnsCnames(
			Map<String,Instance> instancesByCname) {

		logger.info("Process {}", hostedZoneId);

		AmazonAwsUtils.deleteCnameIfExist(instancesByCname.keySet(),
				hostedZoneId, route53);
		AmazonAwsUtils.createCnamesForInstances(instancesByCname, hostedZoneId,
				route53);

		logger.info("Hadoop instances DNS binding SUCCESSFUL");
	}



	private Map<String, RunInstancesRequest> createInstanceCreationRequests() {

		Map<String, String> cnamesMasterNodes = createCnamesMasterNodes();
		Map<String, String> cnamesSlaveNodes = createCnamesSlaveNodes();
		Map<String, RunInstancesRequest> runInstanceRequestByTeamId = newHashMap();

		for (String teamId : workshopInfrastructure.getTeamIdentifiers()) {

			
			RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
					.withInstanceType(InstanceType.T1Micro.toString())
					.withImageId(getAMI()).withMinCount(1).withMaxCount(1)
					.withSecurityGroupIds("accept-all")
					.withKeyName(workshopInfrastructure.getKeyPairName())
					.withUserData(generateCloudInit(cnamesMasterNodes.get(teamId), cnamesSlaveNodes.get(teamId)));

			runInstanceRequestByTeamId.put(teamId, runInstancesRequest);

		}
		
		return runInstanceRequestByTeamId;
		
	}
	
	private Map<String, String> getCnamesByTeamId() {
		Map<String, String> cnamesByTeamId = Maps.newHashMap();
		for(String teamId : workshopInfrastructure.getTeamIdentifiers()) {
			cnamesByTeamId.put(teamId, getCnamePrefix() + teamId + "."
				+ hostedZoneId.getName());
		}
		return cnamesByTeamId;
	}
	
	@Override
	public void run() {
		
		Map<String, RunInstancesRequest> runInstancesRequestByTeamId = createInstanceCreationRequests();
		Map<String, Instance> instancesByCname = Maps.newHashMap();
		
		for (String teamId : workshopInfrastructure.getTeamIdentifiers()) {

			List<Instance> instances = AmazonAwsUtils.reliableEc2RunInstances(
					runInstancesRequestByTeamId.get(teamId), ec2);

			if (instances.size() == 1) {
				String cname = getCnamesByTeamId().get(teamId);
				tagInstance(teamId, instances.get(0), cname);
				instancesByCname.put(cname, instances.get(0));
			} else {
				logger.warn(
						"Unexpected number of instances created: {} instead of {} expected", instances.size(), 1);
			}
		}
		
		bindInstancesToDnsCnames(instancesByCname);
		
	}
	
	
	private Map<String, String> createCnamesMasterNodes() {
		Map<String, String> cnamesMasterByTeamId = Maps.newHashMap();
		
		for(String teamId : workshopInfrastructure.getTeamIdentifiers()) {
			cnamesMasterByTeamId.put(teamId, getCnameMasterHadoop(teamId));
		}
		return cnamesMasterByTeamId;
	}
	
	private Map<String, String> createCnamesSlaveNodes() {
		Map<String, String> cnamesSlaveByTeamId = Maps.newHashMap();
		
		for(String teamId : workshopInfrastructure.getTeamIdentifiers()) {
			cnamesSlaveByTeamId.put(teamId, getCnameSlaveHadoop(teamId));
		}
		return cnamesSlaveByTeamId;
	}
	
	

	private String getCnameSlaveHadoop(String teamId) {
		return CreateHadoopSlaveNode.FLUME_HADOOP_SLAVE_TEAM + teamId +  "."+ hostedZoneId.getName();
	}

	private String getCnameMasterHadoop(String teamId) {
		return CreateHadoopMasterNode.FLUME_HADOOP_MASTER_TEAM+teamId+ "."+ hostedZoneId.getName();
	}

	private String generateCloudInit(String cname, String masterNameNode) {
		String userDataTeamId = CloudInitUserDataBuilder.start()
				.addCloudConfigFromFilePath(CLOUD_CONFIG_HADOOP_FLUME)
				.buildBase64UserData();
		userDataTeamId.replace("$localhost", cname);
		userDataTeamId.replace("$namenode", masterNameNode);
		return userDataTeamId;
	}

	

}
