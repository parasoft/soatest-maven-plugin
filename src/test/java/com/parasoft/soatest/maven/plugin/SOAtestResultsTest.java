/*
 * Copyright 2024 Parasoft Corporation
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class SOAtestResultsTest {
    @Test
    public void parseReportXML_WhenReportHasZeroTests_ReturnsTotalZeroAndFailedZero() throws Exception {
        File report = new File("target/test-classes/report_with_total_zero.xml");
        assertTrue(report.exists());

        SOAtestResults results = SOAtestResults.parseReportXML(report);
        assertEquals(results.getTotal(), 0);
        assertEquals(results.getFailed(), 0);
    }

    @Test
    public void parseReportXML_WhenReportHasTenTestsAndAllPass_ReturnsTotalTenAndFailedZero() throws Exception {
        File report = new File("target/test-classes/report_with_total_ten_pass_ten.xml");
        assertTrue(report.exists());

        SOAtestResults results = SOAtestResults.parseReportXML(report);
        assertEquals(results.getTotal(), 10);
        assertEquals(results.getFailed(), 0);
    }

    @Test
    public void parseReportXML_WhenReportHasTenTestsAndTwoFail_ReturnsTotalTenAndFailedTwo() throws Exception {
        File report = new File("target/test-classes/report_with_total_ten_fail_two.xml");
        assertTrue(report.exists());

        SOAtestResults results = SOAtestResults.parseReportXML(report);
        assertEquals(results.getTotal(), 10);
        assertEquals(results.getFailed(), 2);
    }

    @Test
    public void writeFailsafeSummary_WhenReportHasZeroTests_WritesCompletedZero() throws Exception {
        File report = new File("target/test-classes/report_with_total_zero.xml");
        assertTrue(report.exists());

        File expectedSummary = new File("target/test-classes/failsafe_summary_completed_zero.xml");
        assertTrue(expectedSummary.exists());

        Path actualSummary = Paths.get("target/test-classes/failsafe-summary.xml");
        SOAtestResults results = SOAtestResults.parseReportXML(report);
        results.writeFailsafeSummary(actualSummary);

        String actual = new String(Files.readAllBytes(actualSummary));
        String expected = new String(Files.readAllBytes(expectedSummary.toPath()));
        assertEquals(actual, expected);
    }

    @Test
    public void writeFailsafeSummary_WhenReportHasTenTestsAndAllPass_WritesCompletedTen() throws Exception {
        File report = new File("target/test-classes/report_with_total_ten_pass_ten.xml");
        assertTrue(report.exists());

        File expectedSummary = new File("target/test-classes/failsafe_summary_completed_ten.xml");
        assertTrue(expectedSummary.exists());

        Path actualSummary = Paths.get("target/test-classes/failsafe-summary.xml");
        SOAtestResults results = SOAtestResults.parseReportXML(report);
        results.writeFailsafeSummary(actualSummary);

        String actual = new String(Files.readAllBytes(actualSummary));
        String expected = new String(Files.readAllBytes(expectedSummary.toPath()));
        assertEquals(actual, expected);
    }

    @Test
    public void writeFailsafeSummary_WhenReportHasTenTestsAndTwoFail_WritesCompletedTenFailuresTwo() throws Exception {
        File report = new File("target/test-classes/report_with_total_ten_fail_two.xml");
        assertTrue(report.exists());

        File expectedSummary = new File("target/test-classes/failsafe_summary_completed_ten_failures_two.xml");
        assertTrue(expectedSummary.exists());

        Path actualSummary = Paths.get("target/test-classes/failsafe-summary.xml");
        SOAtestResults results = SOAtestResults.parseReportXML(report);
        results.writeFailsafeSummary(actualSummary);

        String actual = new String(Files.readAllBytes(actualSummary));
        String expected = new String(Files.readAllBytes(expectedSummary.toPath()));
        assertEquals(actual, expected);
    }
}