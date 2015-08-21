import websocket
ws = websocket.WebSocket()
wsaddr = "ws://localhost:8090/simulator/_websocket"
print "Connecting to "+wsaddr + "...",
ws.connect(wsaddr, header=['Authorization: Basic YXN0cm9uYXV0MTpwYXNzd29yZA=='])
#ws.connect(wsaddr, header=['Authorization: Basic b3BlcmF0b3I6cGFzc3dvcmQ='])

#ws.connect(wsaddr, header=['Authorization: Basic b3BlcmF0b3I6cGFzc3dvcmQ='])
#ws.connect(wsaddr)
print "connected"

print " subcribing to alarms...",

ws.send('[1,1,3, {"alarms": "subscribe"}]')
print "subscribed"
while True:
    result =  ws.recv()
    print "Received '%s'" % result

