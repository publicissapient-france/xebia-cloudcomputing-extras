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

import junit.framework.Assert;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;


public class AddTeamIdInPomGroupIDTest {

    private static final Logger logger = LoggerFactory.getLogger(GithubRepositoriesJobCreator.class);

    @Test
    public void should_replace_group_id_in_pom_xml() throws IOException, GitAPIException, SAXException {
        //given
        AddTeamIdInPomGroupID addTeamIdInPomGroupID = new AddTeamIdInPomGroupID("team.test");

        Repository repository = Mockito.mock(Repository.class);
        Git git = Mockito.mock(Git.class);
        AddCommand addCommand = Mockito.mock(AddCommand.class);
        CommitCommand commitCommand = mock(CommitCommand.class);

        when(git.getRepository()).thenReturn(repository);
        when(repository.getWorkTree()).thenReturn(new File("target/test-classes/git/project/"));
        when(git.add()).thenReturn(addCommand);
        when(addCommand.addFilepattern(anyString())).thenReturn(addCommand);
        when(git.commit()).thenReturn(commitCommand);
        when(commitCommand.setMessage(anyString())).thenReturn(commitCommand);


        //when
        addTeamIdInPomGroupID.updateGitRepository(git);

        //then
        FileInputStream updatedPom = new FileInputStream("target/test-classes/git/project/pom.xml");
        InputStream referencePom = getClass().getResourceAsStream("/git/project/reference/pom.xml");
        XMLUnit.setIgnoreWhitespace(true);
        Diff diff = XMLUnit.compareXML(new InputSource(referencePom), new InputSource(updatedPom));

        logger.info(diff.toString());
        Assert.assertEquals(true, diff.identical());
    }

    @Test
    public void should_add_and_commit_pom_xml() throws IOException, GitAPIException, SAXException {
        //given
        AddTeamIdInPomGroupID addTeamIdInPomGroupID = new AddTeamIdInPomGroupID("team.test");

        Repository repository = Mockito.mock(Repository.class);
        Git git = Mockito.mock(Git.class);
        AddCommand addCommand = Mockito.mock(AddCommand.class);
        CommitCommand commitCommand = mock(CommitCommand.class);

        when(git.getRepository()).thenReturn(repository);
        when(repository.getWorkTree()).thenReturn(new File("target/test-classes/git/project/"));
        when(git.add()).thenReturn(addCommand);
        when(addCommand.addFilepattern(anyString())).thenReturn(addCommand);
        when(git.commit()).thenReturn(commitCommand);
        when(commitCommand.setMessage(anyString())).thenReturn(commitCommand);


        //when
        addTeamIdInPomGroupID.updateGitRepository(git);

        //then
        verify(addCommand, times(1)).addFilepattern(eq("pom.xml"));
        verify(addCommand, times(1)).call();
        verify(commitCommand, times(1)).setMessage(eq("update groupid with team id in pom.xml"));
        verify(commitCommand, times(1)).call();
    }

}
