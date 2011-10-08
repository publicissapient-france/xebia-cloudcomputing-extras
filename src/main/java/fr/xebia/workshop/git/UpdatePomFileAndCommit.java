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
package fr.xebia.workshop.git;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnmergedPathException;

public class UpdatePomFileAndCommit implements GitRepositoryHandler {

    private static final String POM_XML = "pom.xml";
    static final String COMMIT_MESSAGE = "Sets groupId to team ID";

    private String teamId;

    public UpdatePomFileAndCommit(String teamId) {
        this.teamId = teamId;
    }

    @Override
    public void updateGitRepository(Git git, GitRepositoryInfo repositoryInfo) throws GitAPIException {
        File pomFile = getPomFile(git);

        updatePomGroupId(pomFile, repositoryInfo);

        git.add().addFilepattern(POM_XML).call();
        try {
            git.commit()
                    .setCommitter("Team " + teamId, "")
                    .setMessage(COMMIT_MESSAGE)
                    .call();
        } catch (UnmergedPathException e) {
            throw new IllegalStateException("Cannot commit git repository", e);
        }
    }

    private File getPomFile(Git git) {
        return new File(git.getRepository().getWorkTree(), POM_XML);
    }

    private void updatePomGroupId(File pomFile, GitRepositoryInfo repositoryInfo) {
        FileReader reader = null;
        FileWriter writer = null;
        MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
        MavenXpp3Writer mavenXpp3Writer = new MavenXpp3Writer();

        try {
            Model model = null;

            reader = new FileReader(pomFile);
            model = mavenXpp3Reader.read(reader);
            updateMavenModel(model, repositoryInfo);
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

    private void updateMavenModel(Model model, GitRepositoryInfo repositoryInfo) {
        model.setGroupId(model.getGroupId() + "-" + teamId);
        String scmConnectionUrl = getScmConnectionUrl(repositoryInfo);
        Scm scm = new Scm();
        scm.setConnection(scmConnectionUrl);
        scm.setDeveloperConnection(scmConnectionUrl);
        model.setScm(scm);
    }

    private String getScmConnectionUrl(GitRepositoryInfo repositoryInfo) {
        return new StringBuilder()
                .append("scm:git:git://github.com/")
                .append(repositoryInfo.getAccountName())
                .append("/")
                .append(repositoryInfo.getRepositoryName()).toString();
    }
}
