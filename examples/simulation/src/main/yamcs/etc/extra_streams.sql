-- create stream tc_sim as select * from tc_realtime where cmdName like '/YSS/SIMULATOR/%'
-- create stream tc_tse as select * from tc_realtime where cmdName like '/TSE/%'

create table if not exists invalid_tm(rectime timestamp, seqNum long, packet binary, primary key(rectime, seqNum))
insert into invalid_tm select * from invalid_tm_stream
