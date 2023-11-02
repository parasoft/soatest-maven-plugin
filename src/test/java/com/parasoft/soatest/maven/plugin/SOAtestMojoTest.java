/*
 * Copyright 2023 Parasoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.parasoft.soatest.maven.plugin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedConstruction;

public class SOAtestMojoTest {
    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() throws Throwable {
        }

        @Override
        protected void after() {
        }
    };

    @Test
    public void testExecute() throws Exception {
        File pom = new File("target/test-classes/project-to-test/");
        assertNotNull(pom);
        assertTrue(pom.exists());

        SOAtestMojo soatestMojo = (SOAtestMojo) rule.lookupConfiguredMojo(pom, "soatest");
        assertNotNull(soatestMojo);
        Process process = mock(Process.class);
        List<String> importCommand;
        List<String> testConfigCommand;
        try (MockedConstruction<ProcessBuilder> processBuilder = mockConstruction(ProcessBuilder.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS), (mock, context) -> {
                    doReturn(process).when(mock).start();
                    doReturn(context.arguments().get(0)).when(mock).command();
                })) {
            soatestMojo.execute();
            List<ProcessBuilder> constructed = processBuilder.constructed();
            assertEquals(2, constructed.size());
            importCommand = constructed.get(0).command();
            testConfigCommand = constructed.get(1).command();
        }
        assertEquals(7, importCommand.size());
        checkBaseCommand(pom, importCommand);
        assertEquals("-import", importCommand.get(5));
        assertEquals(pom.getAbsolutePath(), importCommand.get(6));

        assertEquals(28, testConfigCommand.size());
        checkBaseCommand(pom, testConfigCommand);
        assertThat(testConfigCommand.subList(5, testConfigCommand.size()), contains(
                "-config", "soatest.builtin://Demo Configuration",
                "-publish",
                "-dataGroupConfig", new File(pom, "dataconfig.xml").getAbsolutePath(),
                "-dataSourceRow", "1",
                "-dataSourceName", "data source name",
                "-fail",
                "-environment", "test environment",
                "-environmentConfig", new File(pom, "environments.xml").getAbsolutePath(),
                "-showsettings",
                "-prefs", "prefs.properties",
                "-report", new File(pom, "myreport.xml").getAbsolutePath(),
                "-resource", "testProject",
                "-property", "techsupport.auto_creation=true"
                ));
    }

    private static void checkBaseCommand(File baseDir, List<String> command) {
        assertThat(command.size(), greaterThan(5));
        List<String> baseCommand = command.subList(0, 5);
        assertThat(baseCommand.get(0), endsWith("parasoft/soatest/soatestcli" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "")));
        assertEquals("-data", baseCommand.get(1));
        assertThat(baseCommand.get(2), startsWith(new File(System.getProperty("java.io.tmpdir"), "soatest.workspace").getAbsolutePath()));
        assertEquals("-settings", baseCommand.get(3));
        assertEquals(new File(baseDir, "settings.properties").getAbsolutePath(), baseCommand.get(4));
    }

    /** Do not need the MojoRule. */
    @WithoutMojo
    @Test
    public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() {
        assertTrue(true);
    }

}
