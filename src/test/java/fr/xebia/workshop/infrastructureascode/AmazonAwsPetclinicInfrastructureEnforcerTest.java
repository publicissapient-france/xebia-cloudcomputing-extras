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
package fr.xebia.workshop.infrastructureascode;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang.ObjectUtils;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.model.AppCookieStickinessPolicy;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLBCookieStickinessPolicyRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerPolicyRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.DisableAvailabilityZonesForLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.EnableAvailabilityZonesForLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.LBCookieStickinessPolicy;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException;
import com.amazonaws.services.elasticloadbalancing.model.Policies;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.SetLoadBalancerPoliciesOfListenerRequest;
import com.amazonaws.services.rds.AmazonRDS;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import fr.xebia.workshop.infrastructureascode.AmazonAwsPetclinicInfrastructureEnforcer;

public class AmazonAwsPetclinicInfrastructureEnforcerTest {

    AmazonEC2 ec2 = mock(AmazonEC2.class);
    AmazonElasticLoadBalancing elb = mock(AmazonElasticLoadBalancing.class);
    AmazonRDS rds = mock(AmazonRDS.class);

    @Test
    public void add_one_instance_and_one_availability_zone() {

        DescribeInstancesResult describeInstanceResult = buildDescribeInstancesResult();

        when(ec2.describeInstances((DescribeInstancesRequest) any())).thenReturn(describeInstanceResult);

        LoadBalancerDescription lbDescription = buildExpectedLoadBalancerDescription();
        lbDescription.setAvailabilityZones(Lists.newArrayList("eu-west-1b"));
        lbDescription.setInstances(Lists.newArrayList(new com.amazonaws.services.elasticloadbalancing.model.Instance("i-1")));

        when(elb.describeLoadBalancers((DescribeLoadBalancersRequest) any())) //
                .thenReturn(new DescribeLoadBalancersResult().withLoadBalancerDescriptions(lbDescription));

        AmazonAwsPetclinicInfrastructureEnforcer infraEnforcer = new AmazonAwsPetclinicInfrastructureEnforcer(ec2, elb, rds);

        infraEnforcer.createOrUpdateElasticLoadBalancer("/myapp/healsthcheck.jsp", "myapp");

        verify(elb, atLeastOnce()).describeLoadBalancers((DescribeLoadBalancersRequest) any());
        verify(elb, times(1)).enableAvailabilityZonesForLoadBalancer(
                argThat(new ArgumentMatcher<EnableAvailabilityZonesForLoadBalancerRequest>() {
                    @Override
                    public boolean matches(Object argument) {
                        EnableAvailabilityZonesForLoadBalancerRequest req = (EnableAvailabilityZonesForLoadBalancerRequest) argument;
                        if (!ObjectUtils.equals("eu-west-1c", Iterables.getOnlyElement(req.getAvailabilityZones()))) {
                            return false;
                        }
                        return true;
                    }
                }));

        verify(elb, times(1)).registerInstancesWithLoadBalancer(argThat(new ArgumentMatcher<RegisterInstancesWithLoadBalancerRequest>() {
            @Override
            public boolean matches(Object argument) {
                RegisterInstancesWithLoadBalancerRequest req = (RegisterInstancesWithLoadBalancerRequest) argument;
                if (!ObjectUtils.equals("i-2", Iterables.getOnlyElement(req.getInstances()).getInstanceId())) {
                    return false;
                }
                return true;
            }
        }));
        verifyNoMoreInteractions(elb);

    }

    @Test
    public void add_stickiness_policy() {

        DescribeInstancesResult describeInstanceResult = buildDescribeInstancesResult();

        when(ec2.describeInstances((DescribeInstancesRequest) any())).thenReturn(describeInstanceResult);

        LoadBalancerDescription lbDescription = buildExpectedLoadBalancerDescription();
        lbDescription.setPolicies(new Policies());
        lbDescription.getListenerDescriptions().get(0).getPolicyNames().clear();

        when(elb.describeLoadBalancers((DescribeLoadBalancersRequest) any())) //
                .thenReturn(new DescribeLoadBalancersResult().withLoadBalancerDescriptions(lbDescription));

        AmazonAwsPetclinicInfrastructureEnforcer infraEnforcer = new AmazonAwsPetclinicInfrastructureEnforcer(ec2, elb, rds);

        infraEnforcer.createOrUpdateElasticLoadBalancer("/myapp/healsthcheck.jsp", "myapp");

        verify(elb, atLeastOnce()).describeLoadBalancers((DescribeLoadBalancersRequest) any());
        verify(elb, times(1)).createLBCookieStickinessPolicy(argThat(buildCreateLBCookieStickinessPolicyRequestMatcher()));
        verify(elb, times(1)).setLoadBalancerPoliciesOfListener(argThat(buildSetLoadBalancerPoliciesOfListenerRequestMatcher()));
        verifyNoMoreInteractions(elb);

    }

    ArgumentMatcher<ConfigureHealthCheckRequest> buildConfigureHealthCheckRequestMatcher() {
        ArgumentMatcher<ConfigureHealthCheckRequest> configureHealthCheckMatcher = new ArgumentMatcher<ConfigureHealthCheckRequest>() {
            @Override
            public boolean matches(Object argument) {
                ConfigureHealthCheckRequest req = (ConfigureHealthCheckRequest) argument;
                HealthCheck expectedHealthCheck = new HealthCheck() //
                        .withTarget("HTTP:8080/myapp/healsthcheck.jsp") //
                        .withHealthyThreshold(2) //
                        .withUnhealthyThreshold(2) //
                        .withInterval(30) //
                        .withTimeout(2);

                HealthCheck actualHealthCheck = req.getHealthCheck();
                if ( //
                !ObjectUtils.equals(expectedHealthCheck.getHealthyThreshold(), actualHealthCheck.getHealthyThreshold()) || //
                        !ObjectUtils.equals(expectedHealthCheck.getInterval(), actualHealthCheck.getInterval()) || //
                        !ObjectUtils.equals(expectedHealthCheck.getTarget(), actualHealthCheck.getTarget()) || //
                        !ObjectUtils.equals(expectedHealthCheck.getTimeout(), actualHealthCheck.getTimeout()) || //
                        !ObjectUtils.equals(expectedHealthCheck.getUnhealthyThreshold(), actualHealthCheck.getUnhealthyThreshold())) {
                    return false;
                }
                return true;
            }
        };
        return configureHealthCheckMatcher;
    }

    ArgumentMatcher<CreateLBCookieStickinessPolicyRequest> buildCreateLBCookieStickinessPolicyRequestMatcher() {
        return new ArgumentMatcher<CreateLBCookieStickinessPolicyRequest>() {
            @Override
            public boolean matches(Object argument) {
                CreateLBCookieStickinessPolicyRequest req = (CreateLBCookieStickinessPolicyRequest) argument;
                LBCookieStickinessPolicy expectedCookieStickinessPolicy = new LBCookieStickinessPolicy("myapp-stickiness-policy", null);

                if (!ObjectUtils.equals("myapp", req.getLoadBalancerName())) {
                    return false;
                }
                if (!ObjectUtils.equals(expectedCookieStickinessPolicy.getPolicyName(), req.getPolicyName())) {
                    return false;
                }
                if (!ObjectUtils.equals(expectedCookieStickinessPolicy.getCookieExpirationPeriod(), req.getCookieExpirationPeriod())) {
                    return false;
                }
                return true;
            }
        };
    }

    ArgumentMatcher<CreateLoadBalancerRequest> buildCreateLoadBalancerRequestMatcher() {
        ArgumentMatcher<CreateLoadBalancerRequest> createLbMatcher = new ArgumentMatcher<CreateLoadBalancerRequest>() {

            @Override
            public boolean matches(Object argument) {
                CreateLoadBalancerRequest req = (CreateLoadBalancerRequest) argument;

                ArrayList<String> expectedAvailabilityZones = Lists.newArrayList("eu-west-1b", "eu-west-1c");
                if (!ObjectUtils.equals(expectedAvailabilityZones, req.getAvailabilityZones())) {
                    return false;
                }

                Listener actualListener = Iterables.getOnlyElement(req.getListeners());
                Listener expectedListener = new Listener("HTTP", 80, 8080);
                if ( //
                !ObjectUtils.equals(expectedListener.getProtocol(), actualListener.getProtocol()) || //
                        !ObjectUtils.equals(expectedListener.getLoadBalancerPort(), actualListener.getLoadBalancerPort()) || //
                        !ObjectUtils.equals(expectedListener.getInstancePort(), actualListener.getInstancePort())) {
                    return false;
                }

                return true;
            }
        };
        return createLbMatcher;
    }

    DescribeInstancesResult buildDescribeInstancesResult() {
        DescribeInstancesResult describeInstanceResult = new DescribeInstancesResult().withReservations(new Reservation().withInstances( //
                new Instance().withInstanceId("i-1").withPlacement(new Placement("eu-west-1b")), //
                new Instance().withInstanceId("i-2").withPlacement(new Placement("eu-west-1c"))));
        return describeInstanceResult;
    }

    LoadBalancerDescription buildExpectedLoadBalancerDescription() {
        LoadBalancerDescription lbDescription = new LoadBalancerDescription().withAvailabilityZones("eu-west-1b")
                .withHealthCheck(new HealthCheck("HTTP:8080/myapp/healsthcheck.jsp", 30, 2, 2, 2)) //
                .withInstances( //
                        new com.amazonaws.services.elasticloadbalancing.model.Instance("i-1"), //
                        new com.amazonaws.services.elasticloadbalancing.model.Instance("i-2")) //
                .withAvailabilityZones("eu-west-1b", "eu-west-1c") //
                .withListenerDescriptions( //
                        new ListenerDescription() //
                                .withListener(new Listener("HTTP", 80, 8080)) //
                                .withPolicyNames("myapp-stickiness-policy")) //
                .withPolicies( //
                        new Policies() //
                                .withLBCookieStickinessPolicies(new LBCookieStickinessPolicy("myapp-stickiness-policy", null))//
                )//
                .withLoadBalancerName("myapp");
        return lbDescription;
    }

    ArgumentMatcher<SetLoadBalancerPoliciesOfListenerRequest> buildSetLoadBalancerPoliciesOfListenerRequestMatcher() {
        return new ArgumentMatcher<SetLoadBalancerPoliciesOfListenerRequest>() {
            @Override
            public boolean matches(Object argument) {
                SetLoadBalancerPoliciesOfListenerRequest req = (SetLoadBalancerPoliciesOfListenerRequest) argument;
                if (!ObjectUtils.equals(80, req.getLoadBalancerPort())) {
                    return false;
                }
                if (!ObjectUtils.equals(Lists.newArrayList("myapp-stickiness-policy"), req.getPolicyNames())) {
                    return false;
                }
                if (!ObjectUtils.equals("myapp", req.getLoadBalancerName())) {
                    return false;
                }
                return true;
            }
        };
    }

    @Test
    public void deregister_exceeding_instances() {
        DescribeInstancesResult describeInstanceResult = buildDescribeInstancesResult();

        when(ec2.describeInstances((DescribeInstancesRequest) any())).thenReturn(describeInstanceResult);

        LoadBalancerDescription lbDescription = buildExpectedLoadBalancerDescription();

        lbDescription.setInstances(Arrays.asList( //
                new com.amazonaws.services.elasticloadbalancing.model.Instance("i-1"), //
                new com.amazonaws.services.elasticloadbalancing.model.Instance("i-2"), //
                new com.amazonaws.services.elasticloadbalancing.model.Instance("i-3")) //
                );

        when(elb.describeLoadBalancers((DescribeLoadBalancersRequest) any())) //
                .thenReturn(new DescribeLoadBalancersResult().withLoadBalancerDescriptions(lbDescription));

        AmazonAwsPetclinicInfrastructureEnforcer infraEnforcer = new AmazonAwsPetclinicInfrastructureEnforcer(ec2, elb, rds);

        infraEnforcer.createOrUpdateElasticLoadBalancer("/myapp/healsthcheck.jsp", "myapp");

        verify(elb, atLeastOnce()).describeLoadBalancers((DescribeLoadBalancersRequest) any());

        verify(elb, times(1)).deregisterInstancesFromLoadBalancer(
                argThat(new ArgumentMatcher<DeregisterInstancesFromLoadBalancerRequest>() {
                    @Override
                    public boolean matches(Object argument) {
                        DeregisterInstancesFromLoadBalancerRequest req = (DeregisterInstancesFromLoadBalancerRequest) argument;
                        Collection<String> instanceIdsToUnregister = Collections2.transform(req.getInstances(),
                                AmazonAwsPetclinicInfrastructureEnforcer.ELB_INSTANCE_TO_INSTANCE_ID);
                        if (!Arrays.asList("i-3").equals(Lists.newArrayList(instanceIdsToUnregister))) {
                            return false;
                        }
                        return true;
                    }
                }));
        verifyNoMoreInteractions(elb);

    }

    @Test
    public void disable_exceeding_availability_zone() {
        DescribeInstancesResult describeInstanceResult = buildDescribeInstancesResult();

        when(ec2.describeInstances((DescribeInstancesRequest) any())).thenReturn(describeInstanceResult);

        LoadBalancerDescription lbDescription = buildExpectedLoadBalancerDescription();
        lbDescription.setAvailabilityZones(Arrays.asList("eu-west-1a", "eu-west-1b", "eu-west-1c"));

        when(elb.describeLoadBalancers((DescribeLoadBalancersRequest) any())) //
                .thenReturn(new DescribeLoadBalancersResult().withLoadBalancerDescriptions(lbDescription));

        AmazonAwsPetclinicInfrastructureEnforcer infraEnforcer = new AmazonAwsPetclinicInfrastructureEnforcer(ec2, elb, rds);

        infraEnforcer.createOrUpdateElasticLoadBalancer("/myapp/healsthcheck.jsp", "myapp");

        verify(elb, atLeastOnce()).describeLoadBalancers((DescribeLoadBalancersRequest) any());

        verify(elb, times(1)).disableAvailabilityZonesForLoadBalancer(
                argThat(new ArgumentMatcher<DisableAvailabilityZonesForLoadBalancerRequest>() {
                    @Override
                    public boolean matches(Object argument) {
                        DisableAvailabilityZonesForLoadBalancerRequest req = (DisableAvailabilityZonesForLoadBalancerRequest) argument;
                        if (!Arrays.asList("eu-west-1a").equals(req.getAvailabilityZones())) {
                            return false;
                        }
                        return true;
                    }
                }));
        verifyNoMoreInteractions(elb);

    }

    @Test
    public void dont_update_anything_to_up_to_date_load_balancer() {

        DescribeInstancesResult describeInstanceResult = buildDescribeInstancesResult();

        when(ec2.describeInstances((DescribeInstancesRequest) any())).thenReturn(describeInstanceResult);

        LoadBalancerDescription lbDescription = buildExpectedLoadBalancerDescription();

        when(elb.describeLoadBalancers((DescribeLoadBalancersRequest) any())) //
                .thenReturn(new DescribeLoadBalancersResult().withLoadBalancerDescriptions(lbDescription));

        AmazonAwsPetclinicInfrastructureEnforcer infraEnforcer = new AmazonAwsPetclinicInfrastructureEnforcer(ec2, elb, rds);

        infraEnforcer.createOrUpdateElasticLoadBalancer("/myapp/healsthcheck.jsp", "myapp");

        verify(elb, atLeastOnce()).describeLoadBalancers((DescribeLoadBalancersRequest) any());
        verifyNoMoreInteractions(elb);

    }

    @Test
    public void elb_create_from_scratch() {

        DescribeInstancesResult describeInstanceResult = buildDescribeInstancesResult();

        when(ec2.describeInstances((DescribeInstancesRequest) any())).thenReturn(describeInstanceResult);

        LoadBalancerDescription lbDescription = buildExpectedLoadBalancerDescription();

        when(elb.describeLoadBalancers((DescribeLoadBalancersRequest) any())) //
                .thenThrow(new LoadBalancerNotFoundException("elb '" + "myapp" + "' not found")) //
                .thenReturn(new DescribeLoadBalancersResult().withLoadBalancerDescriptions(lbDescription));

        ArgumentMatcher<CreateLoadBalancerRequest> createLbMatcher = buildCreateLoadBalancerRequestMatcher();

        ArgumentMatcher<ConfigureHealthCheckRequest> configureHealthCheckMatcher = buildConfigureHealthCheckRequestMatcher();

        AmazonAwsPetclinicInfrastructureEnforcer infraEnforcer = new AmazonAwsPetclinicInfrastructureEnforcer(ec2, elb, rds);

        infraEnforcer.createOrUpdateElasticLoadBalancer("/myapp/healsthcheck.jsp", "myapp");

        verify(elb, atLeastOnce()).describeLoadBalancers((DescribeLoadBalancersRequest) any());
        verify(elb, times(1)).createLoadBalancer(argThat(createLbMatcher));
        verify(elb, times(1)).configureHealthCheck(argThat(configureHealthCheckMatcher));
        verify(elb, never()).disableAvailabilityZonesForLoadBalancer((DisableAvailabilityZonesForLoadBalancerRequest) any());
        verify(elb, never()).enableAvailabilityZonesForLoadBalancer((EnableAvailabilityZonesForLoadBalancerRequest) any());
        verify(elb, times(1)).registerInstancesWithLoadBalancer(argThat(new ArgumentMatcher<RegisterInstancesWithLoadBalancerRequest>() {
            @Override
            public boolean matches(Object argument) {
                RegisterInstancesWithLoadBalancerRequest req = (RegisterInstancesWithLoadBalancerRequest) argument;

                if (!ObjectUtils.equals(2, req.getInstances().size())) {
                    return false;
                }
                return true;
            }
        }));
        verify(elb, times(1)).createLBCookieStickinessPolicy(argThat(buildCreateLBCookieStickinessPolicyRequestMatcher()));
        verify(elb, times(1)).setLoadBalancerPoliciesOfListener(argThat(buildSetLoadBalancerPoliciesOfListenerRequestMatcher()));
        verifyNoMoreInteractions(elb);

    }

    @Test
    public void overwrite_unexpected_stickiness_policy() {

        DescribeInstancesResult describeInstanceResult = buildDescribeInstancesResult();

        when(ec2.describeInstances((DescribeInstancesRequest) any())).thenReturn(describeInstanceResult);

        LoadBalancerDescription lbDescription = buildExpectedLoadBalancerDescription();
        lbDescription.setPolicies(new Policies());
        lbDescription.getPolicies().setAppCookieStickinessPolicies(
                Arrays.asList(new AppCookieStickinessPolicy("myapp-jsessionid-policy", "JSESSIONID")));
        lbDescription.getListenerDescriptions().get(0).getPolicyNames().clear();
        lbDescription.getListenerDescriptions().get(0).getPolicyNames().add("myapp-jsessionid-policy");

        when(elb.describeLoadBalancers((DescribeLoadBalancersRequest) any())) //
                .thenReturn(new DescribeLoadBalancersResult().withLoadBalancerDescriptions(lbDescription));

        AmazonAwsPetclinicInfrastructureEnforcer infraEnforcer = new AmazonAwsPetclinicInfrastructureEnforcer(ec2, elb, rds);

        infraEnforcer.createOrUpdateElasticLoadBalancer("/myapp/healsthcheck.jsp", "myapp");

        verify(elb, atLeastOnce()).describeLoadBalancers((DescribeLoadBalancersRequest) any());
        verify(elb, times(1)).createLBCookieStickinessPolicy(argThat(buildCreateLBCookieStickinessPolicyRequestMatcher()));
        verify(elb, times(1)).setLoadBalancerPoliciesOfListener(argThat(buildSetLoadBalancerPoliciesOfListenerRequestMatcher()));
        verify(elb, times(1)).deleteLoadBalancerPolicy(argThat(new ArgumentMatcher<DeleteLoadBalancerPolicyRequest>() {
            @Override
            public boolean matches(Object argument) {
                DeleteLoadBalancerPolicyRequest req = (DeleteLoadBalancerPolicyRequest) argument;
                if (!ObjectUtils.equals("myapp-jsessionid-policy", req.getPolicyName())) {
                    return false;
                }

                if (!ObjectUtils.equals("myapp", req.getLoadBalancerName())) {
                    return false;
                }
                return true;
            }
        }));
        verifyNoMoreInteractions(elb);

    }
}
