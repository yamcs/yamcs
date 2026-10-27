.DEFAULT_GOAL := all

.PHONY: clean
clean:
	@mvn clean

.PHONY: build-skip-test
build-skip-test:
	@mvn install -DskipTests

.PHONY: build
build:
	@mvn install --fail-at-end

.PHONY: build-web-skip-test
build-web-skip-test:
	cd yamcs-web/src/main/webapp && npm install && npm run build
	@mvn install -DskipTests -pl yamcs-web -am

.PHONY: build-web
build-web:
	cd yamcs-web/src/main/webapp && npm install && npm run build
	@mvn install --fail-at-end -pl yamcs-web -am

.PHONY: rebuild-skip-test
rebuild-skip-test: clean build-skip-test

.PHONY: rebuild
rebuild: clean build

.PHONY: test
test:
	@mvn test javadoc:javadoc --fail-at-end

.PHONY: build-sim
build-sim:
	@mvn -pl simulator,examples/pus -am clean install --fail-at-end

.PHONY: build-sim-skip-test
build-sim-skip-test:
	@mvn -pl simulator,examples/pus -am clean install -DskipTests

.PHONY: run-sim
run-sim:
	@mvn -pl examples/pus yamcs:run
# Web UI once running: http://localhost:8090, instance "pus"