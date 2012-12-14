protoc -I .:../../../yamcs-api/src/main/ --java_out=java comp.proto

#workaround the inability of protostuff to include other directories in import
rm -f yamcs.proto
ln -s ../../../yamcs-api/src/main/yamcs.proto .

java -jar /tmp/protostuff-compiler-1.0.7-jarjar.jar modules.properties 
