Parameter Archive Service
=========================

The Parameter Archive stores time ordered parameter values. The parameter archive is column-oriented and is optimized for accessing a (relatively small) number of parameters over longer periods of time. Data is stored in fixed duration time intervals, each interval covering a length of :math:`2^{23}` milliseconds (~139 minutes). 

An interval has always to be processed or reprocessed in full - this means if one data point is added in the interval, the full 139 minutes of data have to be reprocessed.

Intervals are further split into segments such that each segment cannot contain more than a configurable maximum number of samples. This is done in order to limit the number of samples stored in memory when rebuilding an interval. 
A parameter that comes at high frequency will be split into multiple segments whereas for one that comes at low frequency there will be only one segment in each interval.

The parameters are grouped such that the samples of all parameters from one group have the same timestamp. For example all parameters extracted from one TM packet have usually the same timestamp and are part of the same group. A special case is the aggregate parameters: these are decomposed into the individual members if scalar types but all values are belonging to the same group and thus the aggregate can be rebuilt even though the members are stored separately.


Class Name
----------

:javadoc:`org.yamcs.parameterarchive.ParameterArchive`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.parameterarchive.ParameterArchive
        args: 
          realtimeFiller:
            enabled: true
            flushFrequency: 300  #seconds
          backFiller:
            #warmupTime: 60 seconds default warmupTime
            enabled: true
            schedule: [{startInterval: 10, numIntervals: 3}]

This configuration enables the realtime filler, and in addition the backFiller fills the archive 10 intervals in the past, 3 intervals at a time.

.. code-block:: yaml

    services:
      - class: org.yamcs.parameterarchive.ParameterArchive
        args:
          realtimeFiller:
            enabled: false
          backFiller:
            enabled: true
            warmupTime: 120
            schedule:
              - {startInterval: 10, numIntervals: 3}
              - {startInterval: 2, numIntervals: 2, frequency: 600}

This configuration does not use the realtime filler, but instead performs regular (each 600 seconds) back-fillings of the last two intervals. It is the configuration used in the :abbr:`ISS (International Space Station)` ground segment where due to regular (each 20 to 30 minutes) LOS (loss of signal), the archive is very fragmented and the only way to obtain continuous data is to perform replays.


General Options
---------------

maxSegmentSize (integer)
     The ParameterArchive stores data in segments, each segment storing multiple samples of the same parameter. This option configures the maximum segment size. 

     The parameter archive accumulates data in memory to fill the segments, in parallel for all parameters. This option affects thus the memory consumed when the parameter archive is being filled.

     The segment size is limited by the duration of an interval, a segment cannot be larger than :math:`2^{23}` milliseconds (approximately 139 minutes).

     Starting with Yamcs 5.10 the segments from an interval are merged together inside RocksDB such that when retrieving there is only one segment for each interval.
     In order to reduce the memory consumption during parameter archive buildup, the default value of this setting has been changed from 5000 to 500.

     Default: ``500``

sparseGroups (boolean)
    If set to true Parameter Archive will allow gaps in the parameter groups. This reduces the memory consumption and increases the retrieval speed at the expense of storing a gap list with some parameters.
    
    Default: ``true``
    
minimumGroupOverlap (double)
    The term "minimum overlap" falling between 0 and 1 refers to the threshold used when determining if a parameter list belongs to an existing group. Overlap between a parameter list and an existing group (which is also formed from a parameter list) is calculated by dividing the number of the common elements in both lists by the length of the smaller list. If one list is entirely contained within another, the overlap value is 1.
    
    Default: ``0.5``
    
    
    

Backfiller Options
------------------

These options appear under the ``backFiller`` key.

enabled  (boolean)
     If true the backfiller is enabled and executes according to the schedule. Default: ``true``
     
warmupTime (integer)
     When the backfiller performs a replay to fill a data interval, the replay will start this number of seconds before the interval start. This is sometimes required for algorithms that aggregate data, to be able to have all the input data necessary to produce the output. Default: ``60``
    

monitorStreams (string[])
     The list of tm or parameter streams that will be monitored to check for new data. If the list is empty, no stream will be monitored and the archive will be rebuilt according to the schedule defined below. Default: all the tm and param streams defined in the :file:`etc/yamcs.{instance}.yaml` streamConfig section. The backfiller will check the generation time of the packet or parameter received on the monitoring streams and will mark that interval as ``dirty``. As soon as the ``streamUpdateFillFrequency`` timer (see below) expires, a new filling task is started for that interval.

streamUpdateFillFrequency (integer)
     Valid if the ``monitorStreams`` is not empty, configures how often in seconds the fillup based on the stream monitoring is started. The fillup only starts if new data has been received on the streams. Default ``600``.

schedule (list of maps)
    This option contains a list of schedules configuring when the parameter archive runs. This is used when the back filler does not monitor any input stream and instead rebuilds the archive according to a schedule (even if there was maybe no new data received). Each map in the list has the following keys:
    
    startInterval (integer)
        **Required.** when a backfiller starts, it starts processing with this number of intervals in the past.
    
    numIntervals (integer)
        **Required.**  how many intervals to process at one time
    
    frequency (integer)
    
compactFrequency (integer)
    After how many backfilling tasks to compact the underlying RocksDB database. Because the backfiller removes the previous data, RocksDB will have lots of tombstones to skip over when reading. Compacting will get rid of the tombstones. Compacting improves the reading at the expense of writing speed.
    ``-1`` means that no compaction will be performed (RocksDB merges by itself files, and that also gets rid of the tombstones).
    
    Default value: 5
    

Realtime filler Options
-----------------------
   
enabled  (boolean)
     If true the backfiller is enabled and executes according to the schedule. Default: ``true``
 
processorName (String)
     The name of the processor used to receive realtime data. Default: ``realtime``
     
sortingThreshold (integer) milliseconds
     When receiving realtime data, the realtime filler builds up data in memory. In order to know that data can be written to the archive (whole segments at once) the filler needs to know that no data can be received into the old segments. This option configures in milliseconds the amount of acceptable unsorting - that is each new data timestamp which is older than the previous received data timestamp, will be accepted as long as the difference is not bigger than this.
     
     This option is interpreted at the level of parameter group; For example having multiple streams of TM packets (a stream understood as an ordered sequence of packets not necessarily a Yamcs stream) with different timestamps is not a problem as long as each stream has its monotonic increasing time.
     
     Note also the option ``pastJumpThreshold`` below. Default: ``1000`` 

pastJumpThreshold (integer) seconds
     When processing data and the time jumps in the past with more than this number of seconds, the realtime filler will flush all the segments to disk and start from scratch. Default ``86400``.

numThreads (integer)
     The realtime filler will compress and flush the segments to disk in background. This option configures how many threads should be used for that operation. The default is the total number of CPUs of the system minus 1.

flushInterval (integer) seconds
     If no data is received for a parameter group in this number of seconds, then flush the data to the archive. If data is received regularely, it will be flushed when the segment is full (see maxSegmentSize above)

