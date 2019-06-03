General Instructions
====================

Yamcs runs on Java, but does not bundle Java. A distribution of Java 8 or higher should be installed on the machine that runs Yamcs.

Currently we only provide support for running on Linux x64. Other platforms may also work but not without applying undocumented tweaks.


Where to Get Yamcs
------------------

You can find the latest release at https://yamcs.org/downloads/.

Older releases may be available at https://yamcs.org/downloads/archive/.


Distribution Methods
--------------------

For each recent release there are typically these different artifacts:

* RPM Bundle for use on SuSE, and RedHat-like platforms.
* DEB Package for use on Debian-like platforms.
* Binary Tarball for us on any Linux x64

All packages are "Generic Linux". They do not make use of specific distro features and conventions.

We recommend most users to install either RPM or DEB. These install Yamcs to ``/opt/yamcs``, create a user and configure a daemon.
