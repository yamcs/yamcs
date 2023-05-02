Command Post-Processor
======================

Similar to the TM packet pre-processors, the command post-processors are used to change the command before being sent out on the data link. The post-processors are java classes that implement the :javadoc:`~org.yamcs.tctm.CommandPostprocessor` interface.

Typical tasks performed by the post-processors are:
 
* assigning a sequence count (e.g. the CCSDS sequence counts are assigned per APID)
* computing and appending a checksum or CRC
