import websocket
ws = websocket.WebSocket()
wsaddr = "ws://localhost:8090/_websocket/simulator"
print "Connecting to "+wsaddr + "...",
ws.connect(wsaddr)
print "connected"

print " subcribing to processor...",

ws.send('[1,1,3, {"processor": "subscribe"}]'); 
print "subscribed"
while True:
    result =  ws.recv()
    print "Received '%s'" % result

