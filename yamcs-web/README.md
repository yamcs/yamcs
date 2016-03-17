## Yamcs Web

Web-based dashboard application for Yamcs.

This project is in incubation phase. Embrace change.

### Prerequisites

* npm
* bower
* gulp

Once you have installed npm, you can install the other two like this:

    sudo npm install -g bower gulp

If you want to run npm as a non-root user, you might need to
ensure non-root users have write permission in its temporary folder, like:

   sudo chmod go+w ~/tmp/

On Debian-based Linux distributions such as Ubuntu, the NodeJS binary is named "nodejs" instead of "node".
To ensure the NodeJS binary can be found by the build scripts, a symlink is required:

   sudo ln -s /usr/bin/nodejs /usr/bin/node

### First Installation

Fetch dependencies:

    npm install
    bower install

### Build site

    gulp

The output is in the folder `./build`. Indicate this folder as one of the web roots in your `yamcs.yaml` to let Yamcs serve its content.

Run `gulp` whenever you changed a source file. A watch functionality will be added at a later time.
