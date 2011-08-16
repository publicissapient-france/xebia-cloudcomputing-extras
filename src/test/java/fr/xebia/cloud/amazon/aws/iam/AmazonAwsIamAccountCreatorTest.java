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
package fr.xebia.cloud.amazon.aws.iam;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.VerifyEmailAddressRequest;

import fr.xebia.cloud.amazon.aws.iam.AmazonAwsIamAccountCreator;

public class AmazonAwsIamAccountCreatorTest {

    @Ignore
    @Test
    public void testCreateUser() throws IOException {
        new AmazonAwsIamAccountCreator().createUsers("cleclerc@xebia.com");
    }
    
    @Ignore
    @Test
    public void send_email_with_amazon_simple_email_service() throws IOException {
        
        new AmazonAwsIamAccountCreator().sendEmail("Xebia France Amazon EC2 Credentials", "my ses message", "cyrille@cyrilleleclerc.com",
                "cleclerc@xebia.com");
    }

}
