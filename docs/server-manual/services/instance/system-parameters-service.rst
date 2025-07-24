System Parameters Service
===========================

Collects system parameters from any Yamcs component at a frequency of 1 Hz. Parameter values are emitted to the ``sys_var`` stream.

System parameters are grouped under the parameter namespace :file:`/yamcs/{serverId}/` where ``serverId`` identifies the system, defaulting to the hostname. A custom ``serverId`` may be specified in :file:`etc/yamcs.yaml`. This should uniquely identify your Yamcs system among all other Yamcs systems you are running.

.. code-block:: yaml

    serverId: myserver


Class Name
----------

:javadoc:`org.yamcs.parameter.SystemParametersService`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.parameter.SystemParametersService
        args:
          producers:
            - diskstats
            - fs
            - jvm
            - loadavg
            - rocksdb


Configuration Options
---------------------

producers (list of strings)
    Specify built-in groups of system parameters, to be added to the list of parameters.

    Available choices:
    
    ``diskstats``
       Parameters describing disk IO, similar to the Linux ``iostat`` command.

       .. warning::
          This reads :file:`/proc/diskstats` and so only work on Linux.
    ``fs``
       Parameters describing the system disks: total space, available space and percentage used.

       On Linux, only file system types ``ext3``, ``ext4`` and ``xfs`` are considered.
    ``jvm``
       Parameters describing JVM metrics: total memory, used memory, thread count.
    ``loadavg``
       Adds a parameter showing the system's 1 minute load average.
    ``rocksdb``
       Adds parameters for each RocksDB database, containing some RocksDB metrics.
