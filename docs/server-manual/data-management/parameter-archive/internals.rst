Parameter Archive Internals
===========================

The Parameter Archive stores for each parameter tuples (t\ :sub:`i`, ev\ :sub:`i`, rv\ :sub:`i`, ps\ :sub:`i`). In Yamcs the timestamp is 8 bytes long, the raw and engineering values are of usual types (signed/unsigned 32/64 integer, 32/64 floating point, string, boolean, binary) and the parameter status is a protobuf message.

In a typical space data stream there are many parameters that do not change very often (like an device ON/OFF status). For these, the space required to store the timestamp can greatly exceed in size the space required for storing the value (if simple compression is used).

In fact since the timestamps are 8 bytes long, they equal or exceed in size the parameter values almost in all cases, even for parameters that do change.

To reduce the size of the archive, some alternative parameter archives may choose to store only the values when they change with respect to the previous value. Often, like in the above "device ON/OFF" example, the exact timestamps of the non-changing parameter values, received in between actual (but rare) value changes are not very important. One has to take care that gaps in the data are not mistaken for non-changing parameter values.

Storing the values on change only will reduce the space required not only for the value but also (and more importantly) for the timestamp.

However, we know that more often than not parameters are not sampled individually but in packets or frames, and many (if not all) the parameters from one packet share the same timestamp.

Usually some of the parameters in these packets are counters or other things that do change with each sampling of the value. It follows that at least for storing those ever changing parameter values, one has to store the timestamps anyway.

This is why, in Yamcs we do not adopt the "store on change only" strategy but a different one: we store the timestamps in one record and make reference to that record from all the parameters sharing those same timestamps. Of course it wouldn't make any sense to reference one singe timestamp value, instead we store multiple values in a segment and reference the time segment from all value segments that are related to it.


Archive Structure
-----------------

We have established that the Yamcs Parameter Archive stores rows of data of shape:
(t, pv\ :sub:`0`, pv\ :sub:`1`, pv\ :sub:`2`,...,  pv\ :sub:`n`)

Where pv\ :sub:`0`, pv\ :sub:`1`, pv\ :sub:`2`..pv\ :sub:`n` are parameter values (for different parameters) all sharing the same timestamp t. One advantage of seeing the data this way is that we do keep together parameters extracted from the same packet (and having the same timestamp). It is sometimes useful for operators to know a specific parameter from which packet has been extracted (e.g. which APID, packet ID in a CCSDS packet structure).

The Parameter Archive partitions the data at two levels:

1. time partitioned in partitions of 2\ :sup:`31` milliseconds duration (~ 25 days). Each partition is stored in its own ColumnDataFamily in RocksDB (which means separate files and the possibility to remove an entire partition at a time).

2. Inside each partition, data is segmented in segments of 2\ :sup:`22` milliseconds (~ 70 minutes) duration. One data segment contains all the engineering values or raw values or parameter status for one parameter. A time segment contains all the corresponding timestamps.

This means that each parameter requires each ~70 minutes three segments for storing the raw, engineering and status plus a segment containing the timestamps. The timestamp segment is shared with other parameters. In order to be able to efficiently compress and work with the data, one segment stores data of one type only.

Each (parameter_fqn, eng_type, raw_type) combination is given an unique 4 bytes parameter_id (fqn = fully qualified name). We do this in order to be able to accommodate changes in parameter definitions in subsequent versions of the mission database.

The parameter_id ``0`` is reserved for the timestamp.

A ParameterGroup represents a list of parameter_id which share the same timestamp.

Each ParameterGroup is given a ParameterGroup_id


Column Families
---------------

For storing metadata we have 2 CFs:

meta_p2pid
    contains the mapping between the fully-qualified parameter name and parameter_id and type

meta_pgid2pg
    contains the mapping between ParameterGroup_id and parameter_id

For storing parameter values and timestamps we have 1CF per partition: :samp:`data_{partition_id}` where ``partition_id`` is basetimestamp (i.e. the start timestamp of the 2\ :sup:`31` long partitions) in hexadecimal (without 0x in front)

Inside the data partitions we store (key, value) records where:

key
    parameter_id, ParameterGroup_id, segment_start_time, type (the type = 0, 1 or 2 for the eng value, raw value or parameter status)

value
    ValueSegment or TimeSegment (if parameter_id = 0)

We can notice from this organization, that inside one partition, the segments containing data for one parameter follows in the RocksDB files in sequence of engvalue\ :sub:`segment_1`, rawvalue\ :sub:`segment_1`, parameterstatus\ :sub:`segment_1`, engvalue\ :sub:`segment_2`, rawvalue\ :sub:`segment_2`, ...


Segment Encoding
----------------

The segments are compressed in different ways depending on their types.

SortedTimeSegment
    Stores the timestamps as uint32 deltas from the beginning of the segment. The data is first encoded into deltas of deltas, then it's zigzag encoded (such that it becomes positive) and then it's encoded with FastPFOR and VarInt. FastPFOR encodes blocks of 128 bytes so VarInt encoding is used for the remaining data.

    Storing timestamps as deltas of deltas helps if the data is sampled at regular intervals (especially by a real-time system). In this case the encoded deltas of deltas become very close to 0 and that compresses very well.

    Description of the VarInt and zigzag encoding can be found in `Protocol Buffer docs <https://developers.google.com/protocol-buffers/docs/encoding>`_.

    Description and implementation of the FastPFOR algorithm can be found at `<https://github.com/lemire/JavaFastPFOR>`_.

IntSegment
    Stores int32 or uint32 encoded same way as the time segment.

FloatSegment
    Stores 32 bits floating point numbers encoded using the algorithm described in the `Facebook Gorilla paper <http://www.vldb.org/pvldb/vol8/p1816-teller.pdf>`_ (slightly modified to work on 32 bits).

ParameterStatusSegment, StringSegment and BinarySegment
    These are all stored either raw, as an enumeration, or run-length encoded, depending on which results in smaller compressed size.

DoubleSegment and LongSegment
    These are only stored as raw for the moment - compression remains to be implemented. For DoubleSegment we can employ the same approach like for 32 bits (since the original approach is in fact designed for compressing 64 bits floating point numbers).


Future Work
-----------

Segment Compression
    Compression for DoubleSegment and LongSegment. DoubleSegment is straightforward, for LongSegment one has to dig into the FastPFOR algorithm to understand how to change it for 64 bits.

Archive Filling
    It would be desirable to backfill only parts of the archive. Indeed, some ground generated data may not suffer necessarily of gaps and could be just realtime filled. Currently there is no possibility to specify what parts of the archive to be back-filled.
  
    Another useful feature would be to trigger the back filling automatically when gaps are filled in Yamcs database tables.
