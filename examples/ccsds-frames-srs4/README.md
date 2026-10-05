# CCSDS frames through an SRS4 radio

This example configures CCSDS TM and TC frame links with the SRS4 outer-frame providers.

Both supported spacecraft bus paths are enabled:

- nominal Ethernet flow using a 20-byte IPv4 header and 8-byte UDP header;
- contingency CAN flow using a 4-byte CSP v1 header;
- the 4-byte SRS4 radio header outside either bus header.

For TC, the `useCan` command option selects the flow at runtime. Leaving it unset or false selects Ethernet. Setting it to true selects CAN. The internally generated COP-1 control-frame default is also shown, although COP-1 is disabled in this example.

The outer IPv4/UDP and CSP endpoints are fields inside the SRS4 payload. They are independent of the UDP socket ports used by the Yamcs links themselves.

No built-in simulator currently produces or consumes the SRS4 outer format. To exercise the links end to end, send SRS4-wrapped TM frames to UDP port `10057` and receive SRS4-wrapped TC frames on UDP port `10058`.
