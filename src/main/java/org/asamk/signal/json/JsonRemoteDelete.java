package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RemoteDelete")
record JsonRemoteDelete(long timestamp) {}
