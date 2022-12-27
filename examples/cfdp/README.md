This example demonstrates the usage of the CCSDS File Delivery Protocol. 

The simulator supports receiving files, it stores the content into a temporary file. To simulate data loss, it drops about a fifth of the Data packets and about half of the EOF packets.

Be sure to check the _src/main/yamcs/etc/extra_streams.sql_ to see how the CFDP packets are inserted/extracted as CCSDS packets in the TM/TC streams.

