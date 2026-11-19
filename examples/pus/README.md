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
- The simulator on-board time is a real Unix-epoch time (host wall clock) with a small hardcoded
  drift applied. Yamcs decodes it directly (`timeEncoding: {type: CUC, epoch: UNIX}`); this example
  does not use a time correlation service. See the `pus-frames` example for a setup where the
  on-board time is correlated against the frame earth-reception time.
- The time packet is sent every 4 seconds and changing that frequency is not supported.

ST[10] (reserved)

ST[11] time based scheduled.
 - **Not wired up in this example** - see the `pus-frames` example, which demonstrates ST[11]
   instead of ST[22] (see below). The simulator itself supports both regardless of which example
   you run; the two examples were deliberately split one-service-each so their command options
   (`pus11*` / `pus22*`) don't get interleaved in the same instance's command form (Yamcs does not
   currently group/sort command options).
 - The Yamcs command post-processor generates the time based scheduled commands based on command attributes.
   Issue any command with the `pus11ScheduleAt` option set and it is wrapped into a TC[11,4] insert
   activities request. The `pus11SubScheduleId` and `pus11GroupId` options select the sub-schedule and
   the scheduling group; their defaults and field widths are configured in the `pus11` block of the
   command post-processor (see `pus-frames/etc/yamcs.pus-frames.yaml`).
 - Most TC/TM supported in the simulator, including sub-schedules (TC[11,18..21]) and scheduling
   groups (TC[11,22..26], TM[11,27]).
 - Alternatively the TC[11,4] can be built by hand with `/PUS11/INSERT_ACTIVITIES` (wired in
   `pus-frames`). It takes a list of activities, each with a `group_id`, a `release_time` and a
   `tc`: the complete TC packet to release, in hex. See "Embedded TC packets" below. Try it with
   `examples/pus-frames/tests/test-pus11-embedded.py`.

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
- `mdb/pus19.xml` defines the commands, loaded in both `pus` and `pus-frames`:
    * TC[19,1] `ADD_EVENT_ACTIONS`: a list of {`event_id`, `tc`}, where `tc` is the complete TC
      packet to execute, in hex. See "Embedded TC packets" below.
    * TC[19,2..5] delete, delete all, enable and disable definitions.
    * TC[19,6] report status, answered by TM[19,7].
    * TC[19,8..9] enable and disable the event-action function.
- The simulator supports these TCs. It runs the action of an enabled definition each time the
  matching ST[5] event is generated (EVENT_1 every 5 seconds, EVENT_2 in the other seconds),
  provided the function is enabled.
- Simplifications: a definition is identified by the event id only (no application process id),
  so there is one action per event, and new definitions start disabled.
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
      the type is used inside a list, e.g. `activities[1].tc`. The packets are handled in the
      order they appear in the command, so they get increasing sequence counts.
    * appends the packet's CRC, if the post-processor option `embeddedTcCrc` is set. The option
      defaults to true when `errorDetection` is configured; `pus-frames` sets it explicitly.
- The outer command then gets its own sequence count and CRC as usual.
- So the embedded packet can be entered as the binary of a dry run of the command: zero
  sequence count and length, no CRC. Both test scripts do exactly that:
    * call `processor.issue_command(name, args, dry_run=True).binary`
    * pass the result, in hex, as the `tc` argument
- The assigned sequence counts appear in the command history as
  `ccsds-seqcount:<argument path>`, e.g. `ccsds-seqcount:activities[0].tc`.
- The simulator logs each received embedded packet with its sequence count and whether its CRC
  is valid.
- Note that an embedded packet keeps the sequence count it got when it was loaded. Its execution
  reports, possibly repeated in the case of ST[19], carry that sequence count.
- The SCOS-2000 MIB loader (yamcs-scos2k) puts the same annotation on the (PTC, PFC) = (12, 1)
  command parameters.
- See the PUS command post-processor documentation for the limitations, e.g. the encoding has to
  allow the size to grow when a CRC is appended.

ST[20] on-board parameter management

- `mdb/pus20.xml` is generated by `mdb/pus20.py` (PyMDB). It defines:
    * TC[20,1] `REPORT_PARAMETER_VALUES`: `num_parameters` and the array `parameter_ids`.
    * TM[20,2] `PARAMETER_VALUE_REPORT`: N, then N x (parameter ID, value).
    * TC[20,3] `SET_PARAMETER_VALUES`: see "Bulk updates" below.
- Not supported: the management of parameter definitions, TC[20,4], TC[20,5], TC[20,6], TM[20,7].
- The on-board parameters are the simulator parameters in
  `/SIMULATOR/ONBOARD/`, defined in `mdb/landing.xml`, each with an invented
  description of what it means on a spacecraft. Their data source is
  `external1`, so they are visible in the web interface but have no value
  initially (the onboard parameter data source is configurable in the
  PusParameterManager and defaults to `external1`). Each has an alias in the
  namespace `PUS20_Parameter_ID` equal to the label of the parameter ID
  enumeration in `pus20.py`. This is how Yamcs maps the parameter name to its
  PUS20 ID.
  The value of TM[20,2] and TC[20,3] has a size that depends on the parameter
  ("deduced" in the standard): it comes from the type of the parameter in the
  MDB. Hence, for the simulator, keep `landing.xml`, `pus20.py` and
  `Pus20Service` in sync:

  | ID   | Parameter             | Type             | Unit   | Initial value on board   | Meaning                                                                             |
  | ---- | -----------           | ------           | ------ | ------------------------ | ---------                                                                           |
  | 1    | HkReportInterval      | uint16           | ms     | 1000                     | Period of the on-board housekeeping reports (ST[3])                                 |
  | 2    | BatteryHeaterSetpoint | float32          | degC   | 5.0                      | Target temperature of the battery heater thermostat                                 |
  | 3    | AdcsMode              | enumerated uint8 |        | SUN_POINTING (2)         | Attitude control mode: SAFE (0), DETUMBLE (1), SUN_POINTING (2), NADIR_POINTING (3) |
  | 4    | TmDownlinkRate        | uint32           | bit/s  | 64000                    | Data rate of the telemetry downlink transmitter                                     |
  | 5    | AdcsControllerGain    | float64          |        | 1.25                     | Proportional gain of the attitude control loop                                      |
  | 6    | MagnetometerBiasX     | int16            | nT     | -10                      | Bias subtracted from the X-axis magnetometer reading (calibration)                  |
  | 7    | PayloadPowerEnabled   | boolean (uint8)  |        | ENABLED (1)              | Power switch of the payload                                                         |

- Getting the values: send `/PUS20/REPORT_PARAMETER_VALUES` with the regular commanding flow, choosing the
  parameter IDs. The TM[20,2] response is decoded with an indirect parameter entry that looks the
  parameter up by its alias, so the values appear as updates of the `/SIMULATOR/ONBOARD/` parameters.
- Setting a value: open the parameter in the web interface and use the edit
  button, as for the other editable parameters. This is done by
  `org.yamcs.pus.PusParameterManager` (configured in `processor.yaml`), a
  processor service handling the parameters with the data source `EXTERNAL1`
  (by default; this can be changed in the PusParameterManager config). The edit
  sends a TC[20,3] `SET_PARAMETER_VALUES` command, and is equivalent to sending
  the command from the commanding page: same privilege, queues, verification
  and command history, attributed to the user. By default (option
  `reportAfterSet`), the PusParameterManager then also sends a TC[20,1]
  `REPORT_PARAMETER_VALUES` for the updated parameters, so the new value shows up
  right away. Set `reportAfterSet: false` to disable this; the value is then only
  updated by sending TC[20,1] yourself.
- Bulk updates: `SET_PARAMETER_VALUES` has one binary argument `instructions`, the application data of
  TC[20,3] in hex: N, then N x (parameter ID, value), the value being big endian with the size of the
  parameter. XTCE cannot type a command argument based on a preceding argument, so Yamcs does not validate
  the content. For example, `02 01 00fa 06 fed4` sets `HkReportInterval` to 250 and `MagnetometerBiasX` to -300.
- Group commands: to set several parameters with ONE TC[20,3] with typed, validated values (units, ranges,
  enumeration and boolean choices) use the group commands of `pus20.py` with the regular command sender:
  `SET_ADCS_CONFIGURATION` (AdcsMode, AdcsControllerGain, MagnetometerBiasX), `SET_TELEMETRY_CONFIGURATION`
  (HkReportInterval, TmDownlinkRate), `SET_THERMAL_AND_POWER_CONFIGURATION` (BatteryHeaterSetpoint,
  PayloadPowerEnabled) and `SET_ALL_PARAMETERS` (all of them). They have fixed instruction slots (the number of
  instructions and the parameter IDs are fixed, the values are the arguments), so the packet is exactly the one
  of the standard. All the arguments are required. The groups and the argument limits are defined in `pus20.py`
  (`GROUPS`, `PARAMETER_TYPES`); the limits are checked when the command is built, not on board.
- The simulator supports TC[20,1] and TC[20,3]. The on-board parameters are held by the simulator
  and are independent from the housekeeping data. An instruction with an unknown parameter ID fails
  its start of execution (TM[1,4], code `UNKNOWN_PARAMETER`, with the unknown parameter ID as failure data,
  decoded as `/PUS20/start_failure_parameter_id`) while the valid instructions are still
  processed. In TC[20,3] the instructions after an unknown parameter are ignored, because the size
  of its value cannot be deduced. A truncated request is rejected (`INVALID_REQUEST`).
- Try it with `examples/pus/tests/test-pus20.py`.

ST[21] request sequencing
- Not supported. Probably could be implemented similarily with ST[11]
- No support in the simulator.

ST[22] position-based scheduling
 - **This is the example that demonstrates ST[22]** (ST[11] is demonstrated in `pus-frames`
   instead - see above). Try it with `examples/pus/tests/test-pus22.py`.
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
   on each wrap. It is exposed as telemetry (`OrbitNumber`/`OrbitAngle`, `hkid=5`) so you can see
   where a scheduled activity's target position is relative to the current one.
 - Persistent scheduling (ECSS 6.22.5) is not supported - every activity is one-shot, like ST[11].

ST[23] file management 
- TODO: to augument the existing CFDP support in Yamcs with functionality for showing the list of files on the remote system.
- TODO: add support in the simulator