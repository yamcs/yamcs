This configuration is used to asses the performance of Yamcs for processing telemetry. 

Please see the perftest1 for a description of the parameters.

This configuration differs from perftest1:
- the realtime parameter archive filler is disabled, the backfiller is used to post-process the data.
- the parameter cache only caches certain parameters, so not all parameters are processed in realtime.
   Those which have alarms are and also those requested by users (e.g. all the parameters shown in the displays).
- the maxSegmentSize of the parameter archive has been set to 200. This means that when the backfiller runs, it will accumulate in memory maximum 200 samples for each parameter in each group; incresing that number will reduce the archive size (becuase data can be compressed better) at the expense of RAM.

The peformance which can be reached with this configuration on a quad core i7-8650U CPU (SSD disk):

 numPackets: 200
 interval: 100 (millisec)
 packetSize: 1476 (+16 bytes headers)

resulting in 
TM packet rate: 2000 packets/second, about 22Mbps incoming data rate.

