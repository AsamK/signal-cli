package org.asamk.signal;

public class BaseConfig {

    public static final String PROJECT_NAME = BaseConfig.class.getPackage().getImplementationTitle();
    public static final String PROJECT_VERSION = BaseConfig.class.getPackage().getImplementationVersion();

    static final String USER_AGENT_SIGNAL_ANDROID = "Signal-Android/6.39.1";
    static final String USER_AGENT_SIGNAL_CLI = PROJECT_NAME == null
            ? "signal-cli"
            : PROJECT_NAME + "/" + PROJECT_VERSION;
    static final String USER_AGENT = USER_AGENT_SIGNAL_ANDROID + " " + USER_AGENT_SIGNAL_CLI;

    private BaseConfig() {
    }
}
