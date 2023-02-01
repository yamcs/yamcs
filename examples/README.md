This folder includes a few example configurations of Yamcs.

To run one of these examples, use the `./run-example.sh` script from the upper
directory like this:

    ./run-example.sh EXAMPLE [options]

Where `EXAMPLE` is one of the subdirectories in here.

The following is a short description of each example:

## simulation
Basic TC/TM using a trivial simulation of a landing spacecraft. The simulator is connected via TCP links.


## ccsds-frames
The same simulator is connected via AOS and TC frames on UDP links. COP1 is used for command frame acknowledgment. TC frames can be optionally emebedded into CLTU. TM or USLP frames can be used instead of AOS frames.


## replication1, replication2 and replication3
Demonstrate the usage of replication between two or three instances. Into a real environment the instances should be deployed on different Yamcs servers. This is done for redundancy purposes or for security reasons (i.e. one server running in an restricted operational environment and the other one into DMZ accessed by external users).
The README file in each directory contains a description of the respective setup.

## cfdp & cfdp-udp
Demonstrates the usage of CCSDS File Delivery Protocol:
_cfdp_ uses the built-in simulator (which works with CFDP encapsulated in CCSDS packets), while _cfdp-udp_ expects an external UDP connection to transmit and receive raw CFDP PDUs. 

## pus
Simulator using PUS (Packet Utilization Standard - ECSS-E-ST-70-41C) also connected via TCP links. Please read the README inside to understand what services are simulated.

## perftest
This configuration is used to asses the performance of Yamcs for processing telemetry. The simulator sends a configurable number of packets with random content. On the Yamcs server side a MDB will be generated (by the PerfMdbLoader) to define all the packets and parameters within.
