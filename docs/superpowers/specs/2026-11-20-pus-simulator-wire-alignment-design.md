# PUS simulator wire alignment

## Scope

Make the Yamcs PUS simulator exchange packets that the current XTCE adapters describe. The mission deployment will use its `direct` composition and raw CCSDS frames over UDP when `SIMULATOR_ENABLED=true`. Update the PUS examples, the overlapping MDB definitions in `ccsds-frames-srs4`, and `pus_analysis`. Implement ST[128] and ST[130] and return ST[01] verification for the exported ST[131] commands. CFDP simulation is outside this change.

## Approaches

Reconcile the checked-in MDBs in place. This preserves the example command paths and scenario-specific data while bringing their wire definitions into agreement with the adapters. Swarup approved this approach. Replacing the MDBs with a mission export would also replace example identifiers, command paths, and scenario data. A generator dependency would make routine Yamcs builds depend on the adapter checkout and its inputs.

## Packet and frame handling

- Put four spare bytes after source ID in TC and after CUC time in ordinary TM. Zero-fill the field for ordinary services and TC wrappers. Write page number and total pages as big-endian unsigned 16-bit values for ST[11], ST[15], and ST[130] TM. Leave the APID-zero time report layout intact. Protect the spare bytes with the packet CRC when enabled.
- Apply the profile to both TCP and UDP simulator input, generated ST[11] and ST[22] wrappers, and nested PUS TCs used by scheduling, event actions, sequences, and large-packet transfer. Keep header length, CCSDS length, application data boundaries, and checksum length consistent. Use the ST[11] wrapper CRC setting for every outer TC[11,4], since the wire format is identical for an MDB-encoded command and an automatically generated wrapper. Use the configured embedded TC CRC rule whenever either path carries an inner PUS TC. Use the ordinary TC packet CRC setting for other top-level commands unless an applicable scheduling-wrapper setting overrides it.
- Support `NONE` and `CRC16` for simulator CCSDS TM and TC frame error detection. Calculate trailer positions using the configured CRC and TM OCF settings for idle and data frames. Preserve the existing example transport profiles and the COL simulator behavior. The SRS4 example remains an external endpoint example.
- Route the mission simulator through `OPERATIONAL_MODE=direct` only. Share the existing direct link composition with the enabled simulator and use raw TC frames. Preserve the external spacecraft mode used for SRS4.

## Shared configuration

Use the same environment settings in the mission ground links and simulator service args where both consume a value. Add the tracked mission `.env.example` defaults. Yamcs examples keep standalone defaults suitable for their own MDB and transport.

| Environment setting | Mission default |
|---|---|
| `TM_PORT`, `TC_PORT`, `TC_HOST` | Existing deployment values. In simulator mode, the ground TC destination is the simulator. |
| `CCSDS_SPACECRAFT_ID`, `CCSDS_TM_FRAME_TYPE` | `1`, `TM` |
| `CCSDS_TM_FRAME_LENGTH`, `CCSDS_TC_MAX_FRAME_LENGTH` | `980`, `980` |
| `CCSDS_TM_FRAME_CRC`, `CCSDS_TC_FRAME_CRC`, `CCSDS_TM_OCF_PRESENT` | `NONE`, `CRC16`, `false` |
| `PUS_TM_PACKET_CRC`, `PUS_TC_PACKET_CRC`, `PUS11_WRAPPER_CRC`, `PUS_EMBEDDED_TC_CRC` | `false` for each |
| `PUS_CUC_EPOCH`, `PUS_CUC_PFIELD`, `PUS_CUC_PFIELD_CONT` | `UNIX`, `46`, unset continuation |
| `PUS_TM_IMPLICIT_PFIELD`, `PUS11_TC_IMPLICIT_PFIELD` | `true`, `true` |
| `PUS_S11_APID`, `PUS_SPARE_BYTES` | `16`, `4` |
| `PUS11_SUBSCHEDULE_ID_BYTES`, `PUS11_SUBSCHEDULE_ID_DEFAULT` | `1`, `0` |
| `PUS11_GROUP_ID_BYTES`, `PUS11_GROUP_ID_DEFAULT` | `1`, `0` |

Use implicit CUC by default for both MDB-encoded and generated TC[11,4]. Give schedule TC time its own encoding configuration so it can differ from TM. Share the configured ST[11] identifier widths and fallback defaults with the wrapper builder and the simulator. Group and sub-schedule IDs explicitly supplied by a command take precedence over configured defaults. Validate identifier ranges against their widths. Creating and enabling a group still uses the corresponding ST[11] commands. Changing a setting that affects a fixed XTCE field requires a matching MDB. For the mission profile the expected ordinary TC application-data offset is 15 and the ordinary TM offset is 23. The preprocessing time offsets are 13 for ordinary TM and 7 for APID-zero time packets.

## MDB and service behavior

- For each example MDB, compare the base headers, concrete service layouts, fixed and dynamic field sizes, time encodings, counts, and embedded-TC annotations against the corresponding adapter definitions. Keep simulator services without an adapter usable with their existing service payloads and the new common spare-field layout. Preserve example command paths and verifier behavior while aligning the wire fields.
- Reconcile the overlapping standard services, including ST[11] scheduling and report layouts and ST[15] stores and reports. Stage report pages from a consistent snapshot and put pagination in the secondary header. Use one-based pages and encode an empty requested report as a single page. Honor the count width and simulator packet budget when splitting rows.
- ST[128] stores the two 69-byte TLE lines, returns their bytes in TM[128,3], and reports both computed line checksums and the individual validity flags in TM[128,5]. Use the standard TLE decimal checksum rule. Keep the stored TLE in simulator memory.
- ST[130] models the adapter-defined TM modes and transitions, structure selection tables, timed event overlays, active TM configuration, timed passes, slot progress, and dump cancellation. Store state in memory on the simulator executor. The proposed simulator behavior maps NTM, Beacon, and CTM to the STM, Beacon, and CTM tables respectively. An unconfigured table allows existing ST[03] generation rules to apply. An active enabled overlay adds its selections to the active table for its timeout after an ST[05] event. Assign source-mask bits 0, 1, 2, and 3 to STM, Beacon, CTM, and active overlays respectively, subject to a flight behavior reference if available. Report generation, forwarding, and storage state after applying the relevant ST[03], ST[14], and ST[15] rules. Advance pass slots on the simulator clock. Packet-store slots use simulated ST[15] contents. Payload-data slots exercise progress and timing with simulator data. Emit the corresponding TM report layouts with coherent values and pagination. Use verification failures for invalid commands and leave state consistent when a multi-entry request fails.
- ST[131] recognizes the exported TC[131,4] variants and emits ST[01] acceptance, start, and completion according to the simulator verification convention. It does not execute flight operations.
- Remove literal adapter-project references from tracked Yamcs files after reconciling the definitions. Update packet layout, config, service, and verification examples in `pus_analysis` after verifying implementation.

## Verification

Use byte-level checks for TM and TC offsets, zero spare fields, pagination, APID-zero time, per-layer CRC behavior and rejection, and idle and data frame layouts. Verify ordinary TCs and TC[11,4] built by the MDB and by the ground scheduling option against the same simulator profile, including multi-byte identifiers and embedded CRC settings. Create and enable the group and sub-schedule in the round trip before testing command execution. Load all changed example MDBs and verify that representative commands encode and emitted reports decode. Exercise each new service through dispatch and exercise state changes, reports, and invalid requests. Run the existing PUS simulator and postprocessor tests and a live direct-frame round trip with a mission-compatible profile. Use the updated example MDB for manual ST[11] command verification. The mission’s current generated MDB needs a release export with the new ST[11] definitions before its command catalog can offer those same manual commands.
