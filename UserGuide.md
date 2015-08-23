# Introduction #

The zosupload [Maven](http://maven.apache.org/) plugin is a utility to help with uploading z/OS source code as part of a Maven build lifecycle.

It is usually necessary to upload source files in different PDS libraries on z/OS and also to be able to submit JCL (such as compilation) on z/OS and check the return codes as part of the build process.


# Using the plugin #

## POM elements ##

In your maven POM plugins section add a plugin description such as:

```
            <!-- Upload sources and submit JCL. -->
            <plugin>
                <groupId>com.legsem.legstar</groupId>
                <artifactId>zosupload-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>upload-zos-programs</id>
                        <phase>generate-test-resources</phase>
                        <configuration>
                            <hostName>192.168.0.17</hostName>
                            <hostUserId>MYUSERID</hostUserId>
                            <hostPassword>MYPASSWD</hostPassword>
                            <inputFolder>target/zos</inputFolder>
                            <remoteFilesPrefix>MYUSERID.SRCE</remoteFilesPrefix>
                        </configuration>
                        <goals>
                            <goal>upload</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

```

## Local folders layout ##

The plugin assumes a specific folder layout and some file conventions.

Assuming an input folder "target/zos", each direct subfolder is assumed to have a direct PDS counterpart. For instance, a sub folder "target/zos/COBOL" assumes a PDS named MYUSERID.SRCE.COBOL exists on z/OS (provided the remoteFilesPrefix is MYUSERID.SRCE).

Furthermore, the file names in the subfolders must be valid PDS member names. They should not have any suffix and are uploaded as is to z/OS.

Finally, a subfolder named CNTL ("target/zos/CNTL" for instance) has special significance. It is assumed the content of that sub folder needs to be executed on z/OS as part of the build lifecycle. The files in CNTL must therefore be valid JCL, ready for execution.

## JCL execution order ##

With no further setting, the plugin takes JCLs out of the CNTL sub folder in whatever order they are stored on the file system.

The plugin has no mean of knowing that a particular JCL needs to run before some other one.

If order is important, then you have to provide an additional element to the plugin configuration like so:

```
                        <configuration>
                            <hostName>192.168.0.17</hostName>
                            <hostUserId>MYUSERID</hostUserId>
                            <hostPassword>MYPASSWD</hostPassword>
                            <inputFolder>target/zos</inputFolder>
                            <remoteFilesPrefix>MYUSERID.SRCE</remoteFilesPrefix>
                            <jclFileNames>
                                <jclFileName>COBCLFAE</jclFileName>
                                <jclFileName>CICSCSDU</jclFileName>
                                <jclFileName>BUILDXMI</jclFileName>
                            </jclFileNames>
                        </configuration>
```

With this configuration, all files from target/zos/CNTL are uploaded to MYUSERID.SRCE.CNTL but only 3 JCL are actually submitted in the order specified.