Hello,

Here are the command line tools for the Xebia Amazon AWS/EC2 training.

 - EC2 api tools: http://s3.amazonaws.com/ec2-downloads/ec2-api-tools.zip and unzip it under "${awsCommandLinesHome}/ec2" ("${awsCommandLinesWindowsHome}\ec2" on Windows)
 - RDS command line tools: http://s3.amazonaws.com/rds-downloads/RDSCli.zip and unzip it under "${awsCommandLinesHome}/rds" ("${awsCommandLinesWindowsHome}\rds" on Windows)
 - ElasticLoadBalancing tools: http://ec2-downloads.s3.amazonaws.com/ElasticLoadBalancing.zip and unzip it under "${awsCommandLinesHome}/elb" ("${awsCommandLinesWindowsHome}\elb" on Windows)

Configure on Linux / MacOS X

Here is a profile fragment to add to your profile. DO NOT FORGET TO MODIFY THE JAVA_HOME VARIABLE TO MATCH YOURS.
=======================
<#include "profile-fragment.fmt">
=======================

Configure on Windows

Create the followings environment variables. TO MODIFY THE JAVA_HOME VARIABLE TO MATCH YOURS.
=======================
<#include "windows-fragment.fmt">
=======================

Thanks,

Cyrille