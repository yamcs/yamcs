packet-viewer
=============

.. program:: packet-viewer

Synopsis
--------

.. rst-class:: synopsis

    | *packet-viewer* [<*OPTIONS*>]
    | *packet-viewer* [-l <*N*>] -x <*MDB*> <*FILE*>
    | *packet-viewer* [-l <*N*>] [-x <*MDB*>] -i <*INSTANCE*> [-s <*STREAM*>] <*URL*>


Description
-----------

Use :program:`packet-viewer` to extract parameters from packets by either loading a packet dump from disk (~ offline mode), or by decoding the raw data received from connecting to a Yamcs server (~ online mode).

In *online mode*, the splitting of packets is done by Yamcs Server and :program:`packet-viewer` extracts parameters from each packet binary by using the same logic as Yamcs Server would.

In *offline mode* :program:`packet-viewer` must in addition have access to a local MDB, and requires configuration so that it knows how to decode indidivual packets from a dump file. By default, dump files are assumed to contain concatenated CCSDS.


Options
-------

.. option:: -h

    Print a help message and exit.

.. option:: -l <N>

    Limit the view to <N> packets.
    
    In *online mode* only the last <N> packets will be visible. The default is 1000.
    
    In *offline mode* only the first <N> packets of the file are displayed. There is no default, but for large dumps :program:`packet-viewer` may become sluggish or run out of heap memory.

.. option:: -x <MDB>

    Name of the applicable MDB as specified in the ``mdb.yaml`` configuration file.

    This option is required in *offline mode*. In *online* mode the MDB defaults to that of the connected Yamcs instance.

.. option:: -i <INSTANCE>

    In *online mode*, this indicates which instance's telemetry stream :program:`packet-viewer` should connect to.

.. option:: -s <STREAM>

    In *online mode*, this indicates which telemetry stream :program:`packet-viewer` should connect to. 
    
    Default: ``tm_realtime``.

.. option:: <FILE>

    A local file which contains one or more packets. Typically concatenated CCSDS, but other file formats can be defined through configuration.

.. option:: <URL>

    Base URL of a Yamcs server.


Examples
--------

Online mode:

.. code-block:: console

    packet-viewer -l 50 -x my-db packet-file


Offline mode:

.. code-block:: console

    packet-viewer -l 50 -i simulator http://localhost:8090


Configuration Files
-------------------

:program:`packet-viewer` configuration files are placed in the ``etc/`` directory. MDB files for local packet decoding are placed in ``mdb/`` directory.

.. code-block:: text

    <packet-viewer>
    |-- bin/
    |-- etc/
    |   |-- mdb.yaml
    |   +-- packet-viewer.yaml
    |-- lib/
    +-- mdb/
        |-- xtce1.xml
        +-- xtce2.xml

mdb.yaml
~~~~~~~~

Specifies one or more MDB configurations, which you can then choose from in order to extract parameters from a packet.

The MDB configuration structure can be copied from a ``yamcs.<instance>.yaml`` configuration file, but with a level on top which specifies the name visible in UI. In the following example, the user can choose between `mymdb1` and `mymdb2`.

.. code-block:: yaml

    mymdb1:
       - type: "xtce"
         args:
           file: "mdb/xtce1.xml"

    mymdb2:
       - type: "xtce"
         args:
           file: "mdb/xtce2.xml"

packet-viewer.yaml
~~~~~~~~~~~~~~~~~~

``packetPreprocessorClassName`` / ``packetPreprocessorArgs``
    Configure a packet pre-processor. Configuration options are identical to preprocessor configuration of a data link on Yamcs Server.

``fileFormats``
   List of supported file formats when opening a local packet dump file. The file format determines how to split the file in packets. Sub-keys:

    ``name``
       Name of the format, as visible in UI.
    
    ``packetInputStreamClassName`` / ``packetInputStreamArgs``
        Configures a packet input stream. Configuration options are identical to packet input stream configuration of a data link on Yamcs Server.
    
    ``rootContainer``
        Qualified name of the base container. Required if it cannot be uniquely determined.

Example:

.. code-block:: yaml

    packetPreprocessorClassName: org.yamcs.tctm.IssPacketPreprocessor
    fileFormats:
      - name: CCSDS Packets
        packetInputStreamClassName: org.yamcs.tctm.CcsdsPacketInputStream
