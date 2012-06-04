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
package fr.xebia.workshop.caching;

import com.google.common.collect.Lists;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;

import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.*;

public class WorkshopInfrastructure {

    private List<String> teamIdentifiers = newArrayList();

    private String keyPairName;

    private String awsAccessKeyId;

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }

    public WorkshopInfrastructure withAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
        return this;
    }

    private String awsSecretKey;

    public WorkshopInfrastructure() {
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }


    public void setAwsAccessKeyId(String awsAccessKeyId) {
        this.awsAccessKeyId = awsAccessKeyId;
    }

    public WorkshopInfrastructure withAwsAccessKeyId(String awsAccessKeyId) {
        this.awsAccessKeyId = awsAccessKeyId;
        return this;
    }

    public List<String> getTeamIdentifiers() {
        return teamIdentifiers;
    }

    public int getTeamCount() {
        return teamIdentifiers.size();
    }

    public String getKeyPairName() {
        return keyPairName;
    }

    public WorkshopInfrastructure withKeyPairName(String keyPairName) {
        this.keyPairName = keyPairName;
        return this;
    }

    public WorkshopInfrastructure withTeamIdentifiers(String... teamIdentifiers) {
        this.teamIdentifiers = Lists.newArrayList(teamIdentifiers);
        return this;
    }

    public WorkshopInfrastructure withTeamIdentifiers(Collection<String> teamIdentifiers) {
        this.teamIdentifiers = Lists.newArrayList(teamIdentifiers);
        return this;
    }

    public void setBeanstalkNotificationEmail(String beanstalkNotificationEmail) {
        this.beanstalkNotificationEmail = beanstalkNotificationEmail;
    }

    public WorkshopInfrastructure withBeanstalkNotificationEmail(String beanstalkNotificationEmail) {
        this.beanstalkNotificationEmail = beanstalkNotificationEmail;
        return this;
    }

    public void setKeyPairName(String keyPairName) {
        this.keyPairName = keyPairName;
    }

    String beanstalkNotificationEmail;

    public String getBeanstalkNotificationEmail() {
        return beanstalkNotificationEmail;
    }
}
