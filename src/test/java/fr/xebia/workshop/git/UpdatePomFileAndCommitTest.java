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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.class)
public class UpdatePomFileAndCommitTest {

    private static final Logger logger = LoggerFactory.getLogger(UpdatePomFileAndCommitTest.class);

    @Mock
    private Repository repository;
    @Mock
    private Git git;
    @Mock
    private AddCommand addCommand;
    @Mock
    private CommitCommand commitCommand;

    private File pomFile = new File("target/test-classes/git/project/pom.xml");
    private String pomContent;

    @Before
    public void mockGitBehavior() {
        when(git.getRepository()).thenReturn(repository);
        when(git.add()).thenReturn(addCommand);
        when(git.commit()).thenReturn(commitCommand);

        when(repository.getWorkTree()).thenReturn(new File("target/test-classes/git/project/"));

        when(addCommand.addFilepattern(anyString())).thenReturn(addCommand);

        when(commitCommand.setMessage(anyString())).thenReturn(commitCommand);
        when(commitCommand.setCommitter(anyString(), anyString())).thenReturn(commitCommand);
    }

    // prevents tests failures when the workspace is not cleaned between two tests
    @Before
    public void storeTargetPomContent() throws Exception {
        pomContent = FileUtils.readFileToString(pomFile);
    }

    @After
    public void restoreTargetPomContent() throws Exception {
        FileUtils.writeStringToFile(pomFile, pomContent);
    }

    @Test
    public void should_replace_group_id_in_pom_xml() throws Exception {
        // given
        UpdatePomFileAndCommit addTeamIdInPomGroupID = new UpdatePomFileAndCommit("team.test");

        GithubCreateRepositoryRequest createRepositoryRequest = new GithubCreateRepositoryRequest()
                .toRepositoryName("xebia-petclinic-team-test")
                .onAccountName("account");

        // when
        addTeamIdInPomGroupID.updateGitRepository(git, createRepositoryRequest);

        // then
        FileInputStream updatedPom = new FileInputStream(pomFile);
        InputStream referencePom = getClass().getResourceAsStream("/git/project/reference/pom.xml");
        Diff diff = makeDiff(referencePom, updatedPom);

        logger.info(diff.toString());
        assertEquals(true, diff.identical());
    }

    private Diff makeDiff(InputStream stream1, InputStream stream2) throws SAXException, IOException {
        XMLUnit.setIgnoreWhitespace(true);
        return XMLUnit.compareXML(new InputSource(stream1), new InputSource(stream2));
    }

    @Test
    public void should_add_and_commit_pom_xml() throws Exception {
        // given
        UpdatePomFileAndCommit addTeamIdInPomGroupID = new UpdatePomFileAndCommit("team.test");

        // when
        addTeamIdInPomGroupID.updateGitRepository(git, new GithubCreateRepositoryRequest());

        // then
        verify(addCommand, times(1)).addFilepattern("pom.xml");
        verify(addCommand, times(1)).call();
        verify(commitCommand).setMessage(UpdatePomFileAndCommit.COMMIT_MESSAGE);
        verify(commitCommand, times(1)).call();
    }

    @Test
    public void should_not_use_system_git_account() throws Exception {
        // given
        UpdatePomFileAndCommit addTeamIdInPomGroupID = new UpdatePomFileAndCommit("tEsT");

        // when
        addTeamIdInPomGroupID.updateGitRepository(git, new GithubCreateRepositoryRequest());
        verify(commitCommand).setCommitter("Team tEsT", "");
    }
}
