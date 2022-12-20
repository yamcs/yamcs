create stream cfdp_in as select packet as pdu from cfdp_tm
create stream cfdp_out (gentime TIMESTAMP, entityId long, seqNum int, pdu  binary)
--insert into tc_realtime select gentime, 'cfdp-service' as origin, seqNum, '/yamcs/cfdp/upload' as cmdName, unhex('17FDC0000000') + pdu as binary from cfdp_out
insert into cfdp_tc select gentime, 'cfdp-service' as origin, seqNum, '/yamcs/cfdp/upload' as cmdName, pdu as binary from cfdp_out
