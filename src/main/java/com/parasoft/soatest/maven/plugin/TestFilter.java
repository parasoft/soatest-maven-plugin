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

public class TestFilter {
    private String testName;
    private boolean substringMatch;
    private String dataSourceName;
    private String dataSourceRow;

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public Boolean getSubstringMatch() {
        return substringMatch;
    }

    public void setSubstringMatch(Boolean substringMatch) {
        this.substringMatch = substringMatch;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getDataSourceRow() {
        return dataSourceRow;
    }

    public void setDataSourceRow(String dataSourceRow) {
        this.dataSourceRow = dataSourceRow;
    }
}
