include definitions.mk

.PHONY: all clean
all: $(LIBINTERACTIVE_JAR)

clean:
	@rm $(LIBINTERACTIVE_JAR)

$(LIBINTERACTIVE_JAR): $(LIBINTERACTIVE_SOURCES)
	sbt proguard:proguard publishLocal
