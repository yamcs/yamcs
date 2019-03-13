curl -XPOST http://localhost:8090/api/instances -d '{
    name: "simulator2",
    template: "template1",
    templateArgs: {
        tmPort: 30000,
        tcPort: 30001,
        losPort: 30002,
        tm2Port: 30003,
        telnetPort: 30004
    };
    labels: {
        label1: "value1",
        label2: "value2"
    }
}'
