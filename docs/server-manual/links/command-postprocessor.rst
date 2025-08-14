Command Postprocessor
=====================

Similar to the TM packet preprocessors, the command postprocessors are used to change the command before being sent out on the data link. The postprocessors are java classes that implement the :javadoc:`~org.yamcs.tctm.CommandPostprocessor` interface.

Typical tasks performed by the postprocessors are:
 
* assigning a sequence count (e.g. the CCSDS sequence counts are assigned per APID)
* computing and appending a checksum or CRC
