curl -XGET http://localhost:8090/simulator/api/parameter/_get?pretty -d '
  {
    "list": [
      {"name":"/YSS/SIMULATOR/Longitude"},
      {"name":"/YSS/SIMULATOR/Latitude"},
      {"name":"/YSS/SIMULATOR/Altitude"}]
     ,"timeout":2000}
  }'
