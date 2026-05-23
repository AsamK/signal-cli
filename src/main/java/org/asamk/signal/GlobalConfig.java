package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GlobalConfig(
        @JsonProperty("verbose") Integer verbose,
        @JsonProperty("logFile") String logFile,
        @JsonProperty("scrubLog") Boolean scrubLog,
        @JsonProperty("dataDir") String dataDir,
        @JsonProperty("busName") String busName,
        @JsonProperty("dbus") Boolean dbus,
        @JsonProperty("dbusSystem") Boolean dbusSystem,
        @JsonProperty("output") OutputType output,
        @JsonProperty("serviceEnvironment") ServiceEnvironmentCli serviceEnvironment,
        @JsonProperty("trustNewIdentities") TrustNewIdentityCli trustNewIdentities,
        @JsonProperty("disableSendLog") Boolean disableSendLog,
        @JsonProperty("account") String account
) {

    public static final GlobalConfig DEFAULT = new GlobalConfig(null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            ServiceEnvironmentCli.LIVE,
            TrustNewIdentityCli.ON_FIRST_USE,
            null,
            null);

    public static GlobalConfig empty() {
        return new GlobalConfig(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
