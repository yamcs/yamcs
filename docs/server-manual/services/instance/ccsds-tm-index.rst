CCSDS TM Index
==============

Creates an index for the CCSDS Space Packets (`CCSDS 133.0-B-1 <https://public.ccsds.org/Pubs/133x0b1c2.pdf>`) based on the sequence count in the primary header. The index allows to see per APID the available packets in the archive. The main use of such index is to detect when packets are missing. It can be combined with user defined scripts that request missing data from remote systems (if such systems exist that record data in the user specific setup).

The configuration allows to define a list of tm streams where the packets are read from. The packets on those streams have to be CCSDS space packets. This service does not use the Mission Database for interpreting the packets, it just reads the primary header from  the binary data. If the packet legnth is less than 7 bytes, it is discarded.


The index can be visualised in the Yamcs web interface in the Archive -> Overview. It is denoted as "Completeness" and contains one timeline bar for each APID.

Class Name
----------

:javadoc:`org.yamcs.archive.CcsdsTmIndex`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.archive.IndexServer
        streams: ["tm-realtime", "tm_dump"]


Configuration Options
---------------------

streams (list of strings)
    The streams to index. When unspecified, all ``tm`` streams defined in ``streamConfig`` are indexed. 
