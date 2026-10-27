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
   Issue any command with the `pus11ScheduleAt` option set and it is wrapped into a TC[11,4] insert
   activities request addressed to `pus11Apid`. The `pus11SubScheduleId` and `pus11GroupId` options select the
   sub-schedule and the scheduling group; their defaults and field widths are configured in the `pus11` block of
   the command post-processor (see `etc/yamcs.pus.yaml`).
 - Most TC/TM supported in the simulator, including sub-schedules (TC[11,18..21]) and scheduling
   groups (TC[11,22..26], TM[11,27]).
 - The simulator's time-based schedule execution function starts disabled (as per the standard): send `/PUS11/ENABLE_SCHEDULER` before scheduling commands, otherwise they are dropped at their release time.
 - Scheduling groups only exist after an explicit TC[11,22] (`/PUS11/CREATE_SCHEDULING_GROUPS`): an insert referencing an unknown group is rejected. A sub-schedule created by an insert starts disabled (TC[11,20] enables it).
 - A dedicated (web) UI application would be highly beneficial. (Anyone interested in sponsoring its development?)
 - Alternatively the TC[11,4] can be built by hand with `/PUS11/INSERT_ACTIVITIES`. It takes a list of
   activities, each with a `group_id`, a `release_time` and a `tc_packet`: the complete TC packet to
   release, in hex. See "Embedded TC packets" below. `pus-frames` has its own `pus11.xml` where the
   member is named `tc`; try it with `examples/pus-frames/tests/test-pus11-embedded.py`.

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

ST[19] event-action
- `mdb/pus19.xml` defines the commands, loaded in both `pus` and `pus-frames`. A definition is
  identified by (`app_process_id`, `event_def_id`); the simulator has a single application process
  (APID 1).
    * TC[19,1] `TC_19_1_ADD`: a list of {`app_process_id`, `event_def_id`, `request`}, where
      `request` is the complete TC packet to execute, in hex, with no length prefix (ECSS 8.19.2.1).
      See "Embedded TC packets" below.
    * TC[19,2] delete, TC[19,4]/[19,5] enable/disable (`_SPECIFIC` with a list, `_ALL` with no
      payload). TC[19,3] (delete all) is not implemented; TC[19,2] covers ECSS 6.19.8.2.
    * TC[19,6] report status, answered by TM[19,7].
    * TC[19,8..9] enable and disable the event-action function.
    * TC[19,10] report definitions (N=0 for all), answered by TM[19,11].
- The simulator supports these TCs. It runs the request of an enabled definition each time the
  matching ST[5] event is generated (EVENT_1 every 5 seconds, EVENT_2 in the other seconds),
  provided the function is enabled. New definitions and the function start disabled.
- Instruction checks follow ECSS 6.19.8: adding over an enabled definition, exceeding 16
  definitions, deleting an unknown or enabled definition and enabling/disabling an unknown one are
  rejected per instruction with a failed start of execution (TM[1,4]); the valid instructions of
  the same request are still executed. Re-adding a disabled definition replaces its request.
- Try it with `examples/pus-frames/tests/test-pus19-embedded.py`.

Embedded TC packets (used by ST[11], ST[19], and possibly ST[21] and ST[22])
- Several requests carry complete TC packets, to be executed later on board: TC[11,4]
  (time-based schedule), TC[22,4] (position-based schedule), TC[19,1] (event-action) and
  TC[21,1] (request sequence).
- Mark such an argument by putting the `Yamcs:EmbeddedTc` annotation on its binary type. It is
  defined once in `mdb/pus.xml` as `/PUS/EmbeddedTcType`:
  ```xml
  <BinaryArgumentType name="EmbeddedTcType">
      <AncillaryDataSet><AncillaryData name="Yamcs:EmbeddedTc"/></AncillaryDataSet>
      <BinaryDataEncoding><SizeInBits><FixedValue>-1</FixedValue></SizeInBits></BinaryDataEncoding>
  </BinaryArgumentType>
  ```
- For each argument value with this annotation, the `PusCommandPostprocessor`:
    * fills in the CCSDS length and sequence count of the embedded packet. It also works when
      the type is used inside a list, e.g. `activities[1].tc_packet`. The packets are handled in the
      order they appear in the command, so they get increasing sequence counts.
    * appends the packet's CRC, if the post-processor option `embeddedTcCrc` is set. The option
      defaults to true when `errorDetection` is configured; `pus-frames` sets it explicitly.
- The outer command then gets its own sequence count and CRC as usual.
- So the embedded packet can be entered as the binary of a dry run of the command: zero
  sequence count and length, no CRC. Both test scripts do exactly that:
    * call `processor.issue_command(name, args, dry_run=True).binary`
    * pass the result, in hex, as the `tc` / `request` argument
- The assigned sequence counts appear in the command history as
  `ccsds-seqcount:<argument path>`, e.g. `ccsds-seqcount:activities[0].tc_packet`.
- The simulator logs each received embedded packet with its sequence count and whether its CRC
  is valid.
- Note that an embedded packet keeps the sequence count it got when it was loaded. Its execution
  reports, possibly repeated in the case of ST[19], carry that sequence count.
- The SCOS-2000 MIB loader (yamcs-scos2k) puts the same annotation on the (PTC, PFC) = (12, 1)
  command parameters.
- See the PUS command post-processor documentation for the limitations, e.g. the encoding has to
  allow the size to grow when a CRC is appended.

ST[20] on-board parameter management 
- TBD if standard Yamcs MDB definitions are enough
- No support in the simulator.

ST[21] request sequencing
- Not supported. Probably could be implemented similarily with ST[11]
- No support in the simulator.

ST[22] position-based scheduling
 - Try it with `examples/pus/tests/test-pus22.py`.
 - Structurally the same implementation as ST[11] (see above), keyed on a position tag
   (`orbit_number`, `orbit_angle`) instead of a time. Issue any command with the
   `pus22OrbitNumber` and `pus22OrbitAngle` (degrees) options set and it is wrapped into a
   TC[22,4] insert activities request; `pus22SubScheduleId`/`pus22GroupId` select the
   sub-schedule/group, configured the same way as their `pus11` counterparts.
 - Sub-schedules, scheduling groups (TC[22,18..21], TC[22,22..26]/TM[22,27]), and `TC[22,28]`
   "set the orbit number" (applies at the next orbit wrap, ECSS 6.22.6.4) are all supported by
   the simulator.
 - The simulator's "orbit" is a simple synthetic clock: `orbit_angle` sweeps 0..360 degrees
   uniformly over a fixed 90 minute period, `orbit_number` increments
   on each wrap. It is exposed as telemetry (`OrbitNumber`/`OrbitAngle`, `hkid=6`) so you can see
   where a scheduled activity's target position is relative to the current one.
 - Persistent scheduling (ECSS 6.22.5) is not supported - every activity is one-shot, like ST[11].

ST[23] file management 
- TODO: to augument the existing CFDP support in Yamcs with functionality for showing the list of files on the remote system.
- TODO: add support in the simulator


