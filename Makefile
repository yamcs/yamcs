.DEFAULT_GOAL := all

ifeq ($(PREFIX),)
	PREFIX := /usr/local
endif

PACKAGE := $(shell find distribution -name 'yamcs*.tar.gz')

.PHONY: all
all: build

.PHONY: clean
clean:
	@mvn clean

.PHONY: build
build:
	@mvn install -DskipTests -Dassembly.skipAssembly

.PHONY: rebuild
rebuild: clean all

.PHONY: package
package: build
	@mvn -f distribution/pom.xml package

.PHONY: test
test:
	@mvn test javadoc:javadoc

.PHONY: install
install:
	@install -d $(DESTDIR)$(PREFIX)/yamcs || exit
	@tar -xzf $(PACKAGE) --strip-components=1 -C $(DESTDIR)$(PREFIX)/yamcs
	@echo "Yamcs installed to `cd $(DESTDIR)$(PREFIX)/yamcs; pwd`"

