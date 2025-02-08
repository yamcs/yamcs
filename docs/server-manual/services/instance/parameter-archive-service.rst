Parameter Archive Service
=========================

The Parameter Archive stores time ordered parameter values. The parameter archive is column-oriented and is optimized for accessing a (relatively small) number of parameters over longer periods of time. Data is stored in fixed duration time intervals, each interval covering a length of :math:`2^{23}` milliseconds (~139 minutes). 

An interval has always to be processed or reprocessed in full - this means if one data point is added in the interval, the full 139 minutes of data have to be reprocessed.

Intervals are further split into segments such that each segment cannot contain more than a configurable maximum number of samples. This is done in order to limit the number of samples stored in memory when rebuilding an interval. 
A parameter that comes at high frequency will be split into multiple segments whereas for one that comes at low frequency there will be only one segment in each interval.

The parameters are grouped such that the samples of all parameters from one group have the same timestamp. For example all parameters extracted from one TM packet have usually the same timestamp and are part of the same group. A special case is the aggregate parameters: these are decomposed into the individual members if scalar types but all values are belonging to the same group and thus the aggregate can be rebuilt even though the members are stored separately.

Filling the parameter archive.
-----------------------------

Generating the parameter archive has to be done in batches since it is not possible to write individual data points (i.e. a parameter value at one specific time).
Generally, the data has to come from a processor (either realtime or replay).

There are three mechanisms implemented:
- the realtime filler monitors the realtime processor and builds in memory parts of the archive which are then written to the archive when the segments are full.
- the backfiller builds parts of the archive from the past. It can monitor incoming (dump) tm or parameter streams and start filling processes based on the data that is coming on those streams. It can also run periodically independent of any incoming data.
- finally, the API can be used to rebuild parts of the archive. 



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
            automaticBackfilling: true
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

Starting with Yamcs 5.11.1, it is possible to specify better how the archive should be rebuild based on monitoring stream data (tm packets and parameters):

.. code-block:: yaml

    services:
      - class: org.yamcs.parameterarchive.ParameterArchive
        args:
          realtimeFiller:
            enabled: false
          backFiller:
               streamUpdateFillPolicy:
                 - dataAge: 168.0  # Disable the automatic rebuild (manual rebuild required) of data older than 7 days
                   fillFrequency: -1 
                   quietThreshold: -1
                 
                 - dataAge: 2.0  # Applies to data older than 2 hours but newer than 7 days
                   quietThreshold: 60   # Trigger a rebuild if no data arrives for 1 minute
                   fillFrequency: 3600   # Trigger a rebuild every hour even if the 1 min threshold above does not trigger a rebuild

                 - dataAge: 0   # Applies to new data not older than 2 hours (but it does not apply to data coming in the 'future')
                   quietThreshold: 10   # Trigger a rebuild if no new data is received for 10 seconds
                   fillFrequency: 600   # Fill every 10 minutes, even if the 10 sec threshold above does not trigger a rebuild

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
    
coverageEndDelta (integer)
    Number of seconds in the future, relative to the mission time, considered for the parameter archive coverage end. Any data falling beyond this, it is not considered.a
    The coverage end should normally be in the past and it is used when retrieving parameters - if parameters fall before the coverage end, then the parameter retrieval service
     will attempt retrieval fromt he parameter archive and will not try to retrieve the parameter via other means (cache or replay).
     The reason for implementing this delta is to avoid adding by mistake some data in the far future causing the parameter retrieval to never use the cache (because theoretically all data is covered by the parameter archive)	
    
     Default: ``60`` (one minute)   
     
    
    

Backfiller Options
------------------

These options appear under the ``backFiller`` key.


warmupTime (integer)
     When the backfiller performs a replay to fill a data interval, the replay will start this number of seconds before the interval start. This is sometimes required for algorithms that aggregate data, to be able to have all the input data necessary to produce the output. Default: ``60``
     
automaticBackfilling  (boolean)
     If true the backfiller executes backfilling operations according to the schedule or the streamUpdateFillPolicy. 
     Default: ``true`` if the realtime filler is disabled and ``false`` if the realtime filler is enabled.
     The automatic backfilling can be enabled/disabled at runtime via an API call.
     
monitorStreams (string[])
     The list of tm or parameter streams that will be monitored to check for new data. If the list is empty, no stream will be monitored and the archive will be rebuilt according to the
     schedule defined below.
     Default: all the tm and param streams defined in the :file:`etc/yamcs.{instance}.yaml` streamConfig section. The backfiller will check the generation time of the packet or parameter received on the monitoring streams and will mark that interval as ``dirty``. 
     As soon as the ``quietPeriodThreshold`` is reached or the ``streamUpdateFillFrequency`` timer (see below) expires, a new filling task is started for that interval.
     
streamUpdateFillFrequency (integer)
     Valid if the ``monitorStreams`` is not empty, configures how often in seconds the fillup based on the stream monitoring is started. The fillup only starts if new data has been received on the streams. The time applies from the last time the filler ran.
     Default ``3600``.

     Starting with Yamcs 5.11.1, this option is deprecated in favour of the streamUpdateFillPolicy below.
     Internally it is replaced with streamUpdateFillPolicy: [{dataAge: -1, fillFrequency: 600, quietThreshold: -1}] which is the behaviour in the previous Yamcs versions.

streamUpdateFillPolicy (list of maps)
    This policy applies when monitorStreams is not empty. It determines how often the archive is updated based on incoming stream data. A fill operation only occurs 
    when new data is received. The list contains multiple entries, each specifying update behavior for a different data age.
    
    Each entry in the list has the following keys:
     
     dataAge (float)
        **Required** Specifies the number of hours in the past this entry applies to. This determines which quietThreshold and fillFrequency settings are used:
        * Helps reduce rebuild frequency for older data.
        * Computed as: mission time - data timestamp.
        * If data is received in the future (relative to mission time), the age is negative. In such cases, add an entry with a negative dataAge if the archive should be rebuilt.

     fillFrequency (integer)
        Determines how often (in seconds) the archive is updated when new data arrives. A negative value disables periodic updates.
        Default ``3600``. 
     
     quietThreshold: (integer)
        Specifies how long (in seconds) streams must be inactive before triggering an immediate rebuild. It helps react quickly to data inactivity instead of waiting for fillFrequency.
        A negative value disables stream quietness monitoring, the fillFrequency above will be used to trigger periodic rebuilds.  
        Default: ``60``

     Disabling both fillFrequency and quietThreshold will make the filler ignore data older than the ``dataAge`` (manual rebuilding the archive is still possible).
     
     The different entries are sorted in increasing order of `dataAge` and for each tuple received on one of the monitoring streams, the last entry with the ``dataAge`` less than 
     or equal to ``tupleAge`` where ``tupleAge = (mission time - tuple time)``, will apply. If no entry meets this condition, the tuple will be ignored.
     
     The default policy is  [{dataAge: -1, fillFrequency: 600, quietThreshold: 60}, {dataAge: 2, fillFrequency: -1, quietThreshold: 60}].
     This means that data that is newer than 2 hours and up to one hour in the future causes the archive to be rebuilt every 10 minutes or 10 seconds after no data is received 
     (unlikely since Yamcs always generates some parameters), 
     and data that is older than 2 hours causes the archive to be rebuild as soon as no data is received for one minute. 
      

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
    
    Default value: -1
    

Realtime filler Options
-----------------------
   
enabled  (boolean)
     If true the realtime filler is enabled. Default: ``true``
 
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

