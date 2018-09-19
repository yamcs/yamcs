Below instructions are targeted at Yamcs developers.

* **End-User documentation** is available at: [https://www.yamcs.org/docs/server/](https://www.yamcs.org/docs/server/)
* Extension development is not currently documented.

---

### Prerequisites:

* JDK 8
* Maven
* yarn

### Build

Build Java jars:

    mvn clean install -DskipTests

Build web interface

    cd yamcs-web
    yarn install
    yarn build
    cd ..

By default this makes an optimized production version of the website. This process will take a few minutes. For faster incremental builds refer to instructions under yamcs-web.

### Prepare development environment
The root folder contains a script `make-live-devel.sh`. This script creates a directory `live` that mimics the structure of a package installation but where jars and web files are symlinks to the previously built code:

    ./make-live-devel.sh --yss

By default this configuration will store data to `/storage/yamcs-data`. Ensure this folder exists and that you can write to it. You can choose a different location by changing the location in `live/etc/yamcs.yaml`.

By providing the `--yss` flag, this live environment is preconfigured for an all-in-one simulation that is useful for demo or development purposes only. This setup configures Yamcs to accept data from a a simple simulator that generates TM and that accepts a few very basic commands. The simulator starts together with Yamcs as a subprocess.

### Run Yamcs

    cd live
    bin/yamcs-server.sh

When you see `Server running... press ctrl-c to stop` your server has fully started. If you built the web files you can now also visit the built-in web interface by navigating to `http://localhost:8090`.

### CI Status

[![Build Status](https://travis-ci.org/yamcs/yamcs.svg?branch=master)](https://travis-ci.org/yamcs/yamcs)
