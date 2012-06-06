#summary Continuous Delivery with Jenkins and rundeck Lab for team '${infrastructure.identifier}'

*<font size="5">Continuous Delivery Apache Tomcat Maven Plugin for Team '${infrastructure.identifier}'</font>*

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

= Lab : Tomcat Maven Plugin =

== Architecture ==

<img width="400" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/tomcat-maven-plugin.png"/>

== Lab ==
*Goal:* Run the current version of the application on the laptop of the team, then deploy it on the Tomcat Development instance
=== Run the application in developement mode ===
 * Run Tomcat on the application
  ## Go to your application folder : 
{{{
cd ~/continuous-delivery-workshop/${infrastructure.githubRepositoryName} 
}}}
  ## Modify one more time the welcome.jsp. It's not needed to commit or push the modification on git.
{{{
~/continuous-delivery-workshop/${infrastructure.githubRepositoryName}/src/main/webapp/welcome.jsp
}}}
  ## Launch the application thanks to the Tomcat6 Maven Plugin. 
{{{
   mvn tomcat6:run
}}}
  ## Connect to the application and check your modification
{{{
http://localhost:8080/xebia-petclinic-lite
}}}

=== Deploy the application to the remote Dev Instance of Tomcat ===
 * Deploy the application on the dev instance of Tomcat
  ## As previously, go to the root directory of the project
{{{
cd ~/continuous-delivery-workshop/${infrastructure.githubRepositoryName} 
}}}   

  ## Deploy the application to Tomcat Dev server. The Maven goal is *tomcat6:deploy*, the parameters needed for this Maven plugin are:
 * *tomcat.password*: a Tomcat user granted to use Tomcat Manager
 * *tomcat.username*: password of the user
 * *maven.tomcat.url*: URL to the Tomcat Manager where the application will be deployed

*Note*: as you can see in the {{{~/continuous-delivery-workshop/${infrastructure.githubRepositoryName}/pom.xml}}}, the Tomcat6 Maven Plugin is configured with the parameter *update* that allows the application to be update if it's already deployed on the server.  
For further information, please go to [http://tomcat.apache.org/maven-plugin-2.0-SNAPSHOT/tomcat6-maven-plugin/deploy-mojo.html] 
  
 * Answer : 
 {{{
 mvn tomcat6:deploy -Dtomcat.password=admin -Dtomcat.username=admin -Dmaven.tomcat.url=${infrastructure.devTomcatUrl}manager
 }}}

  ## Connect to the application and check your modification
{{{
${infrastructure.devTomcatUrl}xebia-petclinic-lite
}}}
----
_${generator}_
