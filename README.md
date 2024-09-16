# Yamcs Mission Control ![Maven Central](https://img.shields.io/maven-central/v/org.yamcs/yamcs.svg?label=release)

* Website: https://yamcs.org
* Mailing list: [Google Groups](https://groups.google.com/group/yamcs/)

Yamcs is a mission control framework developed in Java. It uses an open-ended architecture that allows tailoring its feature set using yaml configuration files. You can also extend the default feature set by writing custom Java classes.

To start developing your own Yamcs application, follow our [Getting Started](https://yamcs.org/getting-started) guide.


## Documentation

* Server Manual: https://docs.yamcs.org/yamcs-server-manual/
* Javadoc: https://docs.yamcs.org/javadoc/yamcs/latest/


## License

Yamcs is licensed under Affero GPLv3.

For commercial licensing please contact [Space Applications Services](https://www.spaceapplications.com) with your use case.


## Development Setup

To work on the core components of Yamcs you need JDK17+, Maven and npm.

Build Java jars:

    mvn clean install -DskipTests

Build web interface:

    cd yamcs-web/src/main/webapp
    npm install
    npm run build
    cd -

These commands will produce an optimized production version of the web interface. This process will take a few minutes. For faster incremental builds run in watch mode (`npm run watch`).

For demo and development purposes we work with an all-in-one simulation environment that uses many Yamcs features. In this simulation, Yamcs receives TM from a simple simulator of a landing spacecraft. Yamcs can also send some basic TC. The simulator starts together with Yamcs as a subprocess.

    ./run-example.sh simulation

This configuration stores data to `/storage/yamcs-data`. Ensure this folder exists and that you can write to it.

When Yamcs started successfully, you can visit the built-in web interface by navigating to `http://localhost:8090`.

**Note to Windows users:** This repository uses some relative symbolic links. To support this on Windows:
* Enable "Developer Mode" in Windows (allows to use `mklink` without administrative privileges).
* Enable msysgit symlink support: `git config --global core.symlinks true`
* If you already cloned the repository prior to these steps, `git status` will tell you how to convert the symlinks. 


## Contributions

While Yamcs is managed and developed by Space Applications Services, we also consider pull requests from other contributors. For non-trivial patches we ask you to sign our [CLA](https://yamcs.org/static/Yamcs_Contributor_Agreement_v2.0.pdf).
