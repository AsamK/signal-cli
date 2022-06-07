package org.asamk.signal.manager.storage.threads;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LegacyThreadInfo {

    @JsonProperty
    public String id;

    @JsonProperty
    public int messageExpirationTime;
}
