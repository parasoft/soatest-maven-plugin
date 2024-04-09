# soatest-maven-plugin
Maven plugin for Parasoft SOAtest that wraps soatestcli.

See the [Plugin Documentation](https://parasoft.github.io/soatest-maven-plugin/)

Include the SOAtest Maven Plugin in your project
```xml
  <build>
    <plugins>
      <plugin>
        <groupId>com.parasoft</groupId>
        <artifactId>soatest-maven-plugin</artifactId>
        <version>1.0.1</version>
        <executions>
          <execution>
            <id>soatest</id>
            <phase>validate</phase>
            <goals>
              <goal>soatest</goal>
            </goals>
            <configuration>
              <config>soatest.user://Example Configuration</config>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
```
