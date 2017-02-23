package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ThreadInfo {
    @JsonProperty
    public String id;

    @JsonProperty
    public int messageExpirationTime;
}
