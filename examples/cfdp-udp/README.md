This example demonstrates the usage of the CCSDS File Delivery Protocol over UDP. 

It expects two external UDP connections (incoming and outgoing) that sends and receives the raw CFDP PDUs.
The _yamcs.cfdp.yaml_ configuration file defines the TM/TC UDP links, processors, and streams that make it possible.

Be sure to check the _src/main/yamcs/etc/extra_streams.sql_ to see how the CFDP packets are inserted/extracted in the TM/TC streams,
as well as _src/main/yamcs/cfdp-pdu.xls_ that describes the packet structure and data types for insertion into the MDB container.
