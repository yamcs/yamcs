create stream cfdp_in as select substring(packet, 16) as pdu from tm_realtime where extract_short(packet, 0) = 6141
create stream cfdp_out (gentime TIMESTAMP, entityId long, seqNum int, pdu  binary)
insert into tc_realtime select gentime, 'cfdp-service' as origin, seqNum, '/yamcs/cfdp/upload' as cmdName, unhex('17FDC000000000000000000000000000') + pdu as binary from cfdp_out

create table if not exists parameter_list(id uuid, name string, description string, patterns string[], primary key(id))
insert into parameter_list (id, name, patterns) values('ecfa3681-1c6c-48b8-ab2f-86a96f3b1ab4', 'PUS11 Detail Report', array['/PUS11/DETAIL_REPORT/*'])
insert into parameter_list (id, name, patterns) values('ecfa3681-1c6c-48b8-ab2f-86a96f3b1ab5', 'PUS11 Summary Report', array['/PUS11/SUMMARY_REPORT/*'])