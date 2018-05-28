curl -v -H "Content-Type:multipart/related; boundary=foo_bar_baz" -XPOST http://localhost:8090/api/buckets/_global/my_bucket/ -T multipart-object
