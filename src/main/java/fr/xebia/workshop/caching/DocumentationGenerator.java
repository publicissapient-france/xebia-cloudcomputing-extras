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
package fr.xebia.workshop.caching;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import fr.xebia.cloud.amazon.aws.tools.AmazonAwsUtils;
import fr.xebia.cloud.cloudinit.FreemarkerUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class DocumentationGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationGenerator.class);

    public static void main(String[] args) {
        AWSCredentials awsCredentials = AmazonAwsUtils.loadAwsCredentials();

        WorkshopInfrastructure workshopInfrastructure = new WorkshopInfrastructure()
                .withTeamIdentifiers("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
                .withAwsAccessKeyId(awsCredentials.getAWSAccessKeyId())
                .withAwsSecretKey(awsCredentials.getAWSSecretKey())
                .withKeyPairName("web-caching-workshop")
                .withBeanstalkNotificationEmail("cleclerc@xebia.fr");

        DocumentationGenerator documentationGenerator = new DocumentationGenerator();
        try {
            documentationGenerator.generateDocs(workshopInfrastructure, "target/wiki/");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void generateDocs(WorkshopInfrastructure workshopInfrastructure, String baseWikiFolder) throws IOException {
        File wikiBaseFolder = new File(baseWikiFolder);
        if (wikiBaseFolder.exists()) {
            logger.debug("Delete wiki folder {}", wikiBaseFolder);
            wikiBaseFolder.delete();
        }
        wikiBaseFolder.mkdirs();


        Map<String, String> wikiPageNamesByTeamIdentifier = Maps.newHashMap();

        for (String teamIdentifier : workshopInfrastructure.getTeamIdentifiers()) {

            Map<String, Object> rootMap = Maps.newHashMap();
            rootMap.put("infrastructure", workshopInfrastructure);
            rootMap.put("teamIdentifier", teamIdentifier);
            String templatePath = "/fr/xebia/workshop/caching/lab.md.ftl";
            rootMap.put("generator", "This page has been generaterd by '{{{" + getClass() + "}}}' with template '{{{" + templatePath + "}}}' on the "
                    + new DateTime());
            String page = FreemarkerUtils.generate(rootMap, templatePath);
            String wikiPageName = "Lab_team_" + teamIdentifier;
            wikiPageNamesByTeamIdentifier.put(teamIdentifier, wikiPageName);
            File wikiPageFile = new File(wikiBaseFolder, wikiPageName + ".md");
            Files.write(page, wikiPageFile, Charsets.UTF_8);
            logger.debug("Generated file {}", wikiPageFile.getAbsoluteFile());
        }


        StringWriter indexPageStringWriter = new StringWriter();
        PrintWriter indexPageWriter = new PrintWriter(indexPageStringWriter);

        indexPageWriter.println("# Labs Per Team");

        for (String teamIdentifier : new TreeSet<String>(workshopInfrastructure.getTeamIdentifiers())) {
            indexPageWriter.println(
                    "* [Lab for team " + teamIdentifier + "]" +
                    "(https://github.com/xebia-france/workshop-web-caching-cocktail/wiki/" + wikiPageNamesByTeamIdentifier.get(teamIdentifier) + ")");
        }

        String indexPageName = "Home";
        Files.write(indexPageStringWriter.toString(), new File(baseWikiFolder, indexPageName + ".md"), Charsets.UTF_8);

        System.out.println("GENERATED WIKI PAGES TO BE COMMITTED IN XEBIA-FRANCE GITHUB");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Base folder: " + baseWikiFolder);
        System.out.println("All the files in " + baseWikiFolder + " must be committed in https://xebia-france.googlecode.com/svn/wiki");
        System.out.println("Index page: " + indexPageName);
        System.out.println("Per team pages: \n\t" + Joiner.on("\n\t").join(new TreeSet<String>(wikiPageNamesByTeamIdentifier.values())));
    }
}
