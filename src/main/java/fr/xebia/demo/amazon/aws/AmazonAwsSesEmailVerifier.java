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
package fr.xebia.demo.amazon.aws;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.VerifyEmailAddressRequest;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;

public class AmazonAwsSesEmailVerifier {

    public static void main(String[] args) throws Exception {
        InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("AwsCredentials.properties");
        Preconditions.checkNotNull(credentialsAsStream, "File 'AwsCredentials.properties' NOT found in the classpath");
        AWSCredentials awsCredentials = new PropertiesCredentials(credentialsAsStream);

        AmazonSimpleEmailService ses = new AmazonSimpleEmailServiceClient(awsCredentials);

        URL emailsToVerifyURL = Thread.currentThread().getContextClassLoader().getResource("emails-to-verify.txt");
        List<String> emailsToVerify = Resources.readLines(emailsToVerifyURL, Charsets.ISO_8859_1);

        for (String emailToVerify : emailsToVerify) {
            System.out.println(emailToVerify);
            Thread.sleep(10*1000);
            ses.verifyEmailAddress(new VerifyEmailAddressRequest().withEmailAddress(emailToVerify));
        }
    }
}
