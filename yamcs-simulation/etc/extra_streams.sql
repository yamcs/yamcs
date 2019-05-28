create stream tc_sim as select * from tc_realtime where cmdName like '/YSS/SIMULATOR/%'
create stream tc_tse as select * from tc_realtime where cmdName like '/TSE/%'

create stream cfdp_in as select substring(packet, 16) as pdu from tm_realtime where extract_short(packet, 0) = 6141
create stream cfdp_out (gentime TIMESTAMP, entityId long, seqNum int, pdu  binary)
insert into tc_sim select gentime, 'cfdp-service' as origin, seqNum, '/yamcs/cfdp/upload' as cmdName, unhex('17FDC000000000000000000000000000') + pdu as binary from cfdp_out

