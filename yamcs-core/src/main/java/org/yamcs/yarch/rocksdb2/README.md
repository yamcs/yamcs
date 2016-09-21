This is the storage engine rocksdb2.

The difference between this and rocksdb is the way partitions are managed:
 - in rocksdb each partition is a Column Family (CF).
 - in rocksdb2 there is one column family (the default one) and the partitions are encoded in front of the key. That means that for rocksdb2 we can only have partitions on columns of fixed size (e.g. int, enum, byte, etc but not string, binary, protobuf).
 
 The reason for trying out the different partitioning scheme is because we noticed that the rocksdb LSM merging doens't really work well when data is mostly sorted (which is the case of mostly real-time telemetry reception where our primary key is the time, and even more so when doing large data imports). That's why we end up with lots of small sst files that are never re-written, creating also a filesystem fragmentation problem. 
The only option to create bigger files is to set the write buffer size to a large value; in this case rocksdb will accumulate data in memory up to the specified size and then it will dump it into an sst file. This is also not optimal - for partitions that get few data (like on-event packets) there will be lots of buffers in memory doing nothing. 
 
 
However, in rocksdb2 when we put the partitioning key in front of the normal table key (i.e generation time), then the data is not anymore sorted (assuming that we have at least two partitions), so the data is nicely distributed across the sst files with the merging taking place when the files grow large, etc.
 

Note: there is no re-implementation of the HistogramDB, same one as for the rocksdb is used. 