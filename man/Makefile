A2X = a2x

MANPAGESRC = signal-cli.1

.PHONY: all
all: $(MANPAGESRC)

%: %.txt
	@echo "Generating manpage for $@"
	$(A2X) --no-xmllint --doctype manpage --format manpage "$^"
