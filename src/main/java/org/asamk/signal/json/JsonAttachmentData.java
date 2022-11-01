package org.asamk.signal.json;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.InputStream;

public record JsonAttachmentData(
        @JsonSerialize(using = JsonStreamSerializer.class) InputStream data
) {}
