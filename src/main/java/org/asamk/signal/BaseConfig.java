package org.asamk.signal;

public class BaseConfig {

    public final static String PROJECT_NAME = BaseConfig.class.getPackage().getImplementationTitle();
    public final static String PROJECT_VERSION = BaseConfig.class.getPackage().getImplementationVersion();

    final static String USER_AGENT = PROJECT_NAME == null ? "signal-cli" : PROJECT_NAME + " " + PROJECT_VERSION;

    private BaseConfig() {
    }
}
