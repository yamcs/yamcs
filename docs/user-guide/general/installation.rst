Installation
============

Prerequisites
-------------

Yamcs Server runs on 64-bit Linux.

RAM
    >= 1Gb

HD
    >= 500Gb (dependent on amount of data archived)

Java Runtime Environment (JRE)
    >= version 1.8


Install Manually
----------------

Yamcs Server software is packaged in RPM format. To install::

    rpm -U yamcs-version.noarch.rpm

This command also works for upgrading. If a configuration file (in the ``etc`` directory) has been updated with regard to the previous installed version, the old files will be saved with the extension ``.rpmsave``. The user then has to inspect the difference between the two versions and to implement the newly added options into the old configuration files.

To uninstall Yamcs Server use::

    rpm -e yamcs

Note that this will also remove the ``yamcs`` user.

Install from Repository
-----------------------

Yamcs Server packages are distributed via yum and APT. Configure the Yamcs repository appropriate to your distribution following the `repository instructions </downloads/>`_.

RPM (RHEL, Fedora, CentOS)
^^^^^^^^^^^^^^^^^^^^^^^^^^

Install via ``dnf`` (or ``yum`` on older distributions)

.. code-block:: shell

    dnf check-update
    sudo dnf install yamcs

RPM (SLE, openSUSE)
^^^^^^^^^^^^^^^^^^^

.. code-block:: shell

    sudo zypper refresh
    sudo zypper install yamcs

APT (Debian, Ubuntu)
^^^^^^^^^^^^^^^^^^^^

.. code-block:: shell

    sudo apt-get update
    sudo apt-get install yamcs

File Layout
-----------
    
After installing the rpms, the following directories are created under ``/opt/yamcs``:
        
bin
    Contains shell scripts for starting the different programs

cache
    Contains cached serialized java files for the Mission Database. This has to be writable by the user ``yamcs``.

etc
    Contains all the configuration files

lib
    Contains the jars required by Yamcs. ``lib/ext`` is where extensions reside

log
    Contains the log files of Yamcs. It has to be writable by the user ``yamcs``

mdb
    Empty directory where the mission database has to be located.

In addition to the default Yamcs package, in order to get a running server, the yamcs-simulation rpm can also be installed::

      rpm -U yamcs-simulation-version.noarch.rpm
      
This package will provide default configuration files and MDB for running a simple simulation of a UAV.

In addition to the directories mentioned above, yamcs also uses ``/storage/yamcs-data`` to store the data (telemetry, telecomand, event archive). This directory has to be writable by the user ``yamcs``. The location of the data directory can be changed by editing ``/opt/yamcs/etc/yamcs.yaml``

Configuration
-------------
Yamcs configuration files are written in YAML format. This format allows to encode in a human friendly way the most common data types: numbers, strings, lists and maps. For detailed syntax rules, please see `https://yaml.org <https://yaml.org>`_.

The root configuration file is ``etc/yamcs.yaml``. It contains a list of Yamcs instances. For each instance, a file called ``etc/yamcs.instance-name.yaml`` defines all the components that are part of the instance. Depending on which components are selected, different configuration files are needed.

Starting Yamcs Server
---------------------
Normally Yamcs Server should be configured to start automatically on boot via ``/etc/init.d/yamcs-server``. The command will automatically run itself as a lower privilege user (username ``yamcs``), but must initially be run as root for this to happen. Yamcs Server can be started and stopped as a service via commands such as ``service yamcs-server start`` and ``service yamcs-server stop``. These commands use the init.d script and will run Yamcs as the appropriate user. It is also possible to directly use the script ``/opt/yamcs/bin/yamcsd``, but use of the ``service`` command is preferred.
