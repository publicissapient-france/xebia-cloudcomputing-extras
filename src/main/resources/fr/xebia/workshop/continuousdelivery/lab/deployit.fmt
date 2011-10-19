#summary Continuous Delivery with Jenkins and rundeck Lab for team '${infrastructure.identifier}'

*<font size="5">Continuous Delivery with DeployIt for Team '${infrastructure.identifier}'</font>*

= Your architecture =

<img width="400" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/per-team-infrastructure.png" />

<table>
<tr><td> *SSH Private key* </td><td> [https://s3-eu-west-1.amazonaws.com/continuous-delivery/continuous-delivery-workshop.pem continuous-delivery-workshop.pem]</td></tr>
<tr><td> *!GitHub project repository url* </td><td> [${infrastructure.githubRepositoryHomePageUrl}] </td></tr>
<tr><td> *Jenkins URL* </td><td> [${infrastructure.jenkinsUrl}] </td></tr>
<tr><td> *Rundeck URL* </td><td> [${infrastructure.rundeckUrl}] </td></tr>
<tr><td> *Deployit URL* </td><td> [${infrastructure.deployitUrl}] </td></tr>
<tr><td> *Nexus URL* </td><td> [${infrastructure.nexusUrl}] </td></tr>
<tr><td> *Tomcat Dev URL* </td><td> [${infrastructure.devTomcatUrl}] </td></tr>
<tr><td> *Tomcat Dev SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.devTomcat.publicDnsName}
}}}
 </td></tr>
<tr><td> *Tomcat Valid 1 URL* </td><td> [${infrastructure.validTomcat1Url}] </td></tr>
<tr><td> *Tomcat Valid 1 SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat1.publicDnsName}
}}}
 </td></tr>
<tr><td> *Tomcat Valid 2 URL* </td><td> [${infrastructure.validTomcat2Url}] </td></tr>
<tr><td> *Tomcat Valid 2 SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat2.publicDnsName}
}}}
 </td></tr>
</table>

----

= Lab. Automated Tomcat Deployment with Deployit  =

== Lab ==

=== Deploy Petclinic from Deployit ===

==== Create the target environment Valid ====
 # Connect via SSH to the rundeck server:
  {{{
ssh -i continuous-delivery-workshop.pem ec2-user@${infrastructure.rundeck.publicDnsName}
sudo su - deployit
}}}
 # Create a file (*setup.py*) to define your target environment
{{{
host_valid_1 = repository.create("Infrastructure/host-valid-1",factory.configurationItem("Host", {'operatingSystemFamily':'UNIX', 'accessMethod':'SSH_SFTP', 'address':'${infrastructure.validTomcat1.privateDnsName}', 'username':'tomcat'}))
tomcat_valid_1 = repository.create(host_valid_1.id+"/tomcat6",factory.configurationItem("TomcatUnmanagedServer",{'host':host_valid_1, 'tomcatHome':'/opt/tomcat/apache-tomcat-6', 'baseUrl':'http://'+host_valid_1.values['address']+':8080', 'startCommand':'/opt/tomcat/apache-tomcat-6/bin/catalina.sh start', 'stopCommand':'/opt/tomcat/apache-tomcat-6/bin/catalina.sh stop'}))
deployit.print(repository.read(tomcat_valid_1.id))
host_valid_2 = repository.create("Infrastructure/host-valid-2",factory.configurationItem("Host", {'operatingSystemFamily':'UNIX', 'accessMethod':'SSH_SFTP', 'address':'${infrastructure.validTomcat2.privateDnsName}', 'username':'tomcat'}))
tomcat_valid_2 = repository.create(host_valid_2.id+"/tomcat6",factory.configurationItem("TomcatUnmanagedServer",{'host':host_valid_2, 'tomcatHome':'/opt/tomcat/apache-tomcat-6', 'baseUrl':'http://'+host_valid_2.values['address']+':8080', 'startCommand':'/opt/tomcat/apache-tomcat-6/bin/catalina.sh start', 'stopCommand':'/opt/tomcat/apache-tomcat-6/bin/catalina.sh stop'}))
deployit.print(repository.read(tomcat_valid_2.id))
env = repository.create("Environments/Continuous-Valid", factory.configurationItem("Environment", {"members":[host_valid_1.id,tomcat_valid_1.id,host_valid_2.id,tomcat_valid_2.id]}))
deployit.print(repository.read(env.id))
}}}
 # Execute the CLI script
  {{{
deployit-cli/bin/cli.sh -username admin -password admin -f ~/setup.py
  }}}
 # Connect to your Deployit server ${infrastructure.deployitUrl} (login=admin, password=admin)
 # Verify your environment has been created.
 <img height=90" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/deployit-check-env-screenshot.png" />

==== Configure Jenkins ====
 # Connect on Jenkins and update the project
 <img height=90" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/deployit-update-project-screenshot.png" />
 # Update the deployit-settings.xml file by replacing the password 'XXXXXX' with the correct value.
 {{{
vi deployit-settings.xml
git ci -a -m "set the right password"
git push
 }}}
 The last line will trigger a new build by Jenking and at then will deploy the application.
 # Connect to your Deployit server ${infrastructure.deployitUrl} (login=admin, password=admin)
 # Verify the application has been deployed
<img height=90" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/deployit-check-deployment-ok.png" />

----
*END OF THE LAB THANK YOU!*

_${generator}_
