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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.security.auth.x500.X500Principal;
import javax.ws.rs.core.Response.StatusType;

import org.apache.commons.lang.RandomStringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.jclouds.crypto.Pems;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.DeleteKeyPairRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.AccessKey;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.AddUserToGroupRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.CreateLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserResult;
import com.amazonaws.services.identitymanagement.model.GetAccountSummaryRequest;
import com.amazonaws.services.identitymanagement.model.GetLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.GetLoginProfileResult;
import com.amazonaws.services.identitymanagement.model.GetUserRequest;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException;
import com.amazonaws.services.identitymanagement.model.SigningCertificate;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.UploadSigningCertificateRequest;
import com.amazonaws.services.identitymanagement.model.UploadSigningCertificateResult;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.identitymanagement.model.statusType;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendEmailResult;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;

/**
 * Create Amazon IAM accounts.
 * 
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class AmazonAwsIamAccountCreator {

    static {
        // adds the Bouncy castle provider to java security
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        try {
            AmazonAwsIamAccountCreator amazonAwsIamAccountCreator = new AmazonAwsIamAccountCreator();
            amazonAwsIamAccountCreator.createUsers();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected final KeyPairGenerator keyPairGenerator;

    protected final AmazonEC2 ec2;

    protected final AmazonIdentityManagement iam;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final AmazonSimpleEmailService ses;

    public AmazonAwsIamAccountCreator() {
        try {
            InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("AwsCredentials.properties");
            Preconditions.checkNotNull(credentialsAsStream, "File 'AwsCredentials.properties' NOT found in the classpath");
            AWSCredentials awsCredentials = new PropertiesCredentials(credentialsAsStream);
            iam = new AmazonIdentityManagementClient(awsCredentials);

            ses = new AmazonSimpleEmailServiceClient(awsCredentials);

            ec2 = new AmazonEC2Client(awsCredentials);
            ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

            keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(1024, new SecureRandom());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Builds difference between list of emails provided in
     * "accounts-to-create.txt" and the already created users (obtained via
     * {@link AmazonIdentityManagement#listUsers()}).
     */
    public Set<String> buildUserNamesToCreate() {
        List<String> existingUserNames = Lists.transform(iam.listUsers().getUsers(), new Function<User, String>() {
            @Override
            public String apply(User user) {
                return user.getUserName();
            }
        });

        URL emailsToVerifyURL = Thread.currentThread().getContextClassLoader().getResource("accounts-to-create.txt");
        Preconditions.checkNotNull(emailsToVerifyURL, "File 'accounts-to-create.txt' NOT found in the classpath");
        List<String> userNamesToCreate;
        try {
            userNamesToCreate = Resources.readLines(emailsToVerifyURL, Charsets.ISO_8859_1);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        return Sets.difference(Sets.newHashSet(userNamesToCreate), Sets.newHashSet(existingUserNames));

    }

    public void createUsers() {
        Set<String> userNames = buildUserNamesToCreate();
        System.out.println("Create accounts for: " + userNames);
        for (String userName : userNames) {
            createUsers(userName);

            // sleep 10 seconds to prevent "Throttling exception"
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public java.security.KeyPair createRsaKeyPair() {

        return keyPairGenerator.generateKeyPair();
    }

    @SuppressWarnings("deprecation")
    public X509Certificate createX509Certificate(String userName, java.security.KeyPair keyPair) {
        try {
            DateTime startDate = new DateTime().minusDays(1);
            DateTime expiryDate = new DateTime().plusYears(2);

            X509V1CertificateGenerator certGen = new X509V1CertificateGenerator();
            X500Principal dnName = new X500Principal("CN=" + userName);

            certGen.setSubjectDN(dnName);
            // same as subject : self signed certificate
            certGen.setIssuerDN(dnName);
            certGen.setNotBefore(startDate.toDate());
            certGen.setNotAfter(expiryDate.toDate());
            certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
            certGen.setPublicKey(keyPair.getPublic());
            certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");

            return certGen.generate(keyPair.getPrivate(), "BC");

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Create an Amazon IAM account with a password, a secret key and member of
     * "Admins". The password, access key and secret key are sent by email.
     * 
     * @param userName
     *            valid email used as userName of the created account.
     */
    public void createUsers(String userName) {

        User user;

        try {
            user = iam.getUser(new GetUserRequest().withUserName(userName)).getUser();
        } catch (NoSuchEntityException e) {
            user = iam.createUser(new CreateUserRequest(userName)).getUser();
        }

        String password;
        try {
            GetLoginProfileResult loginProfile = iam.getLoginProfile(new GetLoginProfileRequest(user.getUserName()));
            password = null;
            logger.info("login already created on the {}", loginProfile.getLoginProfile().getCreateDate());
        } catch (NoSuchEntityException e) {
            password = RandomStringUtils.randomAlphanumeric(8);
            iam.createLoginProfile(new CreateLoginProfileRequest(user.getUserName(), password));
            iam.addUserToGroup(new AddUserToGroupRequest("Admins", user.getUserName()));
        }

        AccessKey accessKey = null;
        ListAccessKeysResult listAccessKeysResult = iam.listAccessKeys(new ListAccessKeysRequest().withUserName(user.getUserName()));
        for (AccessKeyMetadata accessKeyMetadata : listAccessKeysResult.getAccessKeyMetadata()) {
            statusType status = statusType.fromValue(accessKeyMetadata.getStatus());
            if (statusType.Active.equals(status)) {
                logger.info("Key {} ({}) is already active", accessKeyMetadata.getAccessKeyId(), accessKeyMetadata.getCreateDate());
                accessKey = new AccessKey(user.getUserName(), accessKeyMetadata.getAccessKeyId(), statusType.Active.toString(), null);
                break;
            }
        }

        if (accessKey == null) {
            CreateAccessKeyResult createAccessKeyResult = iam
                    .createAccessKey(new CreateAccessKeyRequest().withUserName(user.getUserName()));
            accessKey = createAccessKeyResult.getAccessKey();
        }

        // SSH
        KeyPair sshKeyPair = createOrOverWriteSshKeyPair(userName);

        // X509
        java.security.KeyPair x509KeyPair = createRsaKeyPair();
        X509Certificate x509Certificate = createX509Certificate(userName, x509KeyPair);

        SigningCertificate signingCertificate;
        try {
            UploadSigningCertificateResult uploadSigningCertificateResult = iam
                    .uploadSigningCertificate(new UploadSigningCertificateRequest(Pems.pem(x509Certificate)).withUserName(user
                            .getUserName()));
            signingCertificate = uploadSigningCertificateResult.getCertificate();
        } catch (CertificateEncodingException e) {
            throw Throwables.propagate(e);
        }

        System.out.println("CREATED userName=" + user.getUserName() + "\tpassword=" + password + "\taccessKeyId="
                + accessKey.getAccessKeyId() + "\tsecretAccessKey=" + accessKey.getSecretAccessKey() + "\tsshKeyPair="
                + sshKeyPair.getKeyName() + "\tx509Certificate=" + signingCertificate.getCertificateId());

        String subject = "Xebia France Amazon EC2 Credentials";

        String body = "Hello,\n";
        body += "\n";
        body += "Here are the credentials to connect to Xebia Amazon AWS/EC2 training infrastructure:\n";
        body += "\n";
        body += "User Name: " + user.getUserName() + "\n";
        body += "Password: " + password + "\n";
        body += "\n";
        body += "Access Key Id: " + accessKey.getAccessKeyId() + "\n";
        body += "Secret Access Key: " + accessKey.getSecretAccessKey() + "\n";
        body += "\n";
        body += "SSH private key pair '" + sshKeyPair.getKeyName() + "' attached, rename it as '" + sshKeyPair.getKeyName() + ".pem" + "'n";
        body += "\n";
        body += "The authentication page is https://xebia-france.signin.aws.amazon.com/console";
        body += "\n";
        body += "Don't hesitate to connect to Amazon AWS, to play with it but please DO NOT FORGET TO STOP INSTANCES OR IF POSSIBLE TERMINATE THEM AFTER USING THEM.\n";
        body += "Letting instances started would cost unnecessary money to Xebia.\n";
        body += "\n";
        body += "\n";
        body += "Thanks,\n";
        body += "\n";
        body += "Cyrille";
        try {
            sendEmail(subject, body, accessKey, sshKeyPair, x509KeyPair, x509Certificate, signingCertificate, "cyrille@cyrilleleclerc.com",
                    user.getUserName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    KeyPair createOrOverWriteSshKeyPair(String userName) {
        String keyPairName;
        if (userName.endsWith("@xebia.fr") || userName.endsWith("@xebia.com")) {
            keyPairName = userName.substring(0, userName.indexOf("@xebia."));
        } else {
            keyPairName = userName.replace("@", "_at_").replace(".", "_dot_").replace("+", "_plus_");
        }
        try {
            DescribeKeyPairsResult describeKeyPairsResult = ec2.describeKeyPairs(new DescribeKeyPairsRequest().withKeyNames(keyPairName));
            if (!describeKeyPairsResult.getKeyPairs().isEmpty()) {
                // unexpected, should be an "InvalidKeyPair.NotFound"
                // AmazonServiceException
                ec2.deleteKeyPair(new DeleteKeyPairRequest(keyPairName));
            }
        } catch (AmazonServiceException e) {
            if ("InvalidKeyPair.NotFound".equals(e.getErrorCode())) {
                // key does not exist
            } else {
                throw e;
            }
        }
        CreateKeyPairResult createKeyPairResult = ec2.createKeyPair(new CreateKeyPairRequest(keyPairName));
        KeyPair keyPair = createKeyPairResult.getKeyPair();
        return keyPair;
    }

    /**
     * Send email with Amazon Simple Email Service.
     * <p/>
     * 
     * Please note that the sender (ie 'from') must be a verified address (see
     * {@link AmazonSimpleEmailService#verifyEmailAddress(com.amazonaws.services.simpleemail.model.VerifyEmailAddressRequest)}
     * ).
     * <p/>
     * 
     * Please note that the sender is a CC of the meail to ease support.
     * <p/>
     * 
     * @param subject
     * @param body
     * @param from
     * @param toAddresses
     */

    public void sendEmail(String subject, String body, String from, String... toAddresses) {

        SendEmailRequest sendEmailRequest = new SendEmailRequest( //
                from, //
                new Destination().withToAddresses(toAddresses).withCcAddresses(from), //
                new Message(new Content(subject), //
                        new Body(new Content(body))));
        SendEmailResult sendEmailResult = ses.sendEmail(sendEmailRequest);
        System.out.println(sendEmailResult);
    }

    /**
     * Send email with Amazon Simple Email Service.
     * <p/>
     * 
     * Please note that the sender (ie 'from') must be a verified address (see
     * {@link AmazonSimpleEmailService#verifyEmailAddress(com.amazonaws.services.simpleemail.model.VerifyEmailAddressRequest)}
     * ).
     * <p/>
     * 
     * Please note that the sender is a CC of the meail to ease support.
     * <p/>
     * 
     * @param subject
     * @param body
     * @param from
     * @param toAddresses
     * @throws MessagingException
     * @throws AddressException
     */
    public void sendEmail(String subject, String body, AccessKey accessKey, KeyPair sshKeyPair, java.security.KeyPair x509KeyPair,
            X509Certificate x509Certificate, SigningCertificate signingCertificate, String from, String toAddress) {

        try {
            Session s = Session.getInstance(new Properties(), null);
            MimeMessage msg = new MimeMessage(s);

            msg.setFrom(new InternetAddress(from));
            msg.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toAddress));
            msg.addRecipient(javax.mail.Message.RecipientType.CC, new InternetAddress(from));

            msg.setSubject(subject);

            MimeMultipart mimeMultiPart = new MimeMultipart();
            msg.setContent(mimeMultiPart);

            // body
            BodyPart plainTextBodyPart = new MimeBodyPart();
            mimeMultiPart.addBodyPart(plainTextBodyPart);
            plainTextBodyPart.setContent(body, "text/plain");

            // aws-credentials.txt / accessKey
            {
                BodyPart awsCredentialsBodyPart = new MimeBodyPart();
                awsCredentialsBodyPart.setFileName("aws-credentials.txt");
                StringWriter awsCredentialsStringWriter = new StringWriter();
                PrintWriter awsCredentials = new PrintWriter(awsCredentialsStringWriter);
                awsCredentials.println("#Insert your AWS Credentials from http://aws.amazon.com/security-credentials");
                awsCredentials.println("#" + new DateTime());
                awsCredentials.println();
                awsCredentials.println("# ec2, rds & elb tools use accessKey and secretKey");
                awsCredentials.println("accessKey=" + accessKey.getAccessKeyId());
                awsCredentials.println("secretKey=" + accessKey.getSecretAccessKey());
                awsCredentials.println();
                awsCredentials.println("# iam tools use AWSAccessKeyId and AWSSecretKey");
                awsCredentials.println("AWSAccessKeyId=" + accessKey.getAccessKeyId());
                awsCredentials.println("AWSSecretKey=" + accessKey.getSecretAccessKey());

                awsCredentialsBodyPart.setContent(awsCredentialsStringWriter.toString(), "text/plain");
                mimeMultiPart.addBodyPart(awsCredentialsBodyPart);
            }
            // private ssh key
            {
                BodyPart keyPairBodyPart = new MimeBodyPart();
                keyPairBodyPart.setFileName(sshKeyPair.getKeyName() + ".pem.txt");
                keyPairBodyPart.setContent(sshKeyPair.getKeyMaterial(), "application/octet-stream");
                mimeMultiPart.addBodyPart(keyPairBodyPart);
            }

            // x509 private key
            {
                BodyPart x509PrivateKeyBodyPart = new MimeBodyPart();
                x509PrivateKeyBodyPart.setFileName("pk-" + signingCertificate.getCertificateId() + ".pem.txt");
                String x509privateKeyPem = Pems.pem(x509KeyPair.getPrivate());
                x509PrivateKeyBodyPart.setContent(x509privateKeyPem, "application/octet-stream");
                mimeMultiPart.addBodyPart(x509PrivateKeyBodyPart);
            }
            // x509 private key
            {
                BodyPart x509CertificateBodyPart = new MimeBodyPart();
                x509CertificateBodyPart.setFileName("cert-" + signingCertificate.getCertificateId() + ".pem.txt");
                String x509CertificatePem = Pems.pem(x509Certificate);
                x509CertificateBodyPart.setContent(x509CertificatePem, "application/octet-stream");
                mimeMultiPart.addBodyPart(x509CertificateBodyPart);
            }
            // Convert to raw message
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            msg.writeTo(out);

            RawMessage rawMessage = new RawMessage();
            rawMessage.setData(ByteBuffer.wrap(out.toString().getBytes()));

            ses.sendRawEmail(new SendRawEmailRequest().withRawMessage(rawMessage));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}
