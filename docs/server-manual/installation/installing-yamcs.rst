Installing Yamcs
================

.. rubric:: Prerequisites

RAM
    >= 1Gb

HD
    >= 500Gb (dependent on amount of data archived)

Java
    >= version 1.8


.. rubric:: Where to find Yamcs

You can find the latest release at https://yamcs.org/downloads/.

Older releases may be available at https://yamcs.org/downloads/archive.


.. rubric:: What package to pick

Releases of Yamcs target Linux 64-bit only. Other platforms are not currently supported.

For each recent release there are typically these different artifacts:

* RPM Bundle for use on SuSE, and RedHat-like platforms.
* DEB Package for use on Debian-like platforms.
* Binary Tarball for us on any Linux x64

All packages are "Generic Linux". They do not make use of specific distro features and conventions.

We recommend most users to install either RPM or DEB. These install Yamcs to ``/opt/yamcs``, creates a user and configures a daemon.

Installing from 
