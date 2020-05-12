Packet Pre-processor
====================

Yamcs generally uses the Mission Database to process telemetry packets. When data is received from external systems, there are two processing steps done as part of the Data Link which are outside the Mission Database definition:

1. Splitting a data stream into packets. This is done only for the links that receive data as a stream (e.g. TCP). For Data Links where input is naturally split into frames (e.g. UDP) this step is not necessary and not performed.
2. Pre-processing of packets in order to detect/correct errors and to retrieve basic information about the packets.



Stream Splitting
----------------

The data stream splitter is a java class that implements the :javadoc:`~org.yamcs.tctm.PacketInputStream` interface.

A generic splitter for binary streams is defined in :javadoc:`~org.yamcs.tctm.GenericPacketInputStream`. This class can split a stream based on a packet length that is encoded in a header. It requires all packets to have the length on the same number of bytes.


Packet pre-processing
---------------------

 The packet pre-processor is a java class that implements the :javadoc:`~org.yamcs.tctm.PacketPreprocessor` interface.
 
 It is responsible for error detection (and possibly correction) and extracting basic information required for further packet processing:
 * packet generation time - it represents the time when the packet has been generated on-board.
 * sequence count - a number used to distinguish two packets having the same timestamp.
 
 
 The generation time and sequence count are used as primary key in the tm table in the archive. That means they have to uniquely identify a packet; if the archive receives a new packet with the same (generation time, sequence count) as an existing packet in the archive, it will be considered a duplicate and discarded.
 
 The sequence count is used to distinguish two packets that have the same timestamp; it does not need to be incremental. For example the javadoc:`~org.yamcs.tctm.ISSPacketPreprocessor` uses the first 4 bytes of the CCSDS primary header (containing APID and CCSDS sequence count among others) as sequence count for the telemetry stream.
 
 Each mission has specific ways to encode information in the header but there are some standards supported to a certain extent by Yamcs:
 * Packet Utilisation Standard (PUS) from ESA - implemented in :javadoc:`~oorg.yamcs.tctm.pus.PusPacketPreprocessor`.
 * NASA cFS - implemented in  :javadoc:`~org.yamcs.tctm.cfs.CfsPacketPreprocessor`.
 
 **Generation time**
 
 A particular difficulty when writing a pre-processor is dealing with the generation time. Yamcs originated in the ISS (International Space Station) world where all the payloads and instruments are time synchronized to GPS and each packet sent to ground has a reliable timestamp. This is of course not true for all spacecrafts - most on-board computer have just an internal clock count which resets to 0 when the computer is restarted.
 
 The Yamcs archive needs the generation time for all its functions, not having it means that a large part of the functionality of Yamcs is not usable.
 
 There are different mechanisms to synchronize the on-board time with the ground:
 
 * Do not attempt to synchronize the time. The pre-processor can use local generation (computer) reception time as generation time. The on-board time will be still available as a parameter if defined in the MDB. This method is especially useful when using Yamcs as part of a test and check-out system, the system under test might be incomplete and have no (reliable) clock at all. The disadvantage is that when receiving data in non-realtime (e.g. recorded on board or in a ground station), it will not fit orderly in the archive.
 * Synchronize the on-board system to the ground each time it resets. This is the method employed by cFS - it allows setting a spacecraft time correction factor (STCF) on-board and that will make the on-board time correlated to the ground. 
 * Maintain a correlation factor on ground, his is the method specified by ESA PUS standard. In this case the packet pre-processor has to implement the time correlation. 
 
 Regardless of which method is used, it is important that the pre-processor does not generate packets with wrong timestamps - these might be difficult to locate and remove from the archive later. In order to not lose the packets, the pre-processor can set a flag ``invalid`` on a packet and the Data Link can be configured to store the invalid packets into a different archive table, using the reception time as a key. A custom made script can be made to retrieve those packets and fix their timestamps (or simply inspect their content).
 
 Starting with yamcs-4.11 there is a status bitfield in the telemetry packet table which allows the packet-preprocessor to flag packets that are timetamped with local time instead of spacecraft generation time.
 
 
 
