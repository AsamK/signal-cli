A2X = a2x
MKDIR = mkdir
GZIP = gzip

MANPAGESRC = signal-cli.1 signal-cli-dbus.5 signal-cli-jsonrpc.5

.PHONY: all
all: $(MANPAGESRC)

%: %.adoc
	@echo "Generating manpage for $@"
	$(A2X) --no-xmllint --doctype manpage --format manpage "$^"

.PHONY: install
install: all
	$(MKDIR) -p man1 man5
	for f in *.1; do $(GZIP) < "$$f" > man1/"$$f".gz ; done
	for f in *.5; do $(GZIP) < "$$f" > man5/"$$f".gz ; done

.PHONY: clean
clean:
	rm -rf *.1 *.5 man1 man5
