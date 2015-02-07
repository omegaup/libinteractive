SELF_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))

LIBINTERACTIVE_SOURCES := $(shell /usr/bin/find $(SELF_DIR)/src/main -name *.scala*)

SCALA_VERSION := $(shell grep 'scalaVersion' $(SELF_DIR)/build.sbt | sed -e 's/.*"\([0-9]\+\.[0-9]\+\).*".*/\1/')
LIBINTERACTIVE_VERSION := $(shell grep '^version' $(SELF_DIR)/build.sbt | sed -e 's/.*"\(.*\)".*/\1/')
LIBINTERACTIVE_JAR := $(SELF_DIR)/target/scala-$(SCALA_VERSION)/proguard/libinteractive_$(SCALA_VERSION)-$(LIBINTERACTIVE_VERSION).jar
