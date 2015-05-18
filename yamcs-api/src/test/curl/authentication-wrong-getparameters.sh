# request without authentication, should return 401 Unauthorized if privileges are enable on Yamcs server
curl  -XGET http://localhost:8090/simulator/api/mdb/parameters?pretty