package org.asamk.signal;

import java.util.Optional;

public class BaseConfig {

    public static final String PROJECT_NAME = BaseConfig.class.getPackage().getImplementationTitle();
    public static final String PROJECT_VERSION = BaseConfig.class.getPackage().getImplementationVersion();

    static final String USER_AGENT_SIGNAL_ANDROID = Optional.ofNullable(System.getenv("SIGNAL_CLI_USER_AGENT"))
            .orElse("Signal-Android/7.47.1");
    static final String USER_AGENT_SIGNAL_CLI = PROJECT_NAME == null
            ? "signal-cli"
            : PROJECT_NAME + "/" + PROJECT_VERSION;
    static final String USER_AGENT = USER_AGENT_SIGNAL_ANDROID + " " + USER_AGENT_SIGNAL_CLI;

    private BaseConfig() {
    }
}
