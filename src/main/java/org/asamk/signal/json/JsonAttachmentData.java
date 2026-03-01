package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AttachmentData")
public record JsonAttachmentData(
        String data
) {}
