Parameter archive on top of RocksDB.

== Design considerations ==
The parameter archive has in principle to store for each parameter pairs of (ti, vi) where:
ti - is a timestamp when the sample has been taken
vi - is the value of the parameter at that timestamp.

We can notice that for many parameters, that do not change very often (like an ON/OFF status),
the space required to store the timestamp can greatly exceed in size the space required for storing the value (if simple compression is used).
In fact since the timestamps are 8 bytes long, they are almost in all cases bigger than the parameter values.

Some parameter archives store the values just when they change.
It could be considered than in between the changes the exact timestamps are not very important. Of course, one has always to take care
that gaps in the data are not mistaken for non-changing parameter values.

However, in the space domain (and other domains as well), parameters are not sent individually but in packets, and all the parameters from one packet share the same timestamp.
Usually some of those parameters will be some counters or other things that change at each sample. It follows that at least for storing those values, one has to store the timestamp anyway.
 
So what we do is to store once the timestamps in one record and make reference to that record from all the parameters sharing those timestamps.

===============
The parameter archive stores tuples of shape:
(t, pv0, pv1, pv2,...)

Where pv0, pv1.. are parameter values all having the timestamp t.


 


== Database structure ==
Data is time partitioned in partitions of 2^31 milliseconds duration (=~ 25 days). Each partition is stored in its own ColumnDataFamily in RocksDB (which means separate files)

Inside each partition, data is segmented in segments of 2^22 miliseconds (=~ 70 minutes) duration.
One data segment contains all the values of the parameter and one time segment contains all the corresponding timestamps.

Each (parameter, type) combination is given an unique 4 bytes parameter_id. The parameter_id=0 is reserved for the timestamp.

 
ParameterGroup - represents a list of parameter_id which are received together (share the same timestamp).
Each ParameterGroup is given a ParameterGroup_id


TimeSegment - 
byte 0:
  version = 0
byte 1+: an ordered sequence of varints:
  n = array size
  t0 = time zero - a delta from the beginning of the segment
  dt1 = t1-t0
  dt2 = t2-t1
  ...



ParameterValueArray:
byte 0: 
  version = 0
byte 1:
  type (integer, float, enumeration, etc)

ParameterIdList: SortedVarIntList





Column Families
 for storing metadata we have 2 CFs:  
   meta_p2pid:  contains the mapping between parameter fully qualified name and parameter_id and type
   meta_pgid2pg: contains the mapping between ParameterGroup_id and parameter_id
 
 for storing parameter values and timestamps we have 2CFs per partition:
    data_<partition_id> contains parameter values
      key: parameter_id, ParameterGroup_id, segment_time
      value: ValueSegment or TimeSegment (if parameter_id =0)
 
   

partition_id is basetimestamp in hexadecimal (without 0x in front)



