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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.xml.PrettyPrintXMLWriter;
import org.apache.maven.shared.utils.xml.XMLWriter;

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
     * The locations of projects to import into the workspace prior to
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
     * Specifies where to print the text that would be shown in the Console view
     * in the UI. Use {@code stdout} to print to STDOUT. Otherwise, the value is
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
     * <p>
     * Specifies the active data source within a data group. This parameter must
     * be set to the location of an XML file that specifies the active data
     * source for each data group within each <em>.tst</em> file contained in
     * the test run. The XML file must use the following format:
     * </p>
     * <pre><code>{@literal <tests>
     *  <test> <!--1 or more-->
     *     <workspacePath></workspacePath>
     *     <dataGroups>
     *       <dataGroup> <!--1 or more-->
     *         <dataGroupName></dataGroupName>
     *         <activeDataSourceName></activeDataSourceName>
     *       </dataGroup>
     *     </dataGroups>
     *   </test>
     * </tests>}</code></pre>
     * <p>
     * The {@code <workspacePath>} element for {@code datagroupConfig} and
     * {@code environmentConfig} should contain the path to the resource (for
     * example, .tst) in the workspace, not the path on the file system. You can
     * right-click the .tst file in the SOAtest UI and choose Properties to get
     * the correct path to the resource.
     * </p>
     */
    @Parameter(property = "soatest.datagroupconfig")
    private File dataGroupConfig;

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
     * You can use the dataSourceName parameter to specify which data source
     * contains the row associated with the tests you want to execute.
     */
    @Parameter(property = "soatest.datasourcerow")
    private String dataSourceRow;

    /**
     * <p>
     * Specifies the name of the SOAtest environment to use for executing the
     * tests. An environment in SOAtest is a set of variables that define
     * endpoints and other test-specific inputs. SOAtest environments should not
     * be confused with environment entities as described in Virtualize and
     * Continuous Testing Platform.
     * </p>
     * <p>
     * When running functional tests from the command line, you can override the
     * active environment specified in a project with one specified from the
     * parameters. Note that if the specified environment is not found in the
     * project, the default active environment will be used instead.
     * </p>
     */
    @Parameter(property = "soatest.environment")
    private String environment;

    /**
     * <p>
     * Specifies the active environment variables. This parameter must be set to
     * the location of an XML file that specifies the environment variable
     * values to use for each .tst file contained in the test run. The XML file
     * must use the following format:
     * </p>
     * <pre><code>{@literal <tests>
     *   <test> <!--1 or more-->
     *     <workspacePath></workspacePath>
     *     <Environment>
     *       <Variable> <!--1 or more-->
     *         <Name></Name>
     *         <Value></Value>
     *       </Variable>
     *     </Environment>
     *   </test>
     * </tests>}</code></pre>
     * <p>
     * The {@code <workspacePath>} element for {@code datagroupConfig} and
     * {@code environmentConfig} should contain the path to the resource (for
     * example, .tst) in the workspace, not the path on the file system.
     * </p>
     * <p>
     * You can right-click the .tst file in the SOAtest UI and choose Properties
     * to get the correct path to the resource.
     * </p>
     */
    @Parameter(property = "soatest.environmentconfig")
    private File environmentConfig;

    /**
     * Fails the build by returning a non-zero exit code if any violations are
     * reported.
     */
    @Parameter(property = "soatest.fail", defaultValue = "false")
    private boolean fail; // parasoft-suppress OPT.CTLV "injected"

    /**
     * Prints the current settings and customizations along with information
     * regarding where they are configured (for example, in the
     * settings.properties file).
     */
    @Parameter(property = "soatest.showsettings", defaultValue = "false")
    private boolean showsettings; // parasoft-suppress OPT.CTLV "injected"

    /**
     * <p>
     * Specifies an Eclipse workspace preferences file to import. The specified
     * value is interpreted as a URL or the path to a local Eclipse workspace
     * preferences file. The best way to create a workspace preferences file is
     * to use the Export wizard. To do this:
     * </p>
     *
     * <ol>
     * <li>Go to <strong>File > Export.</strong></li>
     * <li>In the Export Wizard, choose <strong>Preferences</strong> and click
     * <strong>Next.</strong></li>
     * <li>Do one of the following:
     * <ul>
     * <li>To add all of the preferences to the file, choose <strong>Export
     * all.</strong></li>
     * <li>To add only specified preferences to the file, choose <strong>Choose
     * specific preferences to export</strong> and enable the preferences you
     * want to import.</li>
     * </ul>
     * <li>Click <strong>Browse...</strong> and indicate where you want the
     * preferences file saved.</li>
     * <li>Click <strong>Finish</strong>.</li>
     * </ol>
     *
     * <p>
     * We recommend deleting properties that are not applicable to SOAtest and
     * keeping only critical properties, such as the {@code classpath} property.
     * We also recommend that you replace machine/user-specific locations with
     * variables by using the $(VAR) notation. These variables will be replaced
     * with the corresponding Java properties, which can be set at runtime by
     * running {@code soatestcli} with -J-D options (for example
     * {@code soatestcli -J-DHOME=/home/user}).
     * </p>
     *
     * Examples:
     * <ul>
     * <li>{@code -prefs "http://intranet.acme.com/SOAtest/workspace.properties"}</li>
     * <li>{@code -prefs "workspace.properties"}</li>
     * </ul>
     */
    @Parameter(property = "soatest.prefs")
    private String prefs;

    /**
     * Enables publishing reports to DTP Report Center. DTP 5.3.x or later is
     * required.
     *
     * The connection to DTP is configured in the settings file.
     */
    @Parameter(property = "soatest.publish", defaultValue = "false")
    private boolean publish; // parasoft-suppress OPT.CTLV "injected"

    /**
     * <p>
     * Generates a report and saves it with the name and path specified. The
     * report includes an XML file containing the report data, as well as HTML
     * file for presenting the data. You can also configure SOAtest to generate
     * a report in PDF or custom formats by specifying them in the report.format
     * option. The option is specified in the settings file (see
     * {@code settings}). If a path is not included as part the specified value,
     * the report will be generated in the execution directory.
     * </p>
     * <p>
     * All of the following parameters will produce an HTML report
     * <em>filename.html</em> and an XML report <em>filename.xml</em>.
     * </p>
     * <ul>
     * <li>{@code <report>filename.xml</report>}</li>
     * <li>{@code <report>filename.htm</report>}</li>
     * <li>{@code <report>filename.html</report>}</li>
     * </ul>
     * <p>
     * If the specified path ends with an ".html"/".htm"/".xml" extension, it
     * will be treated as a path to the report file to generate. Otherwise, it
     * will be treated as a path to a directory where reports should be
     * generated.
     * </p>
     * <p>
     * If the file name is explicitly specified in the parameter and a file with
     * this name already exists in the specified location, the previous report
     * will be overwritten. If your parameter doesn’t explicitly specify a file
     * name, the existing report file will not be overwritten; the new file will
     * be named repXXXX.html, where XXXX is a random number.
     * </p>
     * <p>
     * If the {@code report} parameter is not specified, reports will be
     * generated with the default names "report.xml"/"html" in the current
     * directory.
     * </p>
     */
    @Parameter(property = "soatest.report")
    private File report;

    /**
     * Specifies the path to the .properties file that includes custom configuration
     * settings.
     */
    @Parameter(property = "soatest.settings")
    private File settings;

    /**
     * Filenames, paths to files, or patterns that matches filenames to be
     * included during testing. The pattern matching syntax is similar to that
     * of Ant file sets. You can also specify a list of patterns by adding them
     * to a .lst file. Example:
     *
     * <pre><code>{@literal <includes>}
     *   {@literal <include>}tests1/*.tst{@literal </include>}
     *   {@literal <include>**}/Test2.tst{@literal </include>}
     * {@literal </includes>}</code></pre>
     */
    @Parameter(property = "soatest.includes")
    private List<String> includes;

    /**
     * Filenames, paths to files, or patterns that matches filenames to be
     * excluded during testing. The pattern matching syntax is similar to that
     * of Ant file sets. You can also specify a list of patterns by adding them
     * to a .lst file. Example:
     *
     * <pre><code>{@literal <excludes>}
     *   {@literal <exclude>}tests1/*.tst{@literal </exclude>}
     *   {@literal <exclude>**}/Test2.tst{@literal </exclude>}
     * {@literal </excludes>}</code></pre>
     */
    @Parameter(property = "soatest.excludes")
    private List<String> excludes;

    /**
     * Allows you to configure settings directly. Settings passed with this
     * parameter will overwrite those with the same key that are specified using the
     * {@code settings} parameter. Example:
     *
     * <pre><code>{@literal <properties>}
     *   {@literal <report.dtp.publish>}true{@literal </report.dtp.publish>}
     *   {@literal <techsupport.auto_creation>}true{@literal </techsupport.auto_creation>}
     * {@literal </properties>}</code></pre>
     */
    @Parameter
    private Map<String, String> properties;

    /**
     * <p>
     * Specifies the path to the test suite(s) to run. To run a single test
     * suite, specify the path to the <em>.tst</em> relative to the workspace.
     * To run all test suites within a directory, specify the directory path
     * relative to the workspace. Use multiple {@code resource} parameters to
     * specify multiple resources. All paths, including absolute paths, are
     * relative to the workspace specified by the {@code soatest.data}
     * parameter.
     * </p>
     * <p>
     * You can specify the following types of resources: <br/>
     * <p/>
     * <table>
     * <tr>
     * <th style="text-align:left"><em>.tst</em> file</th>
     * <td>Specify the path to a <em>.tst</em> file to execute all tests
     * contained</td>
     * </tr>
     * <tr>
     * <th style="text-align:left">directory</th>
     * <td>Specify the path to a directory in the workspace to execute all test
     * suites in the directory.</td>
     * </tr>
     * <tr>
     * <th style="text-align:left"><em>.properties</em> file</th>
     * <td>Specify the path to a <em>.properties</em> file and the value
     * configured for the {@code com.parasoft.xtest.checkers.resources} property
     * in the file will be interpreted as a colon-separated list of resources.
     * Only one <em>.properties</em> file can be specified in this way.</td>
     * </tr>
     * <tr>
     * <th style="text-align:left"><em>.lst</em> file</th>
     * <td>Specify the path to a <em>.lst</em> file and each line in the file
     * will be treated as a resource. If no resources are specified on the
     * command line, the complete workspace will be tested.</td>
     * </tr>
     * <tr>
     * <th style="text-align:left">Team Project Set File (PSF)</th>
     * <td>PSFs are supported for SVN and other source systems, depending on the
     * Eclipse plug-in capabilities installed.</td>
     * </tr>
     * </table>
     * <p>
     * Examples:
     * </p>
     *
     * <pre><code>{@literal <resources>}
     *   {@literal <resource>}Acme Project{@literal </resource>}
     *   {@literal <resource>}/MyProject/tests/acme{@literal </resource>}
     *   {@literal <resource>}testedprojects.properties{@literal </resource>}
     * {@literal </resources>}</code></pre>
     * <p>
     * If you are specifying multiple tests from different projects, tests will
     * be grouped project-by-project, in the order specified in the multiple
     * {@code resource} parameters or in the <em>.lst</em> file.
     * </p>
     * <p>
     * All tests in the same project as the first resource will be run before
     * any tests in a different project. If you specify resources in the order
     * the following order, for example: <br/>
     * <p/>
     * <p>
     * {@code /ProjectA/A.tst, /ProjectB/B.tst, /ProjectA/C.tst} <br/>
     * </p>
     * <p>
     * they will be executed in the following order:
     * </p>
     * <ol>
     * <li>{@code /ProjectA/A.tst}</li>
     * <li>{@code /ProjectA/C.tst}</li>
     * <li>{@code /ProjectB/B.tst}</li>
     * </ol>
     */
    @Parameter(property = "soatest.resources")
    private List<String> resources;

    /**
     * Limit the test scope to the resources that are associated with specific
     * work items. Specify a list of work item IDs.  Example:
     *
     * <pre><code>{@literal <workItems>
     *   <workItem>TEST-7140</workItem>
     *   <workItem>TEST-16447</workItem>
     * </workItems>}</code></pre>
     */
    @Parameter(property = "soatest.workitems")
    private List<String> workItems;

    /**
     * Specifies a .tst file name, path or pattern to be executed during testing.
     * This pattern is added to the includes list, which is useful for debugging tests.
     */
    @Parameter(property = "soatest.test")
    private String test;

    /**
     * Runs tests impacted by change. Parameter value specifies a path to the
     * baseline coverage report.
     */
    @Parameter(property = "soatest.impactedtests")
    private File impactedTests;

    /**
     * <p>
     * Specifies a {@code testName} parameter that matches the name of a test to
     * run. SOAtest searches for tests in the resource that contain the string
     * specified, but programmatic pattern matching, such as wildcards or regex,
     * is not performed.
     * </p>
     * <p>
     * In the following example, a test or test suite named WSDL Tests will run:
     * </p>
     *
     * <pre><code>{@literal <testFilters>}
     *   {@literal <testFilter>}
     *      {@literal <testName>}WSDL Tests{@literal </testName>}
     *   {@literal </testFilter>}
     * {@literal </testFilters>}</code></pre>
     * <p>
     * You can use more than one {@code testFilter} parameter to specify multiple
     * tests: Use the {@code substringMatch} optional parameter to search for
     * tests containing a specific string. In the following example, any tests
     * that contain the string MyTest will run:
     * </p>
     *
     * <pre><code>{@literal <testFilters>}
     *   {@literal <testFilter>}
     *      {@literal <testName>}MyTest{@literal </testName>}
     *      {@literal <substringMatch>}true{@literal </substringMatch>}
     *   {@literal </testFilter>}
     *   {@literal <testFilter>}
     *      {@literal ...}
     *   {@literal </testFilter>}
     * {@literal </testFilters>}</code></pre>
     * <p>
     * If the test uses data from a data source, you can use the
     * {@code dataSourceRow} and {@code dataSourceName} optional parameter to
     * limit the range of data rows used to execute the test. for example:
     * </p>
     *
     * <pre><code>{@literal <testFilters>}
     *   {@literal <testFilter>}
     *      {@literal <testName>}MyTest{@literal </testName>}
     *      {@literal <substringMatch>}true{@literal </substringMatch>}
     *      {@literal <dataSourceName>}MyData{@literal </dataSourceName>}
     *      {@literal <dataSourceRow>}1{@literal </dataSourceRow>}
     *   {@literal </testFilter>}
     * {@literal </testFilters>}</code></pre>
     * <p>
     * The value for the {@code dataSourceRow} parameter can be specified as a
     * single row. The following values are examples of valid values:
     * </p>
     * <ul>
     * <li>{@code 5}</li>
     * <li>{@code 1,2,5}</li>
     * <li>{@code 3-9}</li>
     * <li>{@code 2-5,7,20-30}</li>
     * </ul>
     */
    @Parameter
    private List<TestFilter> testFilters;

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
        List<Path> tempDotProjects = new LinkedList<>();
        List<String> baseCommand = getBaseCommand(log, soatestcli, workspace);
        try {
            renameExistingReports();
            runImport(log, baseCommand, tempDotProjects);
            runTestConfig(log, baseCommand);
            parseXmlReport(log);
        } finally {
            if (data == null) {
                try (Stream<Path> stream = Files.walk(workspace)) {
                    stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    log.debug(e);
                }
            }
            tempDotProjects.stream().map(Path::toFile).forEach(File::delete);
        }
    }

    private List<String> getBaseCommand(Log log, String soatestcli, Path workspace) {
        List<String> baseCommand = new LinkedList<>();
        baseCommand.add(soatestcli);
        addOptionalCommand("-Zjava_home", javaHome, baseCommand); //$NON-NLS-1$
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

    private void runImport(Log log, List<String> baseCommand, List<Path> tempDotProjects) throws MojoExecutionException {
        if (noImport) {
            log.debug("skipping import"); //$NON-NLS-1$
        } else {
            log.debug("importing projects"); //$NON-NLS-1$
            List<File> projectLocs = toImport != null && !toImport.isEmpty() ? toImport :
                    Collections.singletonList(project.getBasedir());
            for (File projectLoc : projectLocs) {
                if (!projectLoc.exists()) {
                    throw new MojoExecutionException(Messages.get("invalid.project.exists", projectLoc)); //$NON-NLS-1$
                }
                if (projectLoc.isFile()) {
                    if (!".project".equals(projectLoc.getName())) { //$NON-NLS-1$
                        throw new MojoExecutionException(Messages.get("invalid.project.file", projectLoc)); //$NON-NLS-1$
                    }
                } else {
                    Path projectPath = projectLoc.toPath();
                    Path dotProject = projectPath.resolve(".project"); //$NON-NLS-1$
                    boolean foundDotProject = Files.isRegularFile(dotProject);
                    if (!foundDotProject) {
                        try (Stream<Path> walkStream = Files.walk(projectPath)) {
                            foundDotProject = walkStream.filter(Files::isRegularFile)
                                    .filter(p -> ".project".equals(p.getFileName().toString())) //$NON-NLS-1$
                                    .findFirst().isPresent();
                        } catch (IOException e) {
                            log.warn(e);
                        }
                    }
                    if (!foundDotProject) {
                        try {
                            log.debug("writing " + dotProject); //$NON-NLS-1$
                            tempDotProjects.add(dotProject);
                            writeDotProject(dotProject);
                        } catch (IOException e) {
                            throw new MojoExecutionException(Messages.get("invalid.project.dotproject", projectLoc, dotProject), e); //$NON-NLS-1$
                        }
                    }
                }
                List<String> command = new ArrayList<>(baseCommand);
                command.add("-import"); //$NON-NLS-1$
                command.add(projectLoc.getAbsolutePath());
                runCommand(log, command);
            }
        }
    }

    private static void writeDotProject(Path dotProject) throws IOException {
        try (Writer writer = Files.newBufferedWriter(dotProject)) {
            XMLWriter xmlWriter = new PrettyPrintXMLWriter(writer, StandardCharsets.UTF_8.name(), null);
            xmlWriter.startElement("projectDescription"); //$NON-NLS-1$
            xmlWriter.startElement("name"); //$NON-NLS-1$
            xmlWriter.writeText(dotProject.getParent().getFileName().toString());
            xmlWriter.endElement();
            xmlWriter.startElement("comment"); //$NON-NLS-1$
            xmlWriter.endElement();
            xmlWriter.startElement("projects"); //$NON-NLS-1$
            xmlWriter.endElement();
            xmlWriter.startElement("buildSpec"); //$NON-NLS-1$
            xmlWriter.endElement();
            xmlWriter.startElement("natures"); //$NON-NLS-1$
            xmlWriter.endElement();
            xmlWriter.endElement();
        }
    }

    private void runTestConfig(Log log, List<String> baseCommand) throws MojoExecutionException {
        List<String> command = new LinkedList<>(baseCommand);
        command.add("-config"); //$NON-NLS-1$
        command.add(config);
        addOptionalCommand("-appconsole", appconsole, command); //$NON-NLS-1$
        addOptionalCommand("-nobuild", nobuild, command); //$NON-NLS-1$
        addOptionalCommand("-publish", publish, command); //$NON-NLS-1$
        addOptionalCommand("-refresh", refresh, command); //$NON-NLS-1$
        addOptionalCommand("-showdetails", showdetails, command); //$NON-NLS-1$
        addOptionalCommand("-dataGroupConfig", dataGroupConfig, command); //$NON-NLS-1$
        addOptionalCommand("-dataSourceRow", dataSourceRow, command); //$NON-NLS-1$
        addOptionalCommand("-dataSourceName", dataSourceName, command); //$NON-NLS-1$
        addOptionalCommand("-fail", fail, command); //$NON-NLS-1$
        addOptionalCommand("-environment", environment, command); //$NON-NLS-1$
        addOptionalCommand("-environmentConfig", environmentConfig, command); //$NON-NLS-1$
        addOptionalCommand("-showsettings", showsettings, command); //$NON-NLS-1$
        addOptionalCommand("-prefs", prefs, command); //$NON-NLS-1$
        addOptionalCommand("-report", report, command); //$NON-NLS-1$
        addOptionalCommand("-include", includes, command); //$NON-NLS-1$
        addOptionalCommand("-include", test, command); //$NON-NLS-1$
        addOptionalCommand("-exclude", excludes, command); //$NON-NLS-1$
        addOptionalCommand("-resource", resources, command); //$NON-NLS-1$
        addOptionalCommand("-impactedTests", impactedTests, command); //$NON-NLS-1$
        if (properties != null) {
            for (Entry<String, String> entry : properties.entrySet()) {
                addOptionalCommand("-property", entry.getKey() + '=' + entry.getValue(), command); //$NON-NLS-1$
            }
        }
        if (workItems != null) {
            addOptionalCommand("-workItems", String.join(",", workItems), command); //$NON-NLS-1$
        }
        addTestFilters(testFilters, command);
        runCommand(log, command);
    }

    private static void addOptionalCommand(String name, boolean value, List<String> command) {
        if (value) {
            command.add(name);
        }
    }

    private static void addOptionalCommand(String name, String value, List<String> command) {
        if (value != null && !value.trim().isEmpty()) {
            command.add(name);
            command.add(value);
        }
    }

    private static void addOptionalCommand(String name, File value, List<String> command) {
        if (value != null) {
            command.add(name);
            command.add(value.getAbsolutePath());
        }
    }

    private static void addOptionalCommand(String name, List<String> parameters, List<String> command) {
        if (parameters != null) {
            for (String parameter : parameters) {
                addOptionalCommand(name, parameter, command);
            }
        }
    }

    private static void addTestFilters(List<TestFilter> testFilters, List<String> command) {
        if (testFilters != null) {
            for (TestFilter testFilter : testFilters) {
                String testName = testFilter.getTestName();
                if (testName != null && !testName.trim().isEmpty()) {
                    command.add("-testName"); //$NON-NLS-1$
                    if (testFilter.getSubstringMatch()) {
                        command.add("match:"); //$NON-NLS-1$
                    }
                    command.add(testName);
                    addOptionalCommand("dataSourceRow:", testFilter.getDataSourceRow(), command); //$NON-NLS-1$
                    addOptionalCommand("dataSourceName:", testFilter.getDataSourceName(), command); //$NON-NLS-1$
                }
            }
        }
    }

    private static void runCommand(Log log, List<String> command) throws MojoExecutionException {
        if (log.isDebugEnabled()) {
            log.debug("command:" + lineSeparator() + String.join(lineSeparator(), command)); //$NON-NLS-1$
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

    private static String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("QQyyyyMMddHHmmssSSS"));        
    }

    private boolean reportParameterHasFilename() {
        if (report == null) {
            return false;
        }

        String path = report.getAbsolutePath().toLowerCase();

        if (path.endsWith(".xml") || path.endsWith(".html")) {
            return true;
        }

        return false;
    }

    private boolean isDefaultReportName(String filename) {
        return filename.equals("report.xml") || filename.equals("report.html");
    }

    private void renameExistingReports() {       
        String reportsDirectoryPath;

        if (report == null) {
            reportsDirectoryPath = System.getProperty("user.dir");
        } else if (reportParameterHasFilename()) {
            if (!isDefaultReportName(report.getName().toLowerCase())) {
                return;
            }

            reportsDirectoryPath = report.getParent();
        } else {
            reportsDirectoryPath = report.getAbsolutePath();
        }

        File reportsDirectory = new File(reportsDirectoryPath);

        if (!reportsDirectory.exists() || !reportsDirectory.isDirectory()) {
            return;
        }

        String timestamp = getTimestamp();

        for (File file : reportsDirectory.listFiles()) {
            if (file.isFile()) {
                String filename = file.getName().toLowerCase();

                if (isDefaultReportName(filename)) {
                    String extension = filename.endsWith(".xml") ? ".xml" : ".html";
                    String newFilename = "report_" + timestamp + extension;
                    file.renameTo(new File(reportsDirectory, newFilename));
                }
            }
        }
    }

    private void parseXmlReport(Log log) {        
        File xmlReport;

        if (report == null) {
            xmlReport = new File(System.getProperty("user.dir"), "report.xml");
        } else if (reportParameterHasFilename()) {
            String filename = report.getName();
            int extensionIndex = filename.lastIndexOf(".");
            filename = filename.substring(0, extensionIndex) + ".xml";
            xmlReport = new File(report.getParent(), filename);
        } else {
            xmlReport = new File(report, "report.xml");
        }

        if (!xmlReport.exists() || !xmlReport.isFile()) {
            return;
        }

        log.info("Found the following XML report: " + xmlReport.getAbsolutePath());
    }
}
