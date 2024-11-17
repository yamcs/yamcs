This service implements parameter retrieval. It stands behind the "/parameters" API endpoints.

It has been introduced in Yamcs 5.10.11. In order to not require modification of all existing configurations, the service is enabled automatically at startup. It can still be declared in order to change the default configuration.


The service combines retrieval from several sources:
- Parameter Archive - this stores efficiently parameter values for long durations.
  However the parameter archive is built by the back filler in segments and generally a segment cannot be used
  unless the full segment has been built and written to the database.  
- Replays - this means processing a stream of packets for extracting parameters. 
  For parameters not part of packets, a similar process is used, 
  entire rows from the pp table have to be streamed in order to extract the value of the required parameters.
  This process makes the replays more CPU intensive but the advantage is that up to date records can be retrieved.
- Parameter Cache - Yamcs can cache in memory the most recently received values of some parameters. However, as this consumes RAM,
  the number of samples which can be cached is limited.
- Realtime Parameter Archive filler - in certain cases when it is guaranteed that only new data is received (common case during 
  lab/flatsat/EGSE tests), the realtime filler can be used instead of the back filler. 
  The realtime filler works as a parameter cache as well (so it can return values from the segments that are being built),
  so the Parameter Cache is not required in this scenario.


Configuration Options
---------------------
parallelRetrievals (integer)
    Number of retrievals allowed to run concurrently. Default: 4.

procName (String)
    Name of te processor used for the realtime subscription of the parameter cache (if enabled);


Parameter Cache options
-----------------------

These options are under the `parameterCache` configuration.
enabled (boolean)
    If true, the parameter cache will be enabled. Default: enabled with the realtime parameter archive filler is not enabled.
            
cacheAll (boolean)
    If true, the cache will store all parameter value regardless if there is any user requesting them or not. If false, the values are added to the cache only for the parameters requested by a user. Once a parameter is added to the cache, its values are always cached. This option can be used to reduce the amount of memory used by the cache with the inconvenience that first time retrieving the values of one parameter will not have them in the cache. 
    Note that the option `subscribeAll` above is somehow similar - if that is set to false, then only some parameters will be available for cache even if this option is set to true. Default: false

duration (integer)
    How long in seconds the parameters should be kept in the cache. This value should be tuned according to the parameter archive consolidation interval. Default: 6000
    
maxNumEntries (integer)
   How many values should be kept in cache for one parameter. Default: 4096
