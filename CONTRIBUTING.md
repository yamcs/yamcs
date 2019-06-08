### Prerequisites:

* JDK 8
* Maven
* yarn


### Build

Build Java jars:

    make clean build

Build web interface

    cd yamcs-web
    yarn install
    yarn build
    cd ..

By default this makes an optimized production version of the website. This process will take a few minutes. For faster incremental builds refer to instructions under `yamcs-web`.


### Prepare development environment
For development purposes you can generate a directory `live` that mimics the structure of a package installation but where jars and web files are symlinks to the previously built code:

    make live

By default this configuration will store data to `/storage/yamcs-data`. Ensure this folder exists and that you can write to it. You can choose a different location by changing the location in `live/etc/yamcs.yaml`.

This live environment is preconfigured for an all-in-one simulation that is intended for demo or development purposes only. This setup configures Yamcs to accept data from a a simple simulator that generates TM and that accepts a few very basic commands. The simulator starts together with Yamcs as a subprocess.


### Run Yamcs

    live/bin/yamcsd

When you see `Server running... press ctrl-c to stop` your server has fully started. If you built the web files you can now also visit the built-in web interface by navigating to `http://localhost:8090`.
