.DEFAULT_GOAL := all

ifeq ($(PREFIX),)
	PREFIX := /usr/local
endif

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

.PHONY: live
live:
	@./make-live-devel.sh --yss

.PHONY: run
run: live
	@live/bin/yamcsd

.PHONY: install
install:
	@install -d $(DESTDIR)$(PREFIX)/yamcs || exit
	@tar -xzf distribution/target/yamcs.tar.gz --strip-components=1 -C $(DESTDIR)$(PREFIX)/yamcs
