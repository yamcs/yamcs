This configuration is used to asses the performance of Yamcs for processing telemetry. The simulator sends a configurable number of packets with random content. On the Yamcs server side a MDB will be generated (by the PerfMdbLoader) to define all the packets and parameters within.

Settings which can be adjusted:
- number of packets - how many different packets will be sent. Each packet will have an identier (part of the secondary CCSDS header) and will be decomposed into a number of fixed size integer parameters. The number of packets has to be specified both in the SimulatorCommander perfTest (to configure the simulator to send that number of packets) as well as in the mdb -> PerfMdbLoader settings (to generate that number of packets in the MDB)
- packet size  - size in bytes of each packet. Again this has to be configured both in the simulator and MDB loader.
- interval in milliseconds between sending each batch of packets
- parameter size in bits - this is configured in the PerfMdbLoader and implicitly determines also how many parameters are inside one packet.
- percentange of parameters with alarms - between 0 and 100. If greater than 0, the first parameters in each packet will have warning and critical alarms configured.
- out of limit chance for warning and critical alarms. Between 0 and 1 will be used to compute the warning and critical alarm ranges such that the probability of a randomly generated parameter value falling outside that range is the value configured.


In the current configuration:
 numPackets: 100
 interval: 100 (millisec)
 paramSizeInBits: 32
 packetSize: 1476 (+16 bytes headers)

resulting in 
TM packet rate: 1000 packets/second, about 12Mbps incoming data rate
TM parameter rate: 369 x 100 = 36900 parameters sampled at 10Hz -> 369000 samples/sec 

The simulator sends data via TCP so it might slow down if the server is not able to process all the packets. The yamcs-web frontend page will show the actual data rates.

