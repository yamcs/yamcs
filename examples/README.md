This folder includes a few example configurations of Yamcs.

To run one of these examples, use the `./run-example.sh` script from the upper
directory like this:

    ./run-example.sh EXAMPLE [options]

Where `EXAMPLE` is one of the subdirectories in here.

The following is a short description of each example:
* simulation - basic TC/TM using a trivial simulation of a landing spacecraft. The simulator is connected via TCP links.
* ccsds-frames - the same simulator is connected via AOS and TC frames on UDP links. COP1 is used for command frame acknowledgment. TC frames can be optionally emebedded into CLTU. TM or USLP frames can be used instead of AOS frames.
* replication1 - demonstrates the usage of replication between two instances. Into a real environment the two instances should be deployed on two different Yamcs servers. This is done for redundancy purposes or for security reasons (i.e. one server running in an restricted operational environment and the other one into DMZ accessed by external users).

* cfdp - demonstrates the usage of CCSDS File Delivery Protocol
