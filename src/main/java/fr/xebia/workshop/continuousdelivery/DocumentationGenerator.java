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
package fr.xebia.workshop.continuousdelivery;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import fr.xebia.cloud.cloudinit.FreemarkerUtils;

public class DocumentationGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationGenerator.class);

    private static final String TEMPLATE_ROOT_PATH = "/fr/xebia/workshop/continuousdelivery/lab/";

    private static final List<String> TEMPLATE_LAB_NAMES = Arrays.asList(
            "apache-tomcat-maven-plugin",
            "jenkins-remote-ssh",
            "rundeck",
            "deployit");

    private static final String SETUP_TEMPLATE_NAME = "setup";

    public void generateDocs(Collection<TeamInfrastructure> teamsInfrastructures, String baseWikiFolder) throws IOException {
        File wikiBaseFolder = new File(baseWikiFolder);
        if (wikiBaseFolder.exists()) {
            logger.debug("Delete wiki folder {}", wikiBaseFolder);
            wikiBaseFolder.delete();
        }
        wikiBaseFolder.mkdirs();

        List<String> generatedWikiPageNames = Lists.newArrayList();
        List<String> setupGeneratedWikiPageNames = Lists.newArrayList();
        HashMap<TeamInfrastructure, List<String>> teamsPages = Maps.newHashMap();

        for (TeamInfrastructure infrastructure : teamsInfrastructures) {
            List<String> generatedForTeam = Lists.newArrayList();
            for (String template : TEMPLATE_LAB_NAMES) {
                try {
                    Map<String, Object> rootMap = Maps.newHashMap();
                    rootMap.put("infrastructure", infrastructure);
                    String templatePath = TEMPLATE_ROOT_PATH + template + ".fmt";
                    rootMap.put("generator", "This page has been generaterd by '{{{" + getClass() + "}}}' with template '{{{" + templatePath + "}}}' on the "
                            + new DateTime());
                    String page = FreemarkerUtils.generate(rootMap, templatePath);
                    String wikiPageName = "ContinuousDeliveryWorkshopLab_" + infrastructure.getIdentifier() + "_" + template;
                    wikiPageName = wikiPageName.replace('-', '_');
                    generatedWikiPageNames.add(wikiPageName);
                    generatedForTeam.add(wikiPageName);
                    File wikiPageFile = new File(wikiBaseFolder, wikiPageName + ".wiki");
                    Files.write(page, wikiPageFile, Charsets.UTF_8);
                    logger.debug("Generated file {}", wikiPageFile);
                } catch (Exception e) {
                    logger.error("Exception generating doc for {}", infrastructure, e);
                }
            }
            // SETUP WITH LINKS TO DIFFERENT LABS WIKI PAGE
            try {
                Map<String, Object> rootMap = Maps.newHashMap();
                rootMap.put("infrastructure", infrastructure);
                String templatePath = TEMPLATE_ROOT_PATH + SETUP_TEMPLATE_NAME + ".fmt";
                rootMap.put("generator", "This page has been generaterd by '{{{" + getClass() + "}}}' with template '{{{" + templatePath + "}}}' on the "
                        + new DateTime());
                rootMap.put("generatedWikiPageNames", generatedForTeam);
                String page = FreemarkerUtils.generate(rootMap, templatePath);
                String wikiPageName = "ContinuousDeliveryWorkshopLab_" + infrastructure.getIdentifier() + "_" + SETUP_TEMPLATE_NAME;
                wikiPageName = wikiPageName.replace('-', '_');

                File wikiPageFile = new File(wikiBaseFolder, wikiPageName + ".wiki");
                Files.write(page, wikiPageFile, Charsets.UTF_8);

                generatedWikiPageNames.add(wikiPageName);
                setupGeneratedWikiPageNames.add(wikiPageName);

                logger.debug("Generated file {}", wikiPageFile);
            } catch (Exception e) {
                logger.error("Exception generating doc for {}", infrastructure, e);
            }
            teamsPages.put(infrastructure, generatedForTeam);
        }

        StringWriter indexPageStringWriter = new StringWriter();
        PrintWriter indexPageWriter = new PrintWriter(indexPageStringWriter);

        indexPageWriter.println("= Labs Per Team =");

        List<TeamInfrastructure> teams = new ArrayList<TeamInfrastructure>(teamsPages.keySet());

        Collections.sort(teams);

        for (TeamInfrastructure teamInfrastructure : teams) {
            indexPageWriter.println(" # [ContinuousDeliveryWorkshopLab_"
                    + teamInfrastructure.getIdentifier() + "_" + SETUP_TEMPLATE_NAME + "]");
            for (String wikiPage : teamsPages.get(teamInfrastructure)) {
                indexPageWriter.println("  * [" + wikiPage + "]");
            }
        }
        /*
         * for (String wikiPage : setupGeneratedWikiPageNames) { indexPageWriter.println(" # [" + wikiPage + "]"); }
         */
        String indexPageName = "ContinuousDeliveryWorkshopLab";
        Files.write(indexPageStringWriter.toString(), new File(baseWikiFolder, indexPageName + ".wiki"), Charsets.UTF_8);

        System.out.println("GENERATED WIKI PAGES TO BE COMMITTED IN XEBIA-FRANCE GOOGLE CODE");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Base folder: " + baseWikiFolder);
        System.out.println("All the files in " + baseWikiFolder + " must be committed in https://xebia-france.googlecode.com/svn/wiki");
        System.out.println("Index page: " + indexPageName);
        System.out.println("Per team pages: \n\t" + Joiner.on("\n\t").join(generatedWikiPageNames));
    }
}
