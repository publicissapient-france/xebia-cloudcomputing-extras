Hello,

Here are the credentials to connect to Xebia Amazon AWS/EC2 PRODUCTION infrastructure.

WARNING this is a PRODUCTION environment.

USER SIGN-IN CREDENTIALS
========================

Authentication page: https://xebia-france-production.signin.aws.amazon.com/console .

 * User Name: ${loginUserName}
 * Password: ${loginPassword}


USER ACCESS CREDENTIALS
=======================

ACCESS KEYS
-----------

 * Access Key Id: ${accessKeyId}
 * Secret Access Key:  ${accessKeySecretId}

<#if attachedCredentialsFileName?exists >
See attached "${attachedCredentialsFileName}".
</#if>

SIGNING CERTIFICATE
-------------------

 * Certificate Id: ${x509CertificateId}

<#if attachedX509PrivateKeyFileName?exists >
See attached "${attachedX509PrivateKeyFileName}" and "${attachedX509CertificateFileName}".
<#else>
The certificate and its private key have already been generated and sent.
</#if>


SERVER CONNECTION CREDENTIALS / SSH KEY
=======================================

 * SSH key name: ${sshKeyName}
 * SSH key finger print: ${sshKeyFingerprint}

<#if attachedSshKeyFileName?exists >
See attached "${attachedSshKeyFileName}".
<#else>
The SSH key and its private key have already been generated and sent.
</#if> 



Thanks,

Cyrille