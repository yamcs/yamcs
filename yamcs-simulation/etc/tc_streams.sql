create stream tc_sim as select * from tc_realtime where cmdName like '/YSS/SIMULATOR/%'
create stream tc_tse as select * from tc_realtime where cmdName like '/TSE/%'
