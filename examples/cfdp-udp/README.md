This example demonstrates the usage of the CCSDS File Delivery Protocol. 

The simulator supports receving files, it stores the content into a temporary file. To simulate data loss, it drops about a fifth of the Data packets and about half of the EOF packets.

Be sure to check the src/main/yamcs/etc/extra_streams.sql to see how the CFDP packets are inserted/extracted as CCSDS packets in the tm/tc streams.

