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
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLStreamException;

import org.apache.maven.shared.utils.xml.PrettyPrintXMLWriter;
import org.apache.maven.shared.utils.xml.XMLWriter;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;


public class SOAtestResults {
    private final int total;
    private final int failed;

    private SOAtestResults() {
        this.total = 0;
        this.failed = 0;
    }

    private SOAtestResults(int total, int failed) {
        this.total = total;
        this.failed = failed;
    }

    public static SOAtestResults parseReportXML(File report) throws XMLStreamException {
        XMLStreamReader2 reader = null;

        try {
            XMLInputFactory2 inputFactory = (XMLInputFactory2)XMLInputFactory2.newInstance();
            reader = (XMLStreamReader2)inputFactory.createXMLStreamReader(report);

            boolean inFunctionalTestDetails = false;

            while (reader.hasNext()) {
                int eventType = reader.next();

                if (eventType == XMLEvent.START_ELEMENT) {
                    String element = reader.getName().toString();

                    if (element.equals("Total") && inFunctionalTestDetails) { //$NON-NLS-1$
                        return createResults(reader);
                    } else if (element.equals("ExecutedTestsDetails") && isFunctionalTestDetails(reader)) { //$NON-NLS-1$
                        inFunctionalTestDetails = true;
                    }
                } else if (eventType == XMLEvent.END_ELEMENT) {
                    String element = reader.getName().toString();

                    if (inFunctionalTestDetails && (element.equals("Total") || element.equals("ExecutedTestsDetails"))) { //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    }
                }
            }

            return new SOAtestResults();
        } finally {
            if (reader != null) {
                reader.closeCompletely();
            }
        }
    }

    public void writeFailsafeSummary(Path failsafeSummary) throws IOException {
        try (Writer writer = Files.newBufferedWriter(failsafeSummary)) {
            XMLWriter xmlWriter = new PrettyPrintXMLWriter(writer, StandardCharsets.UTF_8.name(), null);
            
            xmlWriter.startElement("failsafe-summary"); //$NON-NLS-1$

            if (this.total == 0 || this.failed > 0) {
                String result = this.total == 0 ? "NO_TESTS" : "FAILURE"; //$NON-NLS-1$ //$NON-NLS-2$
                xmlWriter.addAttribute("result", result); //$NON-NLS-1$
            }

            xmlWriter.addAttribute("timeout", "false"); //$NON-NLS-1$ //$NON-NLS-2$
            xmlWriter.startElement("completed"); //$NON-NLS-1$
            xmlWriter.writeText(String.valueOf(this.total));
            xmlWriter.endElement();
            xmlWriter.startElement("errors"); //$NON-NLS-1$
            xmlWriter.writeText("0"); //$NON-NLS-1$
            xmlWriter.endElement();
            xmlWriter.startElement("failures"); //$NON-NLS-1$
            xmlWriter.writeText(String.valueOf(this.failed));
            xmlWriter.endElement();
            xmlWriter.startElement("skipped"); //$NON-NLS-1$
            xmlWriter.writeText("0"); //$NON-NLS-1$
            xmlWriter.endElement();
            xmlWriter.startElement("failureMessage"); //$NON-NLS-1$
            xmlWriter.endElement();
            xmlWriter.endElement();
        }
    }

    private static SOAtestResults createResults(XMLStreamReader2 reader) {
        String totalAttributeValue = reader.getAttributeValue(null, "total"); //$NON-NLS-1$
        String failAttributeValue = reader.getAttributeValue(null, "fail"); //$NON-NLS-1$

        int total = totalAttributeValue == null ? 0 : Integer.parseInt(totalAttributeValue);
        int failed = failAttributeValue == null ? 0 : Integer.parseInt(failAttributeValue);

        return new SOAtestResults(total, failed);
    }

    private static boolean isFunctionalTestDetails(XMLStreamReader2 reader) {
        String functionalAttributeValue = reader.getAttributeValue(null, "functional"); //$NON-NLS-1$
        String typeAttributeValue = reader.getAttributeValue(null, "type"); //$NON-NLS-1$

        boolean foundExpectedFunctionalValue = functionalAttributeValue == null ? false : functionalAttributeValue.equals("true"); //$NON-NLS-1$
        boolean foundExpectedTypeValue = typeAttributeValue == null ? false : typeAttributeValue.equals("FT"); //$NON-NLS-1$

        return foundExpectedFunctionalValue && foundExpectedTypeValue;
    }
}
