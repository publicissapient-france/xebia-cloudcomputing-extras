export JAVA_HOME = /Library/Java/Home

export EC2_HOME=${awsCommandLinesHome}/ec2
export AWS_RDS_HOME=${awsCommandLinesHome}/rds
export AWS_ELB_HOME=${awsCommandLinesHome}/elb
export AWS_IAM_HOME=${awsCommandLinesHome}/iam

# EC2_REGION works for rds tools and elb tools but not for ec2 tools
export EC2_REGION=eu-west-1
# for ec2 tools
export EC2_URL=https://ec2.eu-west-1.amazonaws.com

export AWS_CREDENTIAL_FILE=${awsCredentialsHome}/${attachedCredentialsFileName}
export EC2_CERT=${awsCredentialsHome}/${attachedX509CertificateFileName}
export EC2_PRIVATE_KEY=${awsCredentialsHome}/${attachedX509PrivateKeyFileName}
