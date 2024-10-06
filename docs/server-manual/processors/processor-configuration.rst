Processor Configuration
=======================

The configuration of the different processor types can be found in :file:`etc/processor.yaml`. The file defines a map whose keys are the processor types. The type is used to define a specific configuration used when creating the processor. In addition to its type, each processor has a unique name specified at the moment of creation.

The Yamcs processors are created in various ways:

* at startup by the :doc:`../services/instance/processor-creator-service`. This is how typically the realtime processor is started. Note that here "realtime" is both the type and the name of the processor.
* by asking for archive data via the API with `dataSource = replay`. This will create a processor of type ``ArchiveRetrieval``.
* the :doc:`../services/instance/parameter-archive-service` creates regularly processors of type "ParameterArchive" to build up the parameter archive.
* new processors of any type can be created via API. Yamcs Studio and Yamcs Web make use of this functionality to perform replays of data from the archive and they create processors of type "Archive".

Note that the types ``Archive``, ``ParameterArchive`` and ``ArchiveRetrieval`` are often hardcoded in the services that use those processor types so it is advisable not to change them in the :file:`etc/processor.yaml`. The user can define additional processor types for implementing custom functionality. 

One current restriction is that all instances share the same processor types. It is not possible for example that the ParameterArchive processor type behaves differently in two different instances of the same Yamcs server.

Example of the ``realtime`` processor type configuration:

.. code-block:: yaml

  realtime:
      services:
        - class: org.yamcs.StreamTmPacketProvider
        ...
      config:
          subscribeAll: true
          recordInitialValues: true
          recordInitialValues: true
          persistParameters: true
          maxTcSize: 4096
          
          alarm:
              parameterCheck: true
              parameterServer: enabled
              eventServer: enabled
              eventAlarmMinViolations: 1
              loadDays: 30
          
          parameterCache:
              enabled: true
              cacheAll: true
              duration: 600
              maxNumEntries: 4096
          
          tmProcessor:
              ignoreOutOfContainerEntries: false
              expirationTolerance: 1.9
          

Options
-------

services (list)
    A list of services that are started together with the processor. The list is similar with the list of services used in the instance definitions. The reason is that originally (Yamcs v1) there were no instances but only processors and the data links were connected directly to them.
    The different available services are described in the subsequent chapters after this one.

The other options are under the `config` key:

subscribeAll (boolean)
    If true, all the services that provide parameters will provide all parameters starting at the processor creation. If set to false, the parameter are requested (subscribed) only when the external user asks for them (for example when opening a display, Yamcs Studio will subscribe to all parameters that are in the display). One service which can benefit of this is the XTCE TM processor: sometimes it is possible to extract only a selected list of parameters from packets and skip altogether the packets for which no parameter is requested. The advantage is that there is less work to perform; the disadvantage is that no value is available when subscribing a parameter for the first time (e.g. when opening a display for the first time, there will be no value shown until a packet containing the parameters on the display will have arrived).
    
    The providers are free to ignore this option and to provide more parameters than subscribed. This is for example the case for the XTCE TM processor when extracting parameters from a packet where the position of the entries is not absolute but relative to a previous entry. In this case the only way to extract a parameter in the middle or end of the packet is to extract all the parameters appearing in front.
    
recordInitialValues
    The Mission database can contain initial (default) values for parameters. Enabling this option will cause an archive entry to be created at processor start with the values for all these parameters.
    
recordLocalValues
    Local parameters are those known inside Yamcs and not provided by an external system. They are set by users via API calls. This option allows to record the values for these parameters each time they change.
    
maxTcSize (integer)
    The maximum size of a telecommand packet. This value will set the maximum value regardless of the command definition in the Mission Database. There can be commands which have variable size arguments that do not specify a maximum size; this option will practically limit those cases to an overall maximum.

subscribeContainerArchivePartitions (boolean)
    If set to true (default) the containers declared to be used as archive partition are subscribed by default in the processor. Otherwise the containers are only subscribed when a user subscribes to them or to a parameter contained in them. If alarms are enabled, the subscription to the parameters that can trigger alarms will also cause some container subscriptions.
    The only reason to switch this option off is for improving the performance when doing a archive retrieval that only extracts a few parameters. It is thus advisable to only configure it for the ArchiveRetrieval processor type.
    Note: the statistics shown on the yamcs-web instance home page contain the containers subscribed inside the currently selected processor. If no container is subscribed, only the root containers will be shown.

persistParameters (boolean)
    If set to true, save the value of the parameters when the processor is closed and restore them when a processor with the same name starts. Only the parameters with the persistence flag set will be saved. By default in XTCE all parameters are set as persistent whereas in the spreadsheet the persistance has to be enabled by specifying the "p" flag.
    This is typically set to true for the realtime processor such that the values of the parameters are saved when Yamcs is shut down and restored when Yamcs starts up again.
    Default: false

    
Alarm options 
-------------

These options are defined under config -> alarm.

parameterCheck (boolean)
    If set to true, the parameters will be checked against the Mission Database defined limits. The users will receive the limit information as part of the parameter status. For example Yamcs Studio displays these parameters with a red or yellow border, depending on the severity of the limit. If set to false the limits will be ignored and all parameters will have the status unmonitored (equivalent with having no limit defined in the Mission Database).

parameterServer (string)
    Can be enabled or disabled. If enabled, an alarm server managing the alarm status of parameters will be started as part of the processor. This option requires the parameterCheck to be enabled. If disabled but the parameterCheck set to true, the parameters will still have their out of limit status associated but there will be no alarms generated.

eventServer (string)
    Can be enabled or disabled. If enabled, an alarm server managing the alarm status of events will be started as part of the processor. This works similarly with the alarms for parameters - the severity of the event is used to derive the severity of the alarm. However because the events do not have a definition similar with the parameters in the Mission Database, the event source/type is used as a key for the alarm. That means that if a second event with the same source,type is being received as one that has already triggered an alarm, it is considered another occurrence of the same alarm. 

eventAlarmMinViolations (integer)
    The number of occurrences of a specific event (identified by its source and type) required to raise an alarm. By default it is 1. Note that the parameters do not have this setting because it is part of the Mission Database definition.

loadDays (float)
    Specifies the number of days of past alarms to load at Yamcs startup. If the value is zero or negative, no alarms will be loaded.
    This option has been introduced in Yamcs version 5.9.9 and 10.1.2. In earlier versions, triggered alarms were not reloaded into the alarm server during Yamcs startup.
    Default: 30

Parameter Cache options
-----------------------

These options are defined under the config -> parameterCache.

The processors can make use optionally of a parameter cache that stores the last values of parameters. The cache is used by Yamcs web to plot parameters which are not yet in the Parameter Archive.

Note that regardless of this cache there is always a last value cache which holds only the last known value for each parameter. The last value cache cannot be disabled. 

The parameter cache can cause huge amounts of memory (RAM) to be consumed. The current implementation :javadoc:`org.yamcs.parameter.ArrayParameterCache` tries to minimize the memory requirement by using arrays of primitive values instead of java objects but even then, the memory consumed can be significant. Updating the cache is also quite CPU intensive.

enabled (boolean)
    If true, the parameter cache will be enabled.
            
cacheAll (boolean)
    If true, the cache will store all parameter value regardless if there is any user requesting them or not. If false, the values are added to the cache only for the parameters requested by a user. Once a parameter is added to the cache, its values are always cached. This option can be used to reduce the amount of memory used by the cache with the inconvenience that first time retrieving the values of one parameter will not have them in the cache. 
    Note that the option `subscribeAll` above is somehow similar - if that is set to false, then only some parameters will be available for cache even if this option is set to true.

duration: 600
    How long in seconds the parameters should be kept in the cache. This value should be tuned according to the parameter archive consolidation interval.
    
maxNumEntries: 4096
   How many values should be kept in cache for one parameter.


TM (container) processing options
---------------------------------

These options are defined under the config -> tmProcessor.

ignoreOutOfContainerEntries (boolean)
    If set to false (default), when processing a TM packet, parameters whose position falls outside of the packet, will generate a warning. This option can be used to turn off that warning. Usually it is a sign of an ill-defined Mission Database and it is better to fix the Mission Database than setting this option.
    
.. _expirationTolerance:

expirationTolerance (double)
    The Mission Database can define an expected rate in stream for packets (containers). This signifies how often a packet is expected to be sent by the remote system. The rate in stream property will cause Yamcs to set an expiration time for the parameters extracted from that packet. The expiration of parameters is used to warn the operators that they are potentially looking at stale data in the displays.
    
    Yamcs will compute the expiration time as the rate in stream defined in the Mission Database multiplied by this configuration option. The tolerance is needed in order to avoid generating false expiration warnings.

maxArraySize (integer)
    The maximum size of arrays extracted from TM packets. The arrays can be dynamically sized (meaning the size is given by a parameter in the packet) and this option configures the maximum size allowed. Default: ``10000``.
