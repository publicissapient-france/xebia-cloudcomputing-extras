# Lab Pre Requisite

* [Maven](http://maven.apache.org) 3.0.x

* [YSlow](http://developer.yahoo.com/yslow/) Plugin installed on your favorite browser ( [chrome](https://chrome.google.com/webstore/detail/ninejjcohidippngpapiilnmkgllmakh), [firefox](https://addons.mozilla.org/en-US/firefox/addon/yslow/))

* Google PageSpeed extension for Chrome or Firefox


# Lab Agenda

# The application

![Cocktail App Screenshot](cocktail-app-screenshot.png)

## Initial Architecture

![Web Caching Workshop Initial Architecture](workshop-initial-architecture.png)

## Add Expire Headers

![Web Caching Workshop Architecture - Add Expire Headers](workshop-expires-header.png)

## Use Apache Httpd as a Caching Proxy

![Web Caching Workshop Architecture - Apache Httpd as a Caching Proxy](workshop-apache-mod-cache.png)

## Use Varnish Caching Proxy

![Web Caching Workshop Architecture - Varnish Caching Proxy](workshop-varnish.png)

## Use Amazon CloudFront CDN

![Web Caching Workshop Architecture - Amazon CloudFront CDN](workshop-amazon-cloudfront.png)

# 1. Connect to your Cokctail App

Connect to your application <http://xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com/cocktail/>.

![Cocktail App Screenshot](cocktail-app-screenshot.png)


# 2. Download Cocktail App

Get the Cocktail App on Github (download it or clone it) : <https://github.com/xebia-france/workshop-web-caching-cocktail>

![Github workshop-web-caching-cocktail](github-workshop-web-caching-cocktail-screenshot.png)


# 3. Build Cocktail App

Build the application with Maven

	mvn package
	

# 4. Create your CDN Distribution with Amazon CloudFront

<div style="color:blue;font-weight:bold;font-size:150%">Note: as the creation of a CDN distribution takes several minutes with Amazon CloudFront, we create it at the beginning of the lab and we will use it later.</div>

1. Connect to Amazon AWS Console: <https://xebia-france.signin.aws.amazon.com/console>
1. Open `Services > CloudFront tab`

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-0.png)

1. Click on `Create Distribution`

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-0.1.png)

1. Select `Download` delivery method

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-1.png)

1. Enter `Origin Domain Name: xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com` and `Origin Protocol Policy: Match Viewer`

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-2.png)
   
1. Keep default cache behaviors
   
   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-3.png)
   
1. Keep default Distribution details

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-4.png)

1. Review your configuration

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-5.png)

1. Verify that your distribution is being created

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-6.png)
   
<div style="color:green;font-weight:bold;font-size:150%">Congratulations, you have created your CDN Distribution!</div>


<div style="color:blue;font-weight:bold;font-size:150%">Note: Don't wait for the completion of this creation, go to the next exercice.</div>

# 5. Find Web Caching Issues with YSlow

Analyse with YSlow the web page <http://xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com/cocktail/>.

During this lab, we will focus on two YSlow recommandations: 

* `Add Expires Headers`

   ![YSlow Add Expires Headers](cocktail-1-yslow-expires-header.png)

* `Use a Content Delivery Network (CDN)`

    ![YSlow Add Expires Headers](cocktail-1-yslow-cdn.png)

# 6. Add Expires Headers

See docs:

* [W3C > RFC 2616 -  Hypertext Transfer Protocol - HTTP/1.1 > 14 Header Field Definitions > Cache-Control](http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9)
* [YSlow > Best Practices for Speeding Up Your Web Site > Add an Expires or a Cache-Control Header](http://developer.yahoo.com/performance/rules.html#expires)


## Info: Adding Expires Headers programmatically according to the business logic

<div style="color:green;font-weight:normal;font-size:100%">This step is purely informational, the next exercise is 'Adding Expires Headers With a Servlet Filter'.</div>

You can define the caching policy according to the business logic. For example, an RSS feed could be cached for 5 minutes.

### Java - Spring MVC Sample

See [CocktailManager.java](https://github.com/xebia-france/workshop-web-caching-cocktail/blob/8f7e828a47a32a3f3de1ec0859602d23a9a61671/src/main/java/fr/xebia/cocktail/CocktailManager.java#L223).

![Java Code Sample - Expires Header](java-code-sample-expires-header.png)

### Behavior

1. Query the `/rss` URL:

        curl -v http://xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com/rss

1. Look at the `Cache-Control` and `Expires` header in the response

        < HTTP/1.1 200 OK
        < Cache-Control: public, max-age=300
        < Content-Language: en-US
        < Content-Type: application/rss+xml;charset=ISO-8859-1
        < Date: Wed, 06 Jun 2012 18:40:16 GMT
        < Expires: Wed, 06 Jun 2012 18:45:16 GMT
        < Server: Apache-Coyote/1.1
        < transfer-encoding: chunked
        < Connection: keep-alive


## Adding Expires Headers With a Servlet Filter

![Web Caching Workshop Architecture - Add Expire Headers](workshop-expires-header.png)

Docs available at [Google Code - Xebia France > ExpiresFilter](http://code.google.com/p/xebia-france/wiki/ExpiresFilter).

1. Modify `src/main/webapp/WEB-INF/web.xml`([source](https://github.com/xebia-france/workshop-web-caching-cocktail/blob/f9258173f9ed2222ae6e62ed48e7b4f17e48edd1/src/main/webapp/WEB-INF/web.xml#L45)) to add an `ExpiresFilter`:

         	<filter>
        	   <filter-name>ExpiresFilter</filter-name>
        	   <filter-class>fr.xebia.servlet.filter.ExpiresFilter</filter-class>
        	   <init-param>
        	      <param-name>ExpiresByType image</param-name>
        	      <param-value>access plus 1 year</param-value>
        	   </init-param>
        	   <init-param>
        	      <param-name>ExpiresByType text/css</param-name>
        	      <param-value>access plus 1 year</param-value>
        	   </init-param>
        	   <init-param>
        	      <param-name>ExpiresByType application/javascript</param-name>
        	      <param-value>access plus 1 year</param-value>
        	   </init-param>
        	</filter>
        	...
        	<filter-mapping>
        	   <filter-name>ExpiresFilter</filter-name>
        	   <url-pattern>/*</url-pattern>
        	   <dispatcher>REQUEST</dispatcher>
        	</filter-mapping>

2. Repackage your application

        mvn package

3. Deploy the new version named of your application on your Amazon Elastic Beanstalk environment `xfr-cocktail-${teamIdentifier}` with `Version Label: 1.1.0-team-${teamIdentifier}`
 
     1. Connect to <https://console.aws.amazon.com/elasticbeanstalk/home?region=eu-west-1>
     
     1. Select your environment `xfr-cocktail-${teamIdentifier}` and click on `Deploy a Different Version`:
     
       ![Beanstalk Update Version 1](beanstalk-update-version-1.png)
     
     1. Select `Upload and deploy a new version` and enter `Version Label: 1.1.0-team-${teamIdentifier}`
    
       ![Beanstalk Update Version 2](beanstalk-update-version-2.png)
       
     1. Click on `Deploy Version` and wait for the deployment
     
     1. Verify with YSlow that your expiration headers appeared: <http://xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com/cocktail/>
     
<div style="color:blue;font-weight:bold;font-size:100%" markdown="1">Cheat sheet: deploy version `1.1.0` if you have a problem deploying your own patched version.</div>
 
<div style="color:green;font-weight:bold;font-size:150%">Congratulations!!! You fixed the first YSlow recommandation to add Expires Headers</div>


### Adding Expires Headers with Apache Httpd mod_expires

<div style="color:green;font-weight:normal;font-size:100%">This step is purely informational, the next exercise is 'Add a Caching Proxy with Apache mod_cache in front of the Tomcat Server'.</div>

[Apache > HTTP Server > Documentation > Version 2.2 > Modules > mod_expires](http://httpd.apache.org/docs/2.2/mod/mod_expires.html)

Sample of of `httpd.conf`

	ExpiresByType image "access plus 1 year"
	ExpiresByType text/css "access plus 1 year"
	ExpiresByType text/javascript "access plus 1 year"

# 7. Add a Caching Proxy with Apache mod_cache in front of the Tomcat Server

![Web Caching Workshop Architecture - Apache Httpd as a Caching Proxy](workshop-apache-mod-cache.png)
 
## Enable mod_proxy between Apache and Tomcat

[Apache > HTTP Server > Documentation > Version 2.2 > Modules > mod_proxy](http://httpd.apache.org/docs/2.2/mod/mod_proxy.html)

1. Download the `web-caching-workshop.pem` SSH private key

    	cd
    	mkdir .aws
    	cd .aws
    	wget http://xfr-workshop-caching.s3-website-eu-west-1.amazonaws.com/web-caching-workshop.pem
    	chmod 400 web-caching-workshop.pem
    	echo "Certificate 'web-caching-workshop.pem' installed under `pwd`"

1. Connect to your proxy server `www-cocktail-${teamIdentifier}.aws.xebiatechevent.info`

    	ssh -i web-caching-workshop.pem ec2-user@www-cocktail-${teamIdentifier}.aws.xebiatechevent.info

1. Configure Apache `mod_proxy` 

    1. Create a `conf.d/cocktail.conf` configuration fragment to configure `mod_proxy`

            sudo vi /etc/httpd/conf.d/cocktail.conf

    1. Add a `ProxyPass` directive in cocktail.conf

            # connect Apache to Tomcat
            ProxyPass / http://xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com/


1. Restart Apache	
	
        sudo service httpd restart
	
1. Verify opening in your browser <http://www-cocktail-${teamIdentifier}.aws.xebiatechevent.info/cocktail/>
	
<div style="color:red;font-weight:bold;font-size:100%" markdown="1">WARNING: a production ready `mod_proxy` configuration is more complex.</div>

	
## Enable mod_cache

See [Apache > HTTP Server > Documentation > Version 2.2 > Modules > mod_cache](http://httpd.apache.org/docs/2.2/mod/mod_cache.html)

1. Connect to your proxy server `www-cocktail-${teamIdentifier}.aws.xebiatechevent.info`

    	ssh -i web-caching-workshop.pem ec2-user@www-cocktail-${teamIdentifier}.aws.xebiatechevent.info

1. Enabled mod_disk_cache in `httpd.conf`

    1. Edit `httpd.conf`

            sudo vi /etc/httpd/conf/httpd.conf

    1. Unncoment 

            <IfModule mod_disk_cache.c>
                CacheEnable disk /
                CacheRoot "/var/cache/mod_proxy"
            </IfModule>
	
1. Restart Apache	
	
	    sudo service httpd restart

1. Verify that the Httpd Server successfully restarted opening in your browser <http://www-cocktail-${teamIdentifier}.aws.xebiatechevent.info/cocktail/>

<div style="color:red;font-weight:bold;font-size:100%" markdown="1">WARNING: a production ready `mod_disk_cache` configuration is more complex, you must schedule a disk cleaner (TODO add hyperlink to disk cleaner).</div>


## Check response caching with Apache Httpd mod_cache 

Apache Httpd 2.2 does not add a `X-Cache-Detail` header to the HTTP response in order to ease debugging of page caching.

This `X-Cache-Detail` header has been introduced in Apache Httpd 2.4 with the [`CacheDetailHeader` directive](http://httpd.apache.org/docs/2.4/mod/mod_cache.html#cachedetailheader).

As of Apache 2.2, you can check that a resource has been served by mod_cache rather than by Tomcat checking the existence of the `Age` header.

**Query twice the URL to load the resource in the caching resource.**

	curl -v http://www-cocktail-${teamIdentifier}.aws.xebiatechevent.info/css/bootstrap.min.css | more
	curl -v http://www-cocktail-${teamIdentifier}.aws.xebiatechevent.info/css/bootstrap.min.css | more
	
### Sample of response without caching


	< HTTP/1.1 200 OK
	< Date: Mon, 28 May 2012 13:57:24 GMT
	< Server: Apache-Coyote/1.1                        <==== RESPONSE GENERATED BY TOMCAT
	< Cache-Control: max-age=86400
	< Content-Type: text/css
	< Expires: Tue, 29 May 2012 13:57:24 GMT
	< Last-Modified: Sun, 01 Apr 2012 14:07:28 GMT
	< Content-Length: 81150
	< Connection: close
	

### Sample of response with caching

	< HTTP/1.1 200 OK
	< Date: Mon, 28 May 2012 13:43:18 GMT
	< Server: Apache/2.2.22 (Amazon)                   <==== RESPONSE GENERATED BY APACHE HTTPD
	< Last-Modified: Sun, 01 Apr 2012 14:07:28 GMT
	< Cache-Control: max-age=86400
	< Expires: Tue, 29 May 2012 13:08:40 GMT
	< Age: 222									       <==== 'Age': DURATION IN CACHE IN SECS
	< Content-Length: 81150
	< Connection: close
	< Content-Type: text/css
	


[W3C > RFC 2616 -  Hypertext Transfer Protocol - HTTP/1.1 > 14 Header Field Definitions > Age](http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.6)

> The Age response-header field conveys the sender's estimate of the amount of time since the response (or its revalidation) was generated at the origin server. A cached response is "fresh" if its age does not exceed its freshness lifetime.

# 8. Configure Varnish

![Web Caching Workshop Architecture - Varnish Caching Proxy](workshop-varnish.png)

* [ Varnish 3.0 Documentation](https://www.varnish-cache.org/docs/3.0/index.html)


## Connect via SSH to the varnish server

    ssh -i web-caching-workshop.pem ec2-user@www-cocktail-${teamIdentifier}.aws.xebiatechevent.info


## Setup Backend server

1. Edit `default.vcl` and setup your backend server:

        sudo vi /etc/varnish/default.vcl
    
1. Update `backend default`:

        backend default {
            .host = "xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com";
            .port = "80";
        }


1. Restart Varnish:

    	sudo service varnish restart

1. Verify that the Httpd Server successfully restarted opening in your browser <http://www-cocktail-${teamIdentifier}.aws.xebiatechevent.info:6081/cocktail/>



## Add X-Cache and X-Cacheable header

By default Varnish does not inform us about its execution, let's set up some configuration to keep informed about cache usage per request. X-Cache header will get HIT when resource was found in cache or MISS if not in cache. 

1. Edit `default.vcl`

        sudo vi /etc/varnish/default.vcl
	
1. Add `vcl_fetch` and `vcl_deliver` routines in `default.vcl` (after `backend default` directive):

        sub vcl_fetch {
        
            # Varnish determined the object was not cacheable
            if (!beresp.cacheable) {
                set beresp.http.X-Cacheable = "NO:Not Cacheable";
        
            # You are respecting the Cache-Control=private header from the backend
            } elsif (beresp.http.Cache-Control ~ "private") {
                set beresp.http.X-Cacheable = "NO:Cache-Control=private";
                return(pass);
            
            # You are extending the lifetime of the object artificially
            }  else {
                set beresp.http.X-Cacheable = "YES";
            }
            
            # ....
            
            return(deliver);
        }
        
        sub vcl_deliver {
            if (obj.hits > 0) {
                set resp.http.X-Cache = "HIT";
            } else {
                set resp.http.X-Cache = "MISS";
            }
        }


1. Restart Varnish:

        sudo service varnish restart
	
1. Verify the existence of the 

        curl -v http://www-cocktail-${teamIdentifier}.aws.xebiatechevent.info:6081/css/bootstrap.min.css | more

Now you should see the X-Cache header indicating wether the cache hit or miss, X-Cacheable will display wether the resource was cacheable or not and why.


### Sample of response without caching

	< HTTP/1.1 200 OK
	< Cache-Control: max-age=31536000
	< Content-Type: text/css
	< Expires: Mon, 03 Jun 2013 23:13:44 GMT
	< Last-Modified: Sun, 01 Apr 2012 14:07:28 GMT
	< Server: Apache-Coyote/1.1
	< X-Cacheable: YES                                <==== CACHEABLE BY VARNISH
	< Content-Length: 81150
	< Date: Sun, 03 Jun 2012 23:13:44 GMT
	< X-Varnish: 758000267
	< Age: 0
	< Via: 1.1 varnish
	< Connection: keep-alive
	< X-Cache: MISS                                    <==== VARNISH MISS


### Sample of response with caching

	< HTTP/1.1 200 OK
	< Cache-Control: max-age=31536000
	< Content-Type: text/css
	< Expires: Mon, 03 Jun 2013 23:13:44 GMT
	< Last-Modified: Sun, 01 Apr 2012 14:07:28 GMT
	< Server: Apache-Coyote/1.1                       <==== VARNISH DOESN'T MODIFY THE SERVER HEADER
	< X-Cacheable: YES                                <==== CACHEABLE BY VARNISH
	< Content-Length: 81150
	< Date: Sun, 03 Jun 2012 23:14:10 GMT
	< X-Varnish: 758000268 758000267
	< Age: 27
	< Via: 1.1 varnish
	< Connection: keep-alive
	< X-Cache: HIT                                    <==== VARNISH HIT


## Note about Varnish default behaviour

By default Varnish consider requests with Cookie and response with Set-Cookie not cacheable and just pass the request as a simple reverse proxy to the backend.
A solution is to force Varnish to cache resources even though there is one or more Cookie header present in request.

Add vcl_recv routine in `/etc/varnish/default.vcl`:

	sub vcl_recv {
	    if (req.restarts == 0) {
	        if (req.http.x-forwarded-for) {
	            set req.http.X-Forwarded-For =
	                req.http.X-Forwarded-For ", " client.ip;
	        } else {
	            set req.http.X-Forwarded-For = client.ip;
	        }
	    }
	    if (req.request != "GET" &&
	      req.request != "HEAD" &&
	      req.request != "PUT" &&
	      req.request != "POST" &&
	      req.request != "TRACE" &&
	      req.request != "OPTIONS" &&
	      req.request != "DELETE") {
	        /* Non-RFC2616 or CONNECT which is weird. */
	        return (pipe);
	    }
	    if (req.request != "GET" && req.request != "HEAD") {
	        /* We only deal with GET and HEAD by default */
	        return (pass);
	    }
	    if (req.http.Authorization) {
	        /* Not cacheable by default */
	        return (pass);
	    }
	     /* Now let's use the cache */
	    return (lookup);
	}


Restart Varnish and check access:

	sudo service varnish restart
	
	
	curl -v http://www-cocktail-${teamIdentifier}.aws.xebiatechevent.info:6081/css/bootstrap.min.css | more

With this setup, Varnish will cache resources even if a session Cookie is present in the request.

## Configure Backend probe

Backend probes will monitor backend health wich allows to use grace mode to keep delivering resources while backend server is down.

Update the `backend default` directive in `default.vcl`:

	backend default {
	    .host = "xfr-cocktail-clc.elasticbeanstalk.com";
	    .port = "80";
	    .probe = {
	        .url = "/";
	        .timeout = 0.3 s;
	        .window = 8;
	        .threshold = 3;
	        .initial = 3;
	    }
	}

Add req.grace timeout in vcl_recv and vcl_fetch routines `/etc/varnish/default.vcl`:

	sub vcl_recv {
	    # ....
	    set req.grace = 120m;
	    # ....
	}
	sub vcl_fetch {
	    # ....
	    set beresp.grace = 120m;
	    # ....
	}

Now if the backend goes down, Varnish will keep resources and deliver them during grace timeout.

# 9. Use Amazon CloudFront as a CDN to Deliver the Cacheable Content

![Web Caching Workshop Architecture - Amazon CloudFront CDN](workshop-amazon-cloudfront.png)

## Verify the behavior of the CloudFront Distribution

1. Go to `CloudFront tab`

   ![Cloudfront Create Cistribution ](cloudfront-create-distribution-0.png)
   
1. Get the base URL of your distribution

   The hostname of the distribution looks like `d1mm4v4zybjqbh.cloudfront.net`

   ![CloudFront Distribution Details](cloudfront-distribution-details.png)


1. Open the cocktail app in your browser via the CloudFront distribution URL (something like <http://d1mm4v4zybjqbh.cloudfront.net/>)

   ![CloudFront - Distribution - Cocktail App](cloudfront-cocktail-app-via-distribution.png)


## Look at the integration of the CDN URL in the application

Here is a technique to use switchable CDN URLs in your application. Other techniques exist.

Look at the use of `${r"${cdnUrl}"}` in `view.jsp` ([source](https://github.com/xebia-france/workshop-web-caching-cocktail/blob/9393f3f2d34edd29dcede18691c195020082f820/src/main/webapp/WEB-INF/views/cocktail/view.jsp#L11)):

	<link rel="shortcut icon" href="${r"${cdnUrl}${pageContext.request.contextPath}"}/img/favicon.ico">
	... 
	<link href="${r"${cdnUrl}${pageContext.request.contextPath}"}/css/bootstrap.min.css" media="screen" rel="stylesheet" type="text/css" />
	...
	<script src="${r"${cdnUrl}${pageContext.request.contextPath}"}/js/bootstrap.min.js" type="text/javascript"></script>

And the injection of `cdn_url` System Property as `cdnUrl` variable in JSP Expression Language in `spring-mvc-servlet.xml` ([source](https://github.com/xebia-france/workshop-web-caching-cocktail/blob/9393f3f2d34edd29dcede18691c195020082f820/src/main/webapp/WEB-INF/spring-mvc-servlet.xml#L47))

	<!-- source 'cdn_url' from the system-properties -->
	<context:property-placeholder system-properties-mode="OVERRIDE" />
	
	<!-- inject 'cdn_url' as "cdnUrl" in JSP EL variables -->
	<bean class="org.springframework.web.context.support.ServletContextAttributeExporter">
	    <property name="attributes">
	        <map>
	            <entry key="cdnUrl" value="${r"${cdn_url:}"}/>
	        </map>
	    </property>
	</bean>

## Declare `cdn_url` System Property

1. Edit the configuration of your Amazon Beanstalk Tomcat Environment

    ![Amazon Beanstalk Tomcat Environment](beanstalk-edit-configuration-1.png)

1. Add a `JVM Command Line Options: -Dcdn_url=http://...FIXME....cloudfront.net` and click on `Apply Changes`

    ![Amazon Beanstalk Tomcat Environment](beanstalk-edit-configuration-2.png)

1. Once your application is restarted, reopen in your browser <http://xfr-cocktail-${teamIdentifier}.elasticbeanstalk.com/>

1. Verify in the HTML source code that the CloudFront CDN Distribution servers 

    ![Cocktail App with cdn_url Source Code](cloudfront-cdn-url-in-html.png)



# 10. CONCLUSION

You learned in this workshop how to:

* Add expiration headers to a Java web application
* Use Apache Httpd as a Caching Proxy
* Configure Varnish Caching Proxy
* Use the Content Delivery Network Amazon CloudFront 
* Integrate CDN based URLs in a web application 

