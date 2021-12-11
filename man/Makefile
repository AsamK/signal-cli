A2X = a2x

MANPAGESRC = signal-cli.1 signal-cli-dbus.5 signal-cli-jsonrpc.5

.PHONY: all
all: $(MANPAGESRC)

%: %.adoc
	@echo "Generating manpage for $@"
	$(A2X) --no-xmllint --doctype manpage --format manpage "$^"
