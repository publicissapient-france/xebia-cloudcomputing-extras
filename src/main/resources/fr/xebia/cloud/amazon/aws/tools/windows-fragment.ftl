EC2_HOME=${awsCommandLinesWindowsHome}\ec2
AWS_RDS_HOME=${awsCommandLinesWindowsHome}\rds
AWS_ELB_HOME=${awsCommandLinesWindowsHome}\elb

PATH=%PATH%;%EC2_HOME%\bin;%AWS_RDS_HOME%\bin;%AWS_ELB_HOME%\bin

# EC2_REGION works for rds tools and elb tools but not for ec2 tools
EC2_REGION=eu-west-1
# for ec2 tools
EC2_URL=https://ec2.eu-west-1.amazonaws.com

#Credentials files for user ${userName}, previously sent in email "[Xebia Amazon AWS Workshop] Credentials"
AWS_CREDENTIAL_FILE=${awsCredentialsWindowsHome}\${credentialsFileName}
EC2_CERT=${awsCredentialsWindowsHome}\${X509CertificateFileName}
EC2_PRIVATE_KEY=${awsCredentialsWindowsHome}\${X509PrivateKeyFileName}

JAVA_HOME=\PATH\TO\JAVA\HOME