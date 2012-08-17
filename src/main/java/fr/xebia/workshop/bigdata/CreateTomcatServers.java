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

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.route53.AmazonRoute53;

public class CreateTomcatServers extends CreateServers {

	// TODO JMA à remplacer par la bonne AMI
	protected static final String AMI_HADOOP_DEVOXX_FINAL_2 = "ami-a56f54d1";

	public String getCnamePrefix() {
		return "tomcat-syslog-teamId";
	}

	public CreateTomcatServers(AmazonEC2 ec2, AmazonRoute53 route53,
			WorkshopInfrastructure workshopInfrastructure) {
		super(ec2, route53, workshopInfrastructure);

	}

	public String getTagRole() {
		return "log-generator";
	}

	public String getAMI() {
		// TODO JMA à remplacer par la bonne AMI
		return AMI_HADOOP_DEVOXX_FINAL_2;
	}

}
