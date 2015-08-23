# Overview #

For mixed applications using Java on distributed platforms and  native z/OS code (COBOL, PL/I or C370), it is often important that a single build system controls both Java and z/OS  sources.

Maven is the build system of choice these days so it is useful to be able to upload source code and compile on z/OS as part of a Maven build lifecycle.

The ant FTP task has serious limitations when it comes to uploading source files on z/OS and can't be used to submit JCL.

This project is aimed at developing a Maven plugin capable of uploading several source files at once, in several target PDS libraries and be able to submit multiple compilation JCLs and control their return code.

# Status #
Early aplha release