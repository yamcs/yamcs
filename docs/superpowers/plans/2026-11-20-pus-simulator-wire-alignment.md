# PUS simulator wire alignment implementation plan

**Goal** — Exchange and decode direct CCSDS/PUS traffic with the approved packet layout and simulate the adapter-defined services.

**Architecture** — Use a simulator packet profile shared by transport ingress, packet construction, and embedded command decoding. Keep frame error detection in the frame layer. Reconcile the checked-in example MDBs against the generated mission MDB and adapter definitions.

**Spec** — [PUS simulator wire alignment](../specs/2026-11-20-pus-simulator-wire-alignment-design.md)

## Constraints

Leave the work uncommitted. The refreshed mission MDB is a reference. Use the ST[130] design document for behavioral rules. Run the mission simulation in `OPERATIONAL_MODE=direct` on raw frames. Retain the external SRS4 example and the existing COL transport behavior. Verify byte-level behavior with focused tests and run the affected modules before delivery.

## 1. Packet and command processing

- [ ] Add tests for an ordinary TC at offset 15, ordinary mission-profile TM at offset 23, APID-zero TM at offset 7, two 16-bit pagination fields, and a nested TC whose CRC policy differs from the enclosing packet.
- [ ] Implement packet settings in `PusTcPacket`, `PusTmPacket`, `PusTmTimePacket`, `PusSimulator`, and `SimulatorCommander`. Use the settings from TCP and UDP packet factories and for nested ST[11], ST[13], ST[19], ST[21], and ST[22] TCs. Reject truncation or an invalid configured CRC before execution.
- [ ] In `PusCommandPostprocessor`, add spare bytes to both wrappers, apply the same TC[11,4] CRC selection to manual and automatic commands, and encode the original command using the embedded CRC selection when wrapping it. Verify both code paths with different top-level and embedded CRC settings.
- [ ] In `Pus11Service`, use the configured TC time format for release times and filters, configured byte widths for identifiers, and the TM time format for reports. Paginate ST[11] and ST[15] reports and compare field widths and ordering with the generated MDB. Run the PUS packet, scheduling, and command postprocessor tests.

## 2. Direct frame transport and mission YAML

- [ ] Test TM payload boundaries with CRC/OCF present and absent, including idle frames. Test TC frame ingest with and without the CRC and with a configured maximum frame length.
- [ ] Implement optional CRC and OCF in `UdpTmFrameLink`, and optional CRC and frame length checks in `UdpTcFrameLink` and `TcVcFrameLink`. Wire YAML options through `SimulatorCommander` and `SimulatorArgs`. The `frame` map exposes `tmErrorDetection`, `tcErrorDetection`, `tmOcfPresent`, and `tcMaxFrameLength`.
- [ ] In the mission YAML and `.env.example`, use the approved values for transport, frame, and packet settings. Start the mission simulator in direct mode and preserve the direct spacecraft and external encapsulated paths.

## 3. Custom services

- [ ] Test ST[128] TLE set/report and valid and invalid checksum reports. Implement the exact fixed-width TLE line encoding and a checksum per line.
- [ ] Derive ST[130] modes, tables, overlays, report flags and pass/dump behavior from its specification. Test a mode transition, overlay activation/expiry, table editing and paginated report, and pass/abort behavior. Implement state and all adapter-exported command and report codecs in `Pus130Service`.
- [ ] Route ST[128] and ST[130] through `PusSimulator` and add ST[131] start/completion verification for its exported subtype. Verify unsupported and malformed requests follow the existing negative verification convention.

## 4. MDBs and verification

- [ ] Update the three example MDB families in place. Reconcile common headers, scheduling, verification, service payload field order and widths, time encodings, and embedded commands. Add the custom service definitions and remove the remaining adapter-project reference. Preserve example command paths.
- [ ] Load all changed MDBs with the Yamcs XTCE loader. Test representative TC encoding and TM decoding through the existing example test fixtures or a Java loader test. Check profile-specific YAML against the corresponding MDB.
- [ ] Run Maven tests for `simulator` and `yamcs-core` and exercise a direct framed round trip. Update `pus_analysis` with the verified behavior, offsets, configuration, and service reports.
