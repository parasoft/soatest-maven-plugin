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
     * Skips running SOAtest.
     */
    @Parameter(property = "soatest.skip", defaultValue = "false")
    private boolean skip; // parasoft-suppress OPT.CTLV "injected"

    /**
     * Specifies the location of the Parasoft SOAtest installation.
     */
    @Parameter(property = "soatest.home", defaultValue = "${env.SOATEST_HOME}")
    private File soatestHome;

    /**
     * Specifies an alternative Java runtime for starting SOAtest.
     */
    @Parameter(property = "soatest.javahome")
    private File javaHome;

    /**
     * Specifies additional JVM options. Example:
     *
     * <pre><code>{@literal <vmArgs>}
     *   {@literal <vmArg>}-Xmx8g{@literal </vmArg>}
     *   {@literal <vmArg>}-Dssl.debug=true{@literal </vmArg>}
     * {@literal </vmArgs>}</code></pre>
     */
    @Parameter(property = "soatest.vmargs")
    private List<String> vmArgs;

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
     *
     * <pre><code>{@literal <import>}
     *   {@literal <project>}${user.home}/projects/project1{@literal </project>}
     *   {@literal <project>}${user.home}/projects/project2{@literal </project>}
     * {@literal </import>}</code></pre>
     */
    @Parameter(name = "import", property = "soatest.import")
    private List<File> toImport;

    /**
     * Specifies the test configuration to execute the tests. For example,
     * "soatest.builtin://Demo Configuration"
     */
    @Parameter(property = "soatest.config", required = true)
    private String config;

    /**
     * Specifies where to print the text that would be shown in the Console view in
     * the UI. Use {@code stdout} to print to STDOUT. Otherwise, the value is
     * interpreted as a file path.
     */
    @Parameter(property = "soatest.appconsole")
    private String appconsole;

    /**
     * Disables building of projects before executing the tests.
     */
    @Parameter(property = "soatest.nobuild", defaultValue = "false")
    private boolean nobuild; // parasoft-suppress OPT.CTLV "injected"

    /**
     * Refreshes the workspace, forcing it to resync with the file system.
     */
    @Parameter(property = "soatest.refresh", defaultValue = "false")
    private boolean refresh; // parasoft-suppress OPT.CTLV "injected"

    /**
     * Prints detailed test progress information to the console.
     */
    @Parameter(property = "soatest.showdetails", defaultValue = "false")
    private boolean showdetails; // parasoft-suppress OPT.CTLV "injected"

    /**
     * Specifies the active data source within a data group. This parameter must
     * be set to the location of an XML file that specifies the active data
     * source for each data group within each .tst file contained in the test
     * run.
     */
    @Parameter(property = "soatest.datagroupconfig")
    private String dataGroupConfig;

    /**
     * Specifies the name of the data source associated with the test(s) you
     * want to run. See dataSourceRow for additional information.
     */
    @Parameter(property = "soatest.datasourcename")
    private String dataSourceName;

    /**
     * Runs all tests with the specified data source rows(s). You can specify a
     * list of row numbers or row ranges. The following values are examples of
     * valid values:
     *
     * <ul>
     * <li>5</li>
     * <li>1,2,5</li>
     * <li>3-9</li>
     * <li>2-5,7,20-30</li>
     * </ul>
     *
     * You can also specify {@code all} as the value to force all data source
     * rows to be used, even if the data sources were saved to use only specific
     * rows.
     *
     * You can use the dataSourceName option to specify which data source
     * contains the row associated with the tests you want to execute.
     */
    @Parameter(property = "soatest.datasourcerow")
    private String dataSourceRow;

    /**
     * An absolute or relative path to the .properties file that includes
     * custom configuration settings.
     */
    @Parameter(property = "soatest.settings")
    private File settings;

    public void setImport(List<File> toImport) {
        this.toImport = toImport;
    }

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        if (skip) {
            log.info(Messages.get("soatest.skip")); //$NON-NLS-1$
            return;
        }
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
        List<String> baseCommand = getBaseCommand(log, soatestcli, workspace);
        try {
            runImport(log, baseCommand);
            runTestConfig(log, baseCommand);
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

    private List<String> getBaseCommand(Log log, String soatestcli, Path workspace) {
        List<String> baseCommand = new LinkedList<>();
        baseCommand.add(soatestcli);
        if (javaHome != null) {
            baseCommand.add("-Zjava_home"); //$NON-NLS-1$
            baseCommand.add(javaHome.getAbsolutePath());
        }
        if (vmArgs != null) {
            for (String vmArg : vmArgs) {
                baseCommand.add("-J" + vmArg); //$NON-NLS-1$
            }
        }
        baseCommand.add("-data"); //$NON-NLS-1$
        baseCommand.add(workspace.toAbsolutePath().toString());
        addOptionalCommand("-settings", settings, baseCommand); //$NON-NLS-1$
        return Collections.unmodifiableList(baseCommand);
    }

    private void runImport(Log log, List<String> baseCommand) throws MojoExecutionException {
        if (noImport) {
            log.debug("skipping import"); //$NON-NLS-1$
        } else {
            log.debug("importing projects"); //$NON-NLS-1$
            List<File> projectLocs = toImport != null && !toImport.isEmpty() ? toImport
                    : Collections.singletonList(project.getBasedir());
            for (File projectLoc : projectLocs) {
                if (!projectLoc.exists()) {
                    throw new MojoExecutionException(Messages.get("invalid.project.exists", projectLoc)); //$NON-NLS-1$
                }
                if (projectLoc.isFile()) {
                    if (!".project".equals(projectLoc.getName())) { //$NON-NLS-1$
                        throw new MojoExecutionException(Messages.get("invalid.project.file", projectLoc)); //$NON-NLS-1$
                    }
                } else {
                    try (Stream<Path> walkStream = Files.walk(projectLoc.toPath())) {
                        if (!walkStream.filter(Files::isRegularFile)
                                .filter(p -> ".project".equals(p.getFileName().toString())) //$NON-NLS-1$
                                .findFirst().isPresent()) {
                            throw new MojoExecutionException(Messages.get("invalid.project.dotproject", projectLoc)); //$NON-NLS-1$
                        }
                    } catch (IOException e) {
                        log.warn(e);
                    }
                }
                List<String> command = new ArrayList<>(baseCommand);
                command.add("-import"); //$NON-NLS-1$
                command.add(projectLoc.getAbsolutePath());
                runCommand(log, command);
            }
        }
    }

    private void runTestConfig(Log log, List<String> baseCommand) throws MojoExecutionException {
        List<String> command = new LinkedList<>(baseCommand);
        command.add("-config"); //$NON-NLS-1$
        command.add(config);
        addOptionalCommand("-appconsole", appconsole, command); //$NON-NLS-1$
        addOptionalCommand("-nobuild", nobuild, command); //$NON-NLS-1$
        addOptionalCommand("-refresh", refresh, command); //$NON-NLS-1$
        addOptionalCommand("-showdetails", showdetails, command); //$NON-NLS-1$
        addOptionalCommand("-dataGroupConfig", dataGroupConfig, command); //$NON-NLS-1$
        addOptionalCommand("-dataSourceRow", dataSourceRow, command); //$NON-NLS-1$
        addOptionalCommand("-dataSourceName", dataSourceName, command); //$NON-NLS-1$
        runCommand(log, command);
    }

    private static void addOptionalCommand(String name, boolean value, List<String> command) {
        if (value) {
            command.add(name);
        }
    }

    private static void addOptionalCommand(String name, File value, List<String> command) {
        if (value != null) {
            command.add(name);
            command.add(value.getAbsolutePath());
        }
    }

    private static void addOptionalCommand(String name, String value, List<String> command) {
        if (value != null && !value.trim().isEmpty()) {
            command.add(name);
            command.add(value);
        }
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
