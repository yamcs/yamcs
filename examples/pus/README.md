This example demonstrates the usage of the PUS (Packet Utilization Standard) with Yamcs. 

The mission database is encoded in XTCE and can be found in src/main/yamcs/mdb. There are currently three files:
- dt.xml - contains commonly used data types
- pus.xml, pusN.xml  - contain basic PUS TM, TC and algorithms definitions (N is the service number). These can be reused without many modifications (hopefully) in other projects.
- landing.xml - contains TM/TC definitions specific to the simulator. In a custom project this will be of course replaced with functionality specific to the target mission.

Below is a summary of how the different services are supported by Yamcs and what is simulated in the simulator.

ST[01] request verification

- Command Acceptance, Start and Completion reports are sent.
- The command VOLTAGE_ON can be used to test it:
    * voltage_num = 1 will send all three reports
    * voltage_num = 2 will skip the acceptance (as if the report packet was lost)
    * voltage_num = 3 will send a negative completion with a random code.
    * voltage_num = 4 will send a negative start.

ST[02] device access
- Standard Yamcs MDB definitions should suffice. 
- No support in the simulator.

ST[03] housekeeping
- Only static predefined HK is supported. 

ST[04] parameter statistics reporting
- Standard Yamcs MDB definitions should suffice. 
- No support in the simulator.

ST[05] event reporting 
- The PusEventDecoder service can generate events based on templates.
- The simulator sends periodic events which can be monitored in the Yamcs event page.

ST[06] memory management
- Standard Yamcs MDB definitions should suffice. 
- No support in the simulator.

ST[07] (reserved)

ST[08] function management
- Standard Yamcs MDB definitions should suffice. 
- No support in the simulator.

ST[09] time management
- The time sent by the simulator is obtained from System.nanoTime() and gives roughly the time since the computer has been started.
- A standard hardcoded drift is applied.
- Yamcs uses a time correlation service to correlate the simulator time with the "ground" time. 
  The time correlation does not know about the drift or how the time is generated.
- The time packet is sent every 4 seconds and changing that frequency is not supported.

ST[10] (reserved)

ST[11] time based scheduled. 
 - The Yamcs command post-processor generates the time based scheduled commands based on command attributes.
 - Most TC/TM supported in the simulator
 - A dedicated (web) UI application would be highly beneficial. (Anyone interested in sponsoring its development?)

ST[12] on-board monitoring
- Standard Yamcs MDB definitions should suffice. 
- No support in the simulator.

ST[13] large packet transfer
- Not supported. CFDP is preferred over this service.
- No support in the simulator.

ST[14] real-time forwarding control
- No special support in Yamcs. Not sure how this service is supposed to work and if it should have some influence on the command verification. 
- No support in the simulator.
 

ST[15]  on-board storage and retrieval 
- TODO: to show how LOS recorded packets can be inserted into the Yamcs archive with the correct timestamps.
- TODO: add support in the simulator

ST[16] (reserved) 


ST[17] test 
- Implemented with a container based verifier.
- Supported by the simulator

ST[18] on-board control procedures
- Standard Yamcs MDB definitions can probably be used for basic functionality. Having a dedicated (web) UI could probably help. 
- No support in the simulator.

ST[19] eventaction
- Not supported. Probably could be implemented similarily with ST[11]
- No support in the simulator.

ST[20] on-board parameter management 
- TBD if standard Yamcs MDB definitions are enough
- No support in the simulator.

ST[21] request sequencing
- Not supported. Probably could be implemented similarily with ST[11]
- No support in the simulator.

ST[22] position-based scheduling
- Not supported. Probably could be implemented similarily with ST[11]
- No support in the simulator.

ST[23] file management 
- TODO: to augument the existing CFDP support in Yamcs with functionality for showing the list of files on the remote system.
- TODO: add support in the simulator


