.DEFAULT_GOAL := all

.PHONY: all
all: build

.PHONY: clean
clean:
	@mvn clean

.PHONY: build
build:
	@mvn install -DskipTests

.PHONY: build-web
build-web:
	cd yamcs-web/src/main/webapp && npm install && npm run build
	@mvn install -DskipTests -pl yamcs-web -am

.PHONY: rebuild
rebuild: clean all

.PHONY: test
test:
	@mvn test javadoc:javadoc
