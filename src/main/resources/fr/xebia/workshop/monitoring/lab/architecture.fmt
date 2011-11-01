= Open source java monitoring =

[http://xebia-france.googlecode.com/svn/wiki/oss-monitoring/Archi-OSS-monitoring.png]

= Your Architecture = 

=== Shared env ===

|| *Travel prod frontal 1* || 46.137.97.78 || prod1.travel.xebia-tech-event.info ||
|| *Travel prod frontal 2* || 46.137.98.212 || prod2.travel.xebia-tech-event.info ||
|| *Travel prod antifraude* || 46.137.168.248 || || 
|| *Travel pre-prod frontal 1* ||  46.137.98.234 || test.travel.xebia-tech-event.info ||
|| *Travel pre-prod antifraude* || 46.137.99.111  || ||

=== Your env ===

|| *SSH Private Key* ||  [https://s3-eu-west-1.amazonaws.com/workshop-monitoring/graphite-workshop.pem Download] ||
|| *Monitoring server IP* || ${monitoring-ip}  ||
|| *Monitoring SSH connexion* ||  ssh -i graphite-workshop.pem root@${monitoring-public-dns}  ||
|| *Nagios IP* ||  ${nagios-ip} ||
|| *Nagios SSH connexion* ||  ssh -i graphite-workshop.pem root@${nagios-public-dns}  ||

= Environnement Setup =

# Get the SSH private key oss-monitoring.pem to connect to the servers

{{{
mkdir ~/.aws/
curl https://s3-eu-west-1.amazonaws.com/workshop-monitoring/graphite-workshop.pem --output ~/.aws/graphite-workshop.pem
chmod 400 ~/.aws/graphite-workshop.pem
}}}

= Labs =

  # [MonitoringOpenSourceVisualVM Discover bean with VisualVm]
  # [MonitoringOpenSourceJMXTrans JMX Querying with JMXTrans]
  # [MonitoringOpenSourceGraphite Installing Graphite]
  # [MonitoringOpenSourceGraphiteURL Graph with Graphite]
  # [MonitoringOpenSourceGraphiteNagios Create Nagios Alert with graphite]