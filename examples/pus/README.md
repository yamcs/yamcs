This example demonstrates the usage of the PUS (Packet Utilization Standard) with Yamcs. 

The mission database is encoded in XTCE and can be found in src/main/yamcs/mdb. There are currently three files:
- dt.xml - contains commonly used data types
- pus.xml - contains basic PUS TM, TC and algorithms definitions . These can be reused without many modifications (hopefully) in other projects.
- landing.xml - contains TM/TC definitions specific to the simulator. In a custom project this will be of course replaced with functionality specific to the target mission.

Simulated services are:

ST[01] Request verification

- Command Acceptance, Start and Completion reports are sent.
- The command VOLTAGE_ON can be used to test it:
    * voltage_num = 1 will send all three reports
    * voltage_num = 2 will skip the acceptance (as if the report packet was lost)
    * voltage_num = 3 will send a negative completion with a random code.
    * voltage_num = 4 will send a negative start.


ST[03] HK
- Only static predefined HK is supported. 


ST[09] Time Management
- The time sent by the simulator is obtained from System.nanoTime() and gives roughly the time since the computer has been started.
- A standard hardcoded drift is applied.
- Yamcs uses a time correlation service to correlate the simulator time with the "ground" time. 
  The time correlation does not know about the drift or how the time is generated.
- The time packet is sent every 4 seconds and changing that frequency is not supported.


TODO:

ST[05] - event reporting - an algorithm (customizable by the user) can be made to turn event packets into Yamcs events.

ST[06] - no special support planned in Yamcs but we have to verify that the way binary structures are communicated via PUS works with Yamcs.

ST[11] - time based schedule. To verify how time planned commands which have to contain the on-board time can be supported with Yamcs.

ST[15] - on-board storage and retrieval - to show how LOS recorded packets can be inserted into the Yamcs archive with the correct timestamps.

ST[23] - file management - to augument the existing CFDP support in Yamcs with functionality for showing the list of files on the remote system.
