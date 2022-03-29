package org.asamk.signal.manager;

import org.signal.libsignal.protocol.logging.SignalProtocolLogger;
import org.signal.libsignal.protocol.logging.SignalProtocolLoggerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibSignalLogger implements SignalProtocolLogger {

    private final static Logger logger = LoggerFactory.getLogger("LibSignal");

    static void initLogger() {
        SignalProtocolLoggerProvider.setProvider(new LibSignalLogger());
    }

    private LibSignalLogger() {
    }

    @Override
    public void log(final int priority, final String tag, final String message) {
        final var logMessage = String.format("[%s]: %s", tag, message);
        switch (priority) {
            case SignalProtocolLogger.VERBOSE -> logger.trace(logMessage);
            case SignalProtocolLogger.DEBUG -> logger.debug(logMessage);
            case SignalProtocolLogger.INFO -> logger.info(logMessage);
            case SignalProtocolLogger.WARN -> logger.warn(logMessage);
            case SignalProtocolLogger.ERROR, SignalProtocolLogger.ASSERT -> logger.error(logMessage);
        }
    }
}
