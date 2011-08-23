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

import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nonnull;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.security.auth.x500.X500Principal;

import org.apache.commons.lang.RandomStringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.jclouds.crypto.Pems;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.AccessKey;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.AddUserToGroupRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.CreateLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.GetGroupRequest;
import com.amazonaws.services.identitymanagement.model.GetGroupResult;
import com.amazonaws.services.identitymanagement.model.GetLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.GetUserRequest;
import com.amazonaws.services.identitymanagement.model.Group;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.identitymanagement.model.ListSigningCertificatesRequest;
import com.amazonaws.services.identitymanagement.model.LoginProfile;
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException;
import com.amazonaws.services.identitymanagement.model.SigningCertificate;
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
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendEmailResult;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;

import fr.xebia.cloud.cloudinit.FreemarkerUtils;

/**
 * Create Amazon IAM accounts.
 * 
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class AmazonAwsIamAccountCreator {

    private static final String BOUNCY_CASTLE_PROVIDER_NAME = "BC";

    static {
        // adds the Bouncy castle provider to java security
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        try {
            AmazonAwsIamAccountCreator amazonAwsIamAccountCreator = new AmazonAwsIamAccountCreator();
            amazonAwsIamAccountCreator.createUsers("Admins");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected AmazonEC2 ec2;

    protected AmazonIdentityManagement iam;

    protected final KeyPairGenerator keyPairGenerator;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected Session mailSession;

    protected Transport mailTransport;
    protected InternetAddress mailFrom;

    protected AmazonSimpleEmailService ses;

    public AmazonAwsIamAccountCreator() {
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA", BOUNCY_CASTLE_PROVIDER_NAME);
            keyPairGenerator.initialize(1024, new SecureRandom());

            InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("AwsCredentials.properties");
            Preconditions.checkNotNull(credentialsAsStream, "File '/AwsCredentials.properties' NOT found in the classpath");
            AWSCredentials awsCredentials = new PropertiesCredentials(credentialsAsStream);
            iam = new AmazonIdentityManagementClient(awsCredentials);

            ses = new AmazonSimpleEmailServiceClient(awsCredentials);

            ec2 = new AmazonEC2Client(awsCredentials);
            ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

            InputStream smtpPropertiesAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("smtp.properties");
            Preconditions.checkNotNull(smtpPropertiesAsStream, "File '/smtp.properties' NOT found in the classpath");

            final Properties smtpProperties = new Properties();
            smtpProperties.load(smtpPropertiesAsStream);

            mailSession = Session.getInstance(smtpProperties, null);
            mailTransport = mailSession.getTransport();
            if (smtpProperties.containsKey("mail.username")) {
                mailTransport.connect(smtpProperties.getProperty("mail.username"), smtpProperties.getProperty("mail.password"));
            } else {
                mailTransport.connect();
            }
            try {
                mailFrom = new InternetAddress(smtpProperties.getProperty("mail.from"));
            } catch (Exception e) {
                throw new MessagingException("Exception parsing 'mail.from' from 'smtp.properties'", e);
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * <p>
     * Create an Amazon IAM account and send the details by email.
     * </p>
     * <p>
     * Created elements:
     * </p>
     * <ul>
     * <li>password to login to the management console if none exists,</li>
     * <li>accesskey if none is active,</li>
     * <li></li>
     * </ul>
     * 
     * @param userName
     *            valid email used as userName of the created account.
     */
    public void createUser(@Nonnull final String userName, GetGroupResult groupDescriptor) throws Exception {
        Preconditions.checkNotNull(userName, "Given userName can NOT be null");
        logger.debug("Process user {}", userName);

        List<String> userAccountChanges = Lists.newArrayList();

        Map<String, String> templatesParams = Maps.newHashMap();
        templatesParams.put("awsCredentialsHome", "~/.aws");
        templatesParams.put("awsCommandLinesHome", "/opt/amazon-aws");

        User user;
        try {
            user = iam.getUser(new GetUserRequest().withUserName(userName)).getUser();
        } catch (NoSuchEntityException e) {
            logger.debug("User {} does not exist, create it", userName, e);
            user = iam.createUser(new CreateUserRequest(userName)).getUser();
            userAccountChanges.add("Create user");
        }

        List<BodyPart> attachments = Lists.newArrayList();

        // AWS WEB MANAGEMENT CONSOLE LOGIN & PASSWORD
        try {
            LoginProfile loginProfile = iam.getLoginProfile(new GetLoginProfileRequest(user.getUserName())).getLoginProfile();
            templatesParams.put("loginUserName", loginProfile.getUserName());
            templatesParams.put("loginPassword", "#your password has already been generated and sent to you#");

            logger.info("Login profile already exists {}", loginProfile);
        } catch (NoSuchEntityException e) {
            String password = RandomStringUtils.randomAlphanumeric(8);
            LoginProfile loginProfile = iam.createLoginProfile(new CreateLoginProfileRequest(user.getUserName(), password))
                    .getLoginProfile();
            userAccountChanges.add("Create user.login");
            templatesParams.put("loginUserName", loginProfile.getUserName());
            templatesParams.put("loginPassword", password);
        }

        // ADD USER TO GROUP
        Group group = groupDescriptor.getGroup();
        List<User> groupMembers = groupDescriptor.getUsers();

        boolean isUserInGroup = Iterables.any(groupMembers, new Predicate<User>() {
            public boolean apply(User groupMember) {
                return userName.equals(groupMember.getUserName());
            };
        });

        if (!isUserInGroup) {
            logger.debug("Add user {} to group {}", user, group);
            iam.addUserToGroup(new AddUserToGroupRequest(group.getGroupName(), user.getUserName()));
            groupMembers.add(user);
            userAccountChanges.add("Add user to group");
        }

        // ACCESS KEY
        boolean activeAccessKeyExists = false;
        ListAccessKeysResult listAccessKeysResult = iam.listAccessKeys(new ListAccessKeysRequest().withUserName(user.getUserName()));
        for (AccessKeyMetadata accessKeyMetadata : listAccessKeysResult.getAccessKeyMetadata()) {
            statusType status = statusType.fromValue(accessKeyMetadata.getStatus());
            if (statusType.Active.equals(status)) {
                logger.info("Key {} ({}) is already active, don't create another one", accessKeyMetadata.getAccessKeyId(),
                        accessKeyMetadata.getCreateDate());
                activeAccessKeyExists = true;
                templatesParams.put("accessKeyId", accessKeyMetadata.getAccessKeyId());
                templatesParams.put("accessKeySecretId", "#accessKey has already been generated and the secretId has been sent to you#");

                break;
            }
        }

        if (!activeAccessKeyExists) {
            AccessKey accessKey = iam.createAccessKey(new CreateAccessKeyRequest().withUserName(user.getUserName())).getAccessKey();
            userAccountChanges.add("Create user.accessKey");
            logger.debug("Created access key {}", accessKey);
            templatesParams.put("accessKeyId", accessKey.getAccessKeyId());
            templatesParams.put("accessKeySecretId", accessKey.getSecretAccessKey());

            // email attachment: aws-credentials.txt
            {
                BodyPart awsCredentialsBodyPart = new MimeBodyPart();
                awsCredentialsBodyPart.setFileName("aws-credentials.txt");
                templatesParams.put("attachedCredentialsFileName", awsCredentialsBodyPart.getFileName());
                String awsCredentials = FreemarkerUtils.generate(templatesParams, "/fr/xebia/cloud/amazon/aws/iam/aws-credentials.txt.fmt");
                awsCredentialsBodyPart.setContent(awsCredentials, "text/plain");
                attachments.add(awsCredentialsBodyPart);
            }

        }

        // SSH KEY PAIR
        String keyPairName;
        if (userName.endsWith("@xebia.fr") || userName.endsWith("@xebia.com")) {
            keyPairName = userName.substring(0, userName.indexOf("@xebia."));
        } else {
            keyPairName = userName.replace("@", "_at_").replace(".", "_dot_").replace("+", "_plus_");
        }

        try {
            List<KeyPairInfo> keyPairInfos = ec2.describeKeyPairs(new DescribeKeyPairsRequest().withKeyNames(keyPairName)).getKeyPairs();
            KeyPairInfo keyPairInfo = Iterables.getOnlyElement(keyPairInfos);
            templatesParams.put("sshKeyName", keyPairInfo.getKeyName());
            templatesParams.put("sshKeyFingerprint", keyPairInfo.getKeyFingerprint());
        } catch (AmazonServiceException e) {
            if ("InvalidKeyPair.NotFound".equals(e.getErrorCode())) {
                // ssh key does not exist, create it
                KeyPair keyPair = ec2.createKeyPair(new CreateKeyPairRequest(keyPairName)).getKeyPair();
                userAccountChanges.add("Create ssh key");

                logger.debug("Created ssh key {}", keyPair);
                templatesParams.put("sshKeyName", keyPair.getKeyName());
                templatesParams.put("sshKeyFingerprint", keyPair.getKeyFingerprint());

                BodyPart keyPairBodyPart = new MimeBodyPart();
                keyPairBodyPart.setFileName(keyPair.getKeyName() + ".pem");
                templatesParams.put("attachedSshKeyFileName", keyPairBodyPart.getFileName());
                keyPairBodyPart.setContent(keyPair.getKeyMaterial(), "application/octet-stream");
                attachments.add(keyPairBodyPart);
            } else {
                throw e;
            }
        }

        // X509 SELF SIGNED CERTIFICATE
        Collection<SigningCertificate> certificates = iam.listSigningCertificates(
                new ListSigningCertificatesRequest().withUserName(userName)).getCertificates();
        // filter active certificates
        certificates = Collections2.filter(certificates, new Predicate<SigningCertificate>() {
            @Override
            public boolean apply(SigningCertificate signingCertificate) {
                return statusType.Active.equals(statusType.fromValue(signingCertificate.getStatus()));
            }
        });

        if (certificates.isEmpty()) {
            java.security.KeyPair x509KeyPair = keyPairGenerator.generateKeyPair();
            X509Certificate x509Certificate = generateSelfSignedX509Certificate(userName, x509KeyPair);
            String x509CertificatePem = Pems.pem(x509Certificate);

            UploadSigningCertificateResult uploadSigningCertificateResult = iam.uploadSigningCertificate( //
                    new UploadSigningCertificateRequest(x509CertificatePem).withUserName(user.getUserName()));
            SigningCertificate signingCertificate = uploadSigningCertificateResult.getCertificate();
            templatesParams.put("x509CertificateId", signingCertificate.getCertificateId());
            userAccountChanges.add("Create x509 certificate");

            logger.debug("Created x509 certificate {}", signingCertificate);

            // email attachment: x509 private key
            {
                BodyPart x509PrivateKeyBodyPart = new MimeBodyPart();
                x509PrivateKeyBodyPart.setFileName("pk-" + signingCertificate.getCertificateId() + ".pem");
                templatesParams.put("attachedX509PrivateKeyFileName", x509PrivateKeyBodyPart.getFileName());
                String x509privateKeyPem = Pems.pem(x509KeyPair.getPrivate());
                x509PrivateKeyBodyPart.setContent(x509privateKeyPem, "application/octet-stream");
                attachments.add(x509PrivateKeyBodyPart);
            }
            // email attachment: x509 certifiate pem
            {
                BodyPart x509CertificateBodyPart = new MimeBodyPart();
                x509CertificateBodyPart.setFileName("cert-" + signingCertificate.getCertificateId() + ".pem");
                templatesParams.put("attachedX509CertificateFileName", x509CertificateBodyPart.getFileName());
                x509CertificateBodyPart.setContent(x509CertificatePem, "application/octet-stream");
                attachments.add(x509CertificateBodyPart);
            }

        } else {
            SigningCertificate signingCertificate = Iterables.getFirst(certificates, null);
            templatesParams.put("x509CertificateId", signingCertificate.getCertificateId());
        }

        sendEmail(templatesParams, attachments, userName);
    }

    public void createUsers(String groupName) {

        GetGroupResult groupDescriptor = iam.getGroup(new GetGroupRequest(groupName));

        URL emailsToVerifyURL = Thread.currentThread().getContextClassLoader().getResource("accounts-to-create.txt");
        Preconditions.checkNotNull(emailsToVerifyURL, "File 'accounts-to-create.txt' NOT found in the classpath");
        Collection<String> userNames;
        try {
            userNames = Resources.readLines(emailsToVerifyURL, Charsets.ISO_8859_1);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        for (String userName : userNames) {
            try {
                createUser(userName, groupDescriptor);
            } catch (Exception e) {
                logger.error("Failure to create user '{}'", userName, e);
            }

            // sleep 10 seconds to prevent "Throttling exception"
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * Generates a self signed x509 certificate identified by the given
     * <code>userName</code> and the given <code>keyPair</code>.
     * 
     * @param userName
     *            common name of {@link X500Principal} ("CN={userName}") used as
     *            subjectDN and issuerDN.
     * @param keyPair
     *            used for the certificate public and private key
     * @return self signed X509 certificate
     */
    @SuppressWarnings("deprecation")
    @Nonnull
    public X509Certificate generateSelfSignedX509Certificate(@Nonnull String userName, @Nonnull java.security.KeyPair keyPair) {
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

            return certGen.generate(keyPair.getPrivate(), BOUNCY_CASTLE_PROVIDER_NAME);

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * 
     * @param subject
     * @param htmlBody
     * @param toAddress
     * @throws MessagingException
     */
    public void sendEmail(Map<String, String> templatesParams, List<BodyPart> attachments, String toAddress) throws MessagingException {

        MimeBodyPart htmlAndPlainTextAlternativeBody = new MimeBodyPart();

        // TEXT AND HTML MESSAGE (gmail requires plain text alternative, otherwise, it displays tes 1st plain text attachment in the preview)
        MimeMultipart cover = new MimeMultipart("alternative");
        htmlAndPlainTextAlternativeBody.setContent(cover);
        BodyPart textHtmlBodyPart = new MimeBodyPart();
        String textHtmlBody = FreemarkerUtils.generate(templatesParams,
                "/fr/xebia/cloud/amazon/aws/iam/amazon-aws-iam-credentials-email.html.fmt");
        textHtmlBodyPart.setContent(textHtmlBody, "text/html");
        cover.addBodyPart(textHtmlBodyPart);

        BodyPart textPlainBodyPart = new MimeBodyPart();
        cover.addBodyPart(textPlainBodyPart);
        String textPlainBody = FreemarkerUtils.generate(templatesParams,
                "/fr/xebia/cloud/amazon/aws/iam/amazon-aws-iam-credentials-email.txt.fmt");
        textPlainBodyPart.setContent(textPlainBody, "text/plain");

        MimeMultipart content = new MimeMultipart("related");
        content.addBodyPart(htmlAndPlainTextAlternativeBody);

        // ATTACHMENTS
        for (BodyPart bodyPart : attachments) {
            content.addBodyPart(bodyPart);
        }

        MimeMessage msg = new MimeMessage(mailSession);

        msg.setFrom(mailFrom);
        msg.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toAddress));
        msg.addRecipient(javax.mail.Message.RecipientType.CC, mailFrom);

        msg.setSubject("Xebia France Amazon EC2 Credentials");
        msg.setContent(content);

        mailTransport.sendMessage(msg, msg.getAllRecipients());
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

}
