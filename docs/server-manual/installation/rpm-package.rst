Installing Using an RPM Package
===============================

Yamcs is packaged in RPM format. To install:

.. code-block:: text

    rpm -U yamcs-version.noarch.rpm


File Layout
-----------
    
The RPM installation creates these directory under ``/opt/yamcs``:

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

In addition to the directories mentioned above, yamcs also uses ``/storage/yamcs-data`` to store the data (telemetry, telecomand, event archive). This directory has to be writable by the user ``yamcs``. The location of the data directory can be changed by editing ``/opt/yamcs/etc/yamcs.yaml``
