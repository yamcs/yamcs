create stream good_frame_stream(rectime timestamp, seq int, ertime hres_timestamp, scid int, vcid int, data binary)
create table if not exists good_frames(rectime timestamp, seq int, ertime hres_timestamp, scid int, vcid int, data binary, primary key(rectime, seq))
insert into good_frames select * from good_frame_stream

create stream bad_frame_stream(rectime timestamp, seq int, ertime hres_timestamp, data binary)
create table if not exists bad_frames(rectime timestamp, seq int, ertime hres_timestamp, data binary, primary key(rectime, seq))
insert into bad_frames select * from bad_frame_stream
