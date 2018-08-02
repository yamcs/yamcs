# yamcs-scpi

## context
This project is part of the Deam Chaser Mission Simulator (DCMS). Dream Chaser aims to develop a commercially reusable space plane for NASA.
The CDMS will be used for hardware test support, flight crew and controller mission training and real-time flight/mission support. 

## goal
This project implements the Test Support Equipment (TSE) Interface (IF) for CDMS. It will allow controlling and monitoring of various test equipment that are compliant to the Standard Commands for Programmable Instruments (SCPI; often pronounced "skippy") standard.
It will be integrated with YAMCS (Yet Another Mission Control System). YAMCS is the front- and back-end used to assist payload and system operators their daily tasks.

## Running
The project incorporates the maven exec plugin for easy development. The following command executes the main class after packaging:
```
mvn exec:java
```