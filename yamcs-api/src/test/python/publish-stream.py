import websocket
ws = websocket.WebSocket()
wsaddr = "ws://localhost:8090/simulator/_websocket"
print "Connecting to "+wsaddr + "...",
ws.connect(wsaddr)
print "connected"

print " publishing to stream...",

ws.send('[1,1,3,{"stream":"publish", "data": {"stream": "tm_realtime", "columnValue":[{"columnName":"gentime","value":{"type":6,"timestampValue":1438608491320}},{"columnName":"seqNum","value":{"type":3,"sint32Value":134283264}},{"columnName":"rectime","value":{"type":6,"timestampValue":1438608508323}},{"columnName":"packet","value":{"type":4,"binaryValue":"CAEAAAAPQuou2FJFAAAABOcAAAAAAA=="}}]}}]')
print "published"
result = ws.recv()
print "Received '%s'" % result

