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
package fr.xebia.workshop.continuousdelivery.git;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnmergedPathException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class AddTeamIdInPomGroupID implements GitRepositoryHandler {

    public static final String POM_XML = "pom.xml";
    public static final String COMMIT_MESSAGE = "update groupid with team id in pom.xml";

    private String teamId;

    public AddTeamIdInPomGroupID(String teamId) {
        this.teamId = teamId;
    }

    @Override
    public void updateGitRepository(Git git) throws GitAPIException {
        File pomFile = getPomFile(git);

        updatePomGroupId(pomFile);

        git.add().addFilepattern(POM_XML).call();
        try {
            git.commit().setMessage(COMMIT_MESSAGE).call();
        } catch (UnmergedPathException e) {
            throw new IllegalStateException("Cannot commit git repository", e);
        }
    }

    private File getPomFile(Git git) {
        return new File(git.getRepository().getWorkTree(), POM_XML);
    }

    private void updatePomGroupId(File pomFile) {
        FileReader reader = null;
        FileWriter writer = null;
        MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
        MavenXpp3Writer mavenXpp3Writer = new MavenXpp3Writer();

        try {
            Model model = null;

            reader = new FileReader(pomFile);
            model = mavenXpp3Reader.read(reader);
            model.setGroupId(model.getGroupId() + "." + teamId);
            writer = new FileWriter(pomFile);
            mavenXpp3Writer.write(writer, model);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot update pom.xml", e);
        } catch (XmlPullParserException e) {
            throw new IllegalStateException("Cannot update pom.xml", e);
        } finally {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(writer);
        }
    }
}
