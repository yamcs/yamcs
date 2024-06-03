This configuration is used to asses the performance of Yamcs for processing telemetry. 

Please see the perftest1 for a description of the parameters.

This configuration differs from perftest1:
- the realtime parameter archive filler is disabled, the backfiller is used to post-process the data.
- the parameter cache only caches certain parameters, so not all parameters are processed in realtime.
   Those which have alarms are and also those requested by users (e.g. all the parameters shown in the displays). 
- the maxSegmentSize of the parameter archive has been set to 200. This means that when the backfiller runs, it will accumulate in memory maximum 200 samples for each parameter in each group; incresing that number will reduce the archive size (becuase data can be compressed better) at the expense of RAM.

The peformance which can be reached with this configuration on a quad core i5-1345U CPU (SSD disk):

 numPackets: 200
 interval: 100 (millisec)
 packetSize: 1476 (+16 bytes headers)
 percentangeParamWithAlarms: 5

resulting in 
TM packet rate: 2000 packets/second, about 22Mbps incoming data rate.
TM parameter rate: 369 x 200 = 73800 parameters sampled at 10Hz -> 738000 samples/sec
Among the 73800, 3690 parameters are monitored (because they have alarms), the other are not extracted in realtime (unless some client subscribes to them), only during the parameter archive backfilling.

When there is not backfilling and only one web client connected, the load is about 50%.

During the backfilling, the speed of parameter archive processing is about 2 millions samples/second, so about 2.5 times faster than real time.
That means one cannot run too often the backfilling: the parameter archive interval is 139 minutes, any backfilling process will process data from an entire interval. One interval requires 50 minutes to process in full. 

