# Yamcs Mission Control
[![Website](https://img.shields.io/website/http/shields.io.svg?label=docs)](https://www.yamcs.org/docs/server/)
[![Build Status](https://travis-ci.org/yamcs/yamcs.svg?branch=master)](https://travis-ci.org/yamcs/yamcs)

Yamcs is an open source mission control framework developed in Java. It uses an open-ended architecture that allows tailoring its feature set using yaml configuration files. You can also extend the default feature set by writing custom java classes.

To build your own Yamcs application, use the [Yamcs Maven Plugin](https://www.yamcs.org/yamcs-maven/yamcs-maven-plugin).

See also:

* Server Manual: [https://www.yamcs.org/docs/server/](https://www.yamcs.org/docs/server/)
* Javadoc: [https://www.yamcs.org/yamcs/javadoc/](https://www.yamcs.org/yamcs/javadoc/)


## Developer Setup

To work on Yamcs itself you need JDK8, Maven and yarn.


### Build

Build Java jars:

    mvn clean install -DskipTests

Build web interface

    cd yamcs-web
    yarn install
    yarn build
    cd ..

These commands will produce an optimized production version of the web interface. This process will take a few minutes. For faster incremental builds refer to instructions under `yamcs-web`.


### Run Simulation

For demo and development purposes we work with an all-in-one simulation environment that uses many Yamcs features. In this simulation, Yamcs receives TM from a simple simulator of a landing spacecraft. Yamcs can also send some basic TC. The simulator starts together with Yamcs as a subprocess.

    ./run-simulation.sh

This will run Yamcs from a directory `live` that mimics the structure of a package installation but where jars and web files are symlinks to the previously built code.

By default this configuration stores data to `/storage/yamcs-data`. Ensure this folder exists and that you can write to it. You can choose a different location by changing the location in `live/etc/yamcs.yaml`.

When you see `Server running... press ctrl-c to stop` your server has fully started. If you built the web files you can now also visit the built-in web interface by navigating to `http://localhost:8090`.
