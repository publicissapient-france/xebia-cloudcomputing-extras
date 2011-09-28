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

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class ContinuousDeliveryInfrastructureCreatorTest {

    @Test
    public void testFindInfrasturctureTopology() {
        ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator();
        Collection<TeamInfrastructure> teamInfrastructures = creator.discoverInfrasturctureTopology();
        System.out.println(teamInfrastructures);
    }

    @Test
    public void testGenerateDocs() throws Exception {
        ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator();
        Collection<TeamInfrastructure> teamInfrastructures = creator.discoverInfrasturctureTopology();
        creator.generateDocs(teamInfrastructures, "/tmp/continuous-delivery-wiki-test");
    }

}
