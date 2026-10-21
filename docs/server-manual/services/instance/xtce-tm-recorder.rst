XTCE TM Recorder
================

Records XTCE TM sequence containers. This service stores the data coming from one or more streams into a table ``tm``. The tm table has a column called ``pname`` which stands for packet name. The main task of this service is to assign the value for that column; all the other columns will come directly from the tm stream as provided by the data links.

The pname is a fully qualified name of a matching XTCE container. In the XTCE hierarchy some containers have a flag ``UseAsPacketName`` (this flag is an Yamcs extension to XTCE, called ``UseAsArchivingPartition`` in older versions). That flag is used to determine the container that will give its name to the packet when saved into the tm table - the name of the lowest level matching container with this flag set is chosen as the pname. If no container matches, then the name of the root container will be used. 


Class Name
----------

:javadoc:`org.yamcs.archive.XtceTmRecorder`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.archive.XtceTmRecorder

    streamConfig:
      tm:
        - tm_realtime
        - tm_dump

With this configuration containers coming from both the tm_realtime and tm_dump streams are stored into the table ``tm``.


Configuration Options
---------------------

streams (list of strings)
    The streams to record. When unspecified, all ``tm`` streams defined in ``streamConfig`` are recorded.

partitionByPname (boolean)
    If ``true``, the ``tm`` table is partitioned by ``pname``: the packets of each container are stored separately, and the primary key is ``(gentime, seqNum)`` within each partition. If ``false``, ``pname`` is added to the primary key, ``(gentime, seqNum, pname)``. In both cases two packets are considered duplicates (and the second one is not stored) only if they have the same generation time, sequence number and pname.

    Partitioning by pname makes retrievals of a few packet types faster because only their partitions are read. Without it, retrieving all the packets (as done for example by the parameter archive backfilling) is faster, because the data does not have to be merged from many partitions, and the archive does not get fragmented into many small partitions when there are many packet types. The ``partitionGroups`` option offers a middle ground.

    This option is only used when the ``tm`` table is created. Changing it has no effect on an existing archive. It cannot be used together with ``partitionGroups``.

    Default: ``false``. Versions prior to 5.13.8 always partitioned by pname.

partitionGroups (map)
    Partitions the ``tm`` table by groups of packets. The map keys are the group names and the values are lists of fully qualified container names. Packets that are not assigned to any group are stored in a group called ``default``.

    This is useful when some packets are much less frequent than the others, for example event packets compared with housekeeping packets. If the event packets are in their own group, retrieving them only reads that group instead of the whole archive.

    .. code-block:: yaml

        services:
          - class: org.yamcs.archive.XtceTmRecorder
            args:
              partitionGroups:
                events: [/MYSAT/Events]
                science: [/MYSAT/Science/HiRes, /MYSAT/Science/LoRes]

    The group is determined from the packet name (the ``pname`` column): starting from the container having that name and going up in the XTCE hierarchy, the first container listed in a group decides the group. A listed container therefore only has an effect if it, or one of its inheriting containers, is used as packet name (see the ``UseAsPacketName`` property); otherwise a warning is logged at startup.

    The primary key is ``(gentime, seqNum, pname)`` within each group, so the duplicates are detected as without groups.

    This option is only used when the ``tm`` table is created; if the ``tm`` table already exists and is not partitioned by group, it is ignored (with a warning). The groups themselves can be changed later, but this should be done with care: the retrievals filtering on packet names compute the groups to read from the current configuration, so if a packet is moved to a different group, its data recorded before the change will not be found anymore by these retrievals (it will still be found by the retrievals not filtering on packet names). The same applies if changing the MDB causes a packet to be recorded under a different packet name belonging to a different group.

    If the archive is replicated, the replication slaves should use the same ``partitionGroups`` configuration as the master.
