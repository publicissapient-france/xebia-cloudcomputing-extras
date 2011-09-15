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
package fr.xebia.cloud.amazon.aws.tools;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Placement;
import com.google.common.base.Function;

/**
 * Google Guava {@link Function} for Amazon AWS.
 * 
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class AmazonAwsFunctions {

    /**
     * Extract the {@link Placement#getAvailabilityZone()} zone of the given ec2
     * <code>instance</code> .
     */
    public static final Function<Instance, String> EC2_INSTANCE_TO_AVAILABILITY_ZONE = new Function<Instance, String>() {
        @Override
        public String apply(Instance instance) {
            return instance.getPlacement().getAvailabilityZone();
        }
    };

    /**
     * Extract the {@link Instance#getInstanceId()} of the given ec2
     * <code>instance</code>.
     */
    public final static Function<Instance, String> EC2_INSTANCE_TO_INSTANCE_ID = new Function<Instance, String>() {
        @Override
        public String apply(Instance instance) {
            return instance.getInstanceId();
        }
    };

    /**
     * Extract the
     * {@link com.amazonaws.services.elasticloadbalancing.model.Instance#getInstanceId()}
     * of the given elb <code>instance</code>.
     */
    public final static Function<com.amazonaws.services.elasticloadbalancing.model.Instance, String> ELB_INSTANCE_TO_INSTANCE_ID = new Function<com.amazonaws.services.elasticloadbalancing.model.Instance, String>() {
        @Override
        public String apply(com.amazonaws.services.elasticloadbalancing.model.Instance instance) {
            return instance.getInstanceId();
        }
    };

    /**
     * Converts the given <code>instanceId</code> into an elb
     * {@link com.amazonaws.services.elasticloadbalancing.model.Instance}.
     */
    public final static Function<String, com.amazonaws.services.elasticloadbalancing.model.Instance> INSTANCE_ID_TO_ELB_INSTANCE = new Function<String, com.amazonaws.services.elasticloadbalancing.model.Instance>() {
        @Override
        public com.amazonaws.services.elasticloadbalancing.model.Instance apply(String instanceId) {
            return new com.amazonaws.services.elasticloadbalancing.model.Instance(instanceId);
        }
    };
}
