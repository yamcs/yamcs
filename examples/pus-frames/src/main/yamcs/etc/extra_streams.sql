-- CFDP over PUS: extract the file data PDUs from the realtime TM and feed the CFDP service,
-- and turn the CFDP service output into TC packets. Same as the pus example.
create stream cfdp_in as select substring(packet, 16) as pdu from tm_realtime where extract_short(packet, 0) = 6141
create stream cfdp_out (gentime TIMESTAMP, entityId long, seqNum int, pdu  binary)
insert into tc_realtime select gentime, 'cfdp-service' as origin, seqNum, '/yamcs/cfdp/upload' as cmdName, unhex('17FDC0000000') + pdu as binary from cfdp_out

create table if not exists parameter_list(id uuid, name string, description string, patterns string[], primary key(id))
insert into parameter_list (id, name, patterns) values('ecfa3681-1c6c-48b8-ab2f-86a96f3b1ab4', 'PUS11 Detail Report', array['/PUS11/DETAIL_REPORT/*'])
insert into parameter_list (id, name, patterns) values('ecfa3681-1c6c-48b8-ab2f-86a96f3b1ab5', 'PUS11 Summary Report', array['/PUS11/SUMMARY_REPORT/*'])

-- Frame streams. The definitions have to match org.yamcs.tctm.ccsds.FrameStreamHelper
create stream good_frame_stream(rectime timestamp, seq int, ertime hres_timestamp, scid int, vcid int, data binary)
create table if not exists good_frames(rectime timestamp, seq int, ertime hres_timestamp, scid int, vcid int, data binary, primary key(rectime, seq))
insert into good_frames select * from good_frame_stream

create stream bad_frame_stream(rectime timestamp, seq int, ertime hres_timestamp, data binary)
create table if not exists bad_frames(rectime timestamp, seq int, ertime hres_timestamp, data binary, primary key(rectime, seq))
insert into bad_frames select * from bad_frame_stream
