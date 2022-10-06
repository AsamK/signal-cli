package org.asamk.signal;

public class BaseConfig {

    public final static String PROJECT_NAME = BaseConfig.class.getPackage().getImplementationTitle();
    public final static String PROJECT_VERSION = BaseConfig.class.getPackage().getImplementationVersion();

    final static String USER_AGENT_SIGNAL_ANDROID = "Signal-Android/5.51.7";
    final static String USER_AGENT_SIGNAL_CLI = PROJECT_NAME == null
            ? "signal-cli"
            : PROJECT_NAME + "/" + PROJECT_VERSION;
    final static String USER_AGENT = USER_AGENT_SIGNAL_ANDROID + " " + USER_AGENT_SIGNAL_CLI;

    private BaseConfig() {
    }
}
