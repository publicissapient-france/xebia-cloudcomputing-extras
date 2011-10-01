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

import java.util.Map;

import org.junit.Test;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.Maps;

import fr.xebia.cloud.cloudinit.FreemarkerUtils;

public class LabWikiPageGeneratorTest {

    @Test
    public void generate_page() {
        TeamInfrastructure infrastructure = new TeamInfrastructure("3");

        Instance jenkins = new Instance().withPublicDnsName("ec2-79-125-58-67-jenkins.eu-west-1.compute.amazonaws.com");
        infrastructure.setJenkins(jenkins);
        infrastructure.setJenkinsName("jenkins-clc");
        infrastructure.setRundeck(jenkins);
        infrastructure.setRundeckName("jenkins-clc");
        

        Instance devTomcat = new Instance() //
                .withPublicDnsName("ec2-79-125-53-61-devtomcat.eu-west-1.compute.amazonaws.com") //
                .withPrivateDnsName("ip-10-234-33-147.eu-west-1.compute.internal") //
                .withPrivateIpAddress("10.234.33.147");
        infrastructure.setDevTomcat(devTomcat);
        infrastructure.setDevTomcatName("tomcat-clc-dev-1");

        Instance validTomcat1 = new Instance() //
                .withPublicDnsName("ec2-79-011-33-55-validtomcat1.eu-west-1.compute.amazonaws.com") //
                .withPrivateDnsName("ip-10-01-03-05.eu-west-1.compute.internal") //
                .withPrivateIpAddress("10.01.03.05");
        infrastructure.setValidTomcat1(validTomcat1);
        infrastructure.setValidTomcat1Name("tomcat-clc-valid-1");

        Instance validTomcat2 = new Instance() //
                .withPublicDnsName("ec2-80-022-44-66-validtomcat2.eu-west-1.compute.amazonaws.com") //
                .withPrivateDnsName("ip-10-02-04-04.eu-west-1.compute.internal") //
                .withPrivateIpAddress("10.02.04.06");
        infrastructure.setValidTomcat2(validTomcat2);
        infrastructure.setValidTomcat2Name("tomcat-clc-valid-2");

        Map<String, Object> rootMap = Maps.newHashMap();
        rootMap.put("infrastructure", infrastructure);
        String page = FreemarkerUtils.generate(rootMap, "/fr/xebia/workshop/continuousdelivery/continuous-delivery-lab.fmt");
        System.out.println(page);
    }
}
