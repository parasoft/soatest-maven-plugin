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

import static java.lang.System.lineSeparator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Executes Parasoft SOAtest test suites with soatestcli.
 */
@Mojo(name = "soatest", defaultPhase = LifecyclePhase.INTEGRATION_TEST, threadSafe = true)
public class SOAtestMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Specifies the location of the Parasoft SOAtest installation.
     */
    @Parameter(property = "soatest.home", defaultValue = "${env.SOATEST_HOME}")
    private File soatestHome;

    /**
     * Specifies the location of the Eclipse workspace to use for executing the
     * tests. Defaults to a temporary directory.
     */
    @Parameter(property = "soatest.data")
    private File data;

    /**
     * Skip importing projects into the workspace.
     */
    @Parameter(property = "soatest.noimport", defaultValue = "false")
    private boolean noImport; // parasoft-suppress OPT.CTLV "injected"

    /**
     * The locations of Eclipse projects to import into the workspace prior to
     * executing tests. Defaults to ${project.basedir}. Example:
     * <pre><code>
     * {@literal <import>}
     *   {@literal <project>}${user.home}/projects/project1{@literal </project>}
     *   {@literal <project>}${user.home}/projects/project2{@literal </project>}
     * {@literal </import>}
     * </code></pre>
     */
    @Parameter(name = "import", property = "soatest.import")
    private List<File> toImport;

    /**
     * Specifies the test configuration to execute the tests. For example,
     * "soatest.builtin://Demo Configuration"
     */
    @Parameter(property = "soatest.config", required = true)
    private String config;

    public void setImport(List<File> toImport) {
        this.toImport = toImport;
    }

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        if (soatestHome == null) {
            throw new MojoExecutionException(Messages.get("soatest.home.not.set")); //$NON-NLS-1$
        }
        String osName = System.getProperty("os.name"); //$NON-NLS-1$
        boolean isWindows = osName != null && osName.startsWith("Windows"); //$NON-NLS-1$
        String soatestcli = new File(soatestHome, isWindows ? "soatestcli.exe" : "soatestcli").getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
        Path workspace;
        if (data != null) {
            workspace = data.toPath();
        } else {
            try {
                workspace = Files.createTempDirectory("soatest.workspace"); //$NON-NLS-1$
            } catch (IOException e) {
                throw new MojoExecutionException(e);
            }
        }
        try {
            runImport(log, soatestcli, workspace);
            runTestConfig(log, soatestcli, workspace);
        } finally {
            if (data == null) {
                try (Stream<Path> stream = Files.walk(workspace)) {
                    stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    log.debug(e);
                }
            }
        }
    }

    private void runImport(Log log, String soatestcli, Path workspace) throws MojoExecutionException {
        if (noImport) {
            log.debug("skipping import"); //$NON-NLS-1$
        } else {
            log.debug("importing projects"); //$NON-NLS-1$
            List<String> baseCommand = new LinkedList<>();
            baseCommand.add(soatestcli);
            baseCommand.add("-data"); //$NON-NLS-1$
            baseCommand.add(workspace.toAbsolutePath().toString());
            List<File> projectDirs = toImport != null && !toImport.isEmpty() ? toImport
                    : Collections.singletonList(project.getBasedir());
            for (File projectDir : projectDirs) {
                if (!projectDir.exists()) {
                    throw new MojoExecutionException(Messages.get("invalid.project.exists", projectDir)); //$NON-NLS-1$
                }
                if (!new File(projectDir, ".project").exists()) { //$NON-NLS-1$
                    throw new MojoExecutionException(Messages.get("invalid.project.dotproject", projectDir)); //$NON-NLS-1$
                }
                List<String> command = new ArrayList<>(baseCommand);
                command.add("-import"); //$NON-NLS-1$
                command.add(projectDir.getAbsolutePath());
                runCommand(log, command);
            }
        }
    }

    private void runTestConfig(Log log, String soatestcli, Path workspace) throws MojoExecutionException {
        List<String> command = new LinkedList<>();
        command.add(soatestcli);
        command.add("-data"); //$NON-NLS-1$
        command.add(workspace.toAbsolutePath().toString());
        command.add("-config"); //$NON-NLS-1$
        command.add(config);
        runCommand(log, command);
    }

    private static void runCommand(Log log, List<String> command) throws MojoExecutionException {
        if (log.isDebugEnabled()) {
            log.debug("command:" +  lineSeparator() + String.join(lineSeparator(), command)); //$NON-NLS-1$
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        try {
            Process process = pb.start();
            try (OutputStream out = process.getOutputStream();
                    InputStream in = process.getInputStream();
                    InputStream err = process.getErrorStream()) {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new MojoExecutionException(Messages.get("soatestcli.returned.exit.code", exitCode)); //$NON-NLS-1$
                }
            } catch (InterruptedException e) {
                process.destroy();
                throw new MojoExecutionException(e);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }
}
