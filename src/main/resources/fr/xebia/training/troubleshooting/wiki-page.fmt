= Formation Troubleshooting =

<#list infrastructures?keys as teamIdentifier>

== Equipe '${teamIdentifier}' ==
 * URL: [http://${infrastructures[teamIdentifier].PublicDnsName}:8080/petclinic/]
 * Paramètre de connexion SSH:
{{{
ssh -i training-troubleshooting.pem tomcat@${infrastructures[teamIdentifier].PublicDnsName}
}}}
 * Paramètres de connexion JMX
  ** Host: ${infrastructures[teamIdentifier].PublicDnsName}
  ** Port: 6969
  
</#list>