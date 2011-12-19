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
package fr.xebia.workshop.monitoring;

import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class WorkshopInfrastructure {

    private final List<String> teamIdentifiers = newArrayList();
    private String keyPairName;

    private WorkshopInfrastructure() {
    }

    public static Builder create() {
        return new Builder();
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


    public static class Builder {

        private WorkshopInfrastructure infra = new WorkshopInfrastructure();

        public WorkshopInfrastructure build() {
            return infra;
        }


        public Builder withTeamIdentifiers(Collection<String> teamIdentifiers) {
            infra.teamIdentifiers.addAll(teamIdentifiers);
            return this;
        }


        public Builder withKeyPairName(String keyPairName) {
            infra.keyPairName = keyPairName;
            return this;
        }
    }
}
