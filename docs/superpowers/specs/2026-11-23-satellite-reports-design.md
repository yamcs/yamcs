# Satellite reports in Yamcs Web

## Goal

Give operators a durable, navigable record of the S11, S15, and S130 report responses received by a Yamcs instance. Each snapshot represents one independent report request. Users can inspect the latest report for each type, open earlier responses by timestamp, and see where a report has missing pages or uncertain attribution.

The feature is read-only. It observes existing commanding and telemetry paths. Leave command release, transport, packet recording, and verifiers in their existing paths. Operators re-request a report through Yamcs commanding.

## Approaches

- **Native Yamcs feature, selected.** Add an isolated instance service, archive and protobuf API. Add a lazy-loaded web route and a capability-gated top-level navigation item. Operators get standard Yamcs layouts and links, and the HTTP reference is generated with the other Yamcs API docs. Existing packet and command flows are reusable inputs.
- **Packaged web extension.** A separate package could host the service and UI, and inject an item through the experimental web extension hook. It would need its own per-instance availability and front-end packaging, and separate API documentation distribution.

## Instance setup

The `services` entry in `yamcs.<instance>.yaml` activates collection, storage, and the API for that instance. Its capability controls both the navigation item and direct route access. Use the same setting throughout. Instances without the service have no report collection, archive initialization, or report navigation. The read API returns an unavailable response for an unconfigured instance. Required service inputs must be validated when the service starts.

```yaml
services:
  - class: org.yamcs.pus.report.SatelliteReportService
    args:
      telemetryStream: tm_realtime
      commandHistoryStream: cmdhist_realtime
      responseTimeoutMillis: 10000
```

The timeout value above is illustrative. Require a positive configured timeout that covers the instance's maximum elapsed time from confirmed sending or execution to receipt of the last report page. In timeout mode, attribution depends on that bound and on the selected telemetry stream carrying live responses. For jTYU, use `tm_realtime` and `cmdhist_realtime`. Exclude stored packet dumps and archive replay. An instance that serves multiple spacecraft must supply a reliable way to scope a request and its packets to the same target. On a single-spacecraft instance the instance provides that scope. Use an authoritative TM producer identity where needed to distinguish endpoints that publish the same report type. TC APID and TM APID may differ.

The service resolves the report profile against the active MDB. Each registered report type declares its PUS mapping, report container, pagination parameters, payload fields, and table shape. Resolve MDB names by qualified name, with mission-specific overrides where required. Any configured profile with unresolved or incompatible MDB fields fails validation with the specific field and container. Command-history recording is required for durable command links.

## Supported reports

| Report TM | Initiating TC subtypes | Table contents |
| --- | --- | --- |
| 11,10 · Schedule detail | 11,9 · 11,11 · 11,16 | Release time, sub-schedule and group, embedded TC details and original bytes |
| 11,13 · Schedule summary | 11,12 · 11,14 · 11,17 | Release time, sub-schedule and group, source ID, APID, sequence count |
| 11,19 · Sub-schedule status | 11,18 | Sub-schedule ID and enabled status |
| 11,27 · Scheduling group status | 11,26 | Group ID and enabled status |
| 15,6 · Application-process storage control | 15,5 | Store ID, application processes, service types and subtypes |
| 15,13 · Packet store content summary | 15,12 | Store ID, oldest and newest stored packet times, open-retrieval start, fill and remaining-to-retrieve percentages |
| 15,19 · Packet store status | 15,18 | Store ID, storage status, open-retrieval status, by-time-range status where supported |
| 15,23 · Packet store configuration | 15,22 | Store ID, capacity in bytes, store mode and virtual channel where present |
| 15,36 · Housekeeping storage control | 15,35 | Store ID, application process, structure ID and reported subsampling rate |
| 15,38 · Diagnostic storage control | 15,37 | Store ID, application process, structure ID and reported subsampling rate |
| 15,40 · Event blocking storage control | 15,39 | Store ID, application process and blocked event IDs |
| 130,4 · Current TM mode | 130,3 | Current mode, last transition time, last trigger and remaining time |
| 130,8 · Configuration tables | 130,7 | Table ID and entries by type, APID and SID |
| 130,14 · Event overlay configuration | 130,13 | Event ID, enabled status, timeout and telemetry entries |
| 130,16 · Event overlay status | 130,15 | Event ID, enabled and active status, remaining time |
| 130,18 · Active TM configuration | 130,17 | Entry type, APID, SID, source mask, generation, storage gate and transmission state |
| 130,22 · Pass state | 130,21 | Pass mode, loss-of-signal time, dump-active status and available dump slot and timing details |

Decode the jTYU profile through XTCE using the active MDB's encodings. The S15 store capacity is in bytes as received. S130 pass-state slot IDs are 8-bit. Both current and per-slot IDs use that width. Where the MDB decodes fixed-layout fields, use the status and count to express their applicability and retain the decoded values. Subsampling rates retain their on-wire value and application scope. Preserve nested configuration and parent rows with zero children, since an empty selection under a parent can have meaning. Preserve successive segments for the same store, APID, table, or event. Distinguish the command's filter or selected store from an all-entries report in the UI and API.

## Collection and attribution

Subscribe to the configured telemetry stream and extract only recognized report packets with Yamcs's packet-scoped XTCE extractor. Respect each stream's root container and link-supplied packet status. Parse page metadata and payload from the same packet. Decode complete semantic rows before counting a page. Keep spacecraft generation time and Yamcs reception time as distinct values. Bound and isolate collection work so a decoding or storage failure cannot interrupt the telemetry or commanding publisher.

Track matching commands through live command history. A direct request becomes eligible at its successful sent acknowledgement. Use the wire TC service and subtype to select the expected TM type, with the archived Yamcs command ID for navigation. Use received-event timestamps and a bounded allowance for TM to reach the service before the sent acknowledgement is timestamped or delivered. Apply the same candidate rules during that allowance. Use ordinary elapsed time for response deadlines. Include a scheduled request only when there is reliable evidence of the original TC's execution epoch for timeout matching, or a valid echoed request ID. Uplink of a scheduling wrapper alone does not start a report response window.

For timeout attribution, consider every eligible request for the same target and response type at the packet's reception time. The window opens at confirmed sending or execution, has a fixed deadline, and stays eligible until its deadline even if all expected pages arrive early. A page with one candidate belongs to that request. A page with multiple candidates is kept out of all candidate snapshots and contributes an ambiguity indication to each affected attempt. A page with zero candidates has no initiating command. It cannot create or alter a request-level snapshot. Log unassigned and invalid arrivals in the instance service with the report type, target if known, reception time, packet identity and reason. Expose withheld packet counts and data issues in affected snapshots. Report content APIs serve request snapshots and their issues. Overlap by itself does not imply a received page was ambiguous. Decisions use the candidate set at the time of the page. Requests for different report types or different targets have independent windows. Command attempts are tracked separately even when they use identical arguments.

For deterministic attribution, allow the instance configuration to associate a report type's fully qualified TM parameter with the corresponding named command argument or command-history attribute. The optional mapping names `telemetryParameter` and either `commandArgument` or `commandHistoryAttribute` for the specified report type. Compare canonicalized typed values scoped to the target and report type. The flight profile must echo the same request value on every page and keep values unique for as long as packets from a previous use can still arrive. Validate the configured parameter against the report container. The response timeout continues to bound collection and partial-report publication. When an echo is configured, a missing echo, unknown value, or non-unique match cannot fall through to timestamp matching. Different report types can use different policies while adopting the echo fields. A deployment with no echo configuration uses timeout mode for every type. Log unmatched or unusable echo fields as unassigned arrivals. Timeout mode works with the current Hermes-generated MDB.

## Finalization and history

- Require one-based `page_number` and `total_pages` with `1 <= page_number <= total_pages`. The two fields are unsigned 16-bit values in the mission's shared header spare bytes. A valid empty response carries page `1 of 1` and zero rows. Require coverage of every numbered page for completeness, in any arrival order. Assemble accepted rows in page order, then their original order within each page.
- Ignore exact retransmissions of the same page content and pagination metadata for an already attributed request. A different payload claiming an occupied page number, inconsistent total counts, or a malformed payload must never produce a complete report. Quarantine conflicting page variants, retain other confidently decoded rows, and report the data conflict. Report missing numbers or ranges when a consistent total is known. Do not infer a total from another request or from an ambiguous page.
- Use **Complete** when all expected pages uniquely attributable to the request decoded successfully. Record the reception time of the final required page as the assembly time. Use **Partial** at the deadline with confidently attributable rows and missing or unusable pages. Use **Ambiguous** for attempts affected by withheld pages when completeness cannot be independently established. Retain confidently attributable rows, state how many packets were withheld, and expose the known request as an attempt without asserting ownership of the withheld packets. Use **No response** when Yamcs continuously observed a request window and received no report evidence. The expected page count is unknown when none of the available packets establish it.
- Publish snapshots in both modes after the fixed response window and callback reordering allowance close. This lets duplicate conflicts received before the deadline affect the result in either mode. Include `finalizedAt` separately from first and last reception times and the complete report's `assembledAt`. Keep finalized snapshots immutable and deduplicate storage by stable attempt identity. Classify arrivals after the deadline as late when independently identifiable.
- Store history per instance in a dedicated Yamcs archive table, keyed and indexed for stable ID, report type, target, and time queries. Preserve status, request scope, timestamps, page evidence, data issues, and typed payload. Checkpoint accepted pages, conflicts, and withheld counts for open attempts and finalize each attempt idempotently. After a service interruption, finalize persisted open attempts with the interruption reason and any observed coverage. Keep Ambiguous when the attempt already had unresolved attribution. Use Partial for other interrupted outcomes. Outcomes with no observed packets appear in History. Rebuild recent eligibility windows from command history before assigning live pages after a restart. Still-open interrupted windows remain candidates through their original deadlines so their pages cannot be attributed to a newer attempt. If the archive cannot establish which recent requests are eligible, suppress timeout attribution for one full response interval. Historical TM backfill is outside this release.

## Command history

Every confidently attributed complete or partial report has a link to the initiating Yamcs command-history entry. Include request identifiers and available command arguments, subject to the existing per-command `CommandHistory` privilege. The S11 status reports have report-level links. S11 schedule details and summaries additionally resolve the scheduled activity in each row when the archive provides a unique match. Decode or read the PUS source ID, APID, and CCSDS sequence count, use archived packet bytes when available, and constrain the match with mission and time context because CCSDS counts wrap. For a command inserted explicitly in a multi-entry TC[11,4], link the unique parent command and identify the embedded occurrence. Display the wire fields without a link when no unique authorized command is available.

## HTTP API and web

Use a native protobuf API with operation comments and generated HTTP reference documentation. Provide a list of supported report types with their latest observed snapshots, paginated history by type with optional target and time filters, and retrieval by stable snapshot ID. A response carries enough metadata to distinguish empty, partial, ambiguous, and failed-attribution outcomes without reading the raw packet. Return an opaque continuation token and deterministic ordering for pagination. Use canonical timestamps, typed report fields with documented units, and the same status and row semantics for API and UI. Apply existing `ReadPacket` privileges to report content, including report scopes carried in TM. Apply per-command `CommandHistory` privileges to command references, identifiers, arguments and command-derived request scopes. An attempt with no observed packets has only command-derived evidence and is visible only with the relevant command privilege and access to the expected report type. Existing archive administrators with generic table access retain that access.

Add **Satellite reports** to the instance navigation using current Yamcs styles and layout components. Group the report types by service. Linkable **Latest** and **History** views retain the selected type and target. Latest shows the most recent observed report snapshot based on report reception, including Partial and Ambiguous results. A no-response attempt is visible in History and can be indicated in Latest without replacing received data. Show the report status, applicable timestamps, request scope, and available command link above the content. For Partial, show received versus expected pages, identify missing ranges or decoding issues, and suggest re-requesting. For Ambiguous, explain that multiple request windows covered the arrivals and show only confidently attributed content. Empty valid reports display zero rows with Complete status. Use compact tables, expandable nested rows, and horizontal scroll for wide tables at narrow widths.

## Verification

- Exercise all 17 TM types through packet-scoped XTCE decoding and confirm request mapping, page numbering, row structure, and renderable fields using the active mission profile or matching fixtures. Check the S15 byte capacity and the S130 slot width and applicability explicitly. Cover empty rows and nested zero-length arrays.
- Exercise valid single and multiple pages, out-of-order receipt, exact duplicates, conflicting duplicate pages, invalid pagination, inconsistent total counts, and missing first, middle or last pages. Confirm page-local payload order, durable history, and safe handling of interrupted and resumed collection.
- Exercise sequential and overlapping same-type requests, multiple TC subtypes that map to the same TM type, different report types and targets, sent acknowledgement reordering, scheduled-wrapper uplink, zero candidates, and expired windows. Verify that overlap does not delay commanding and that ambiguous pages cannot complete either report. Exercise interleaved deterministic replies and unmatched or duplicate echo values.
- Verify command links and privileges for initiating requests and embedded scheduled activities, including CCSDS count rollover and unavailable matches. Exercise the HTTP page token, latest sorting, instance gating, and direct URL access. Verify narrow and wide layouts in Yamcs Web.
