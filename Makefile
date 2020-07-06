.DEFAULT_GOAL := all

.PHONY: all
all: build

.PHONY: clean
clean:
	@mvn clean

.PHONY: build
build:
	@mvn install -DskipTests

.PHONY: rebuild
rebuild: clean all

.PHONY: test
test:
	@mvn test javadoc:javadoc
