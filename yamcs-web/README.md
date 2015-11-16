## Yamcs Web

Web-based dashboard application for Yamcs.

This project is in incubation phase. Embrace change.

### Prerequisites

* npm
* bower
* gulp

Once you have installed npm, you can install the other two like this:

    sudo npm install -g bower
    sudo npm install -g gulp

### First Installation

Fetch dependencies:

    npm install

This will also fetch bower dependencies.

### Build site

    gulp

The output is in the folder `./build`. Indicate this folder as a web root in your `yamcs.yaml` to let Yamcs serve its
content.

Run `gulp` whenever you changed a source file. A watch functionality will be added at a later time.
