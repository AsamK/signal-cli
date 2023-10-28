package org.asamk.signal.logging;

import org.asamk.signal.OutputType;

import java.io.File;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.FilterReply;

public class LogConfigurator extends ContextAwareBase implements Configurator {

    private static int verboseLevel = 0;
    private static File logFile = null;
    private static boolean scrubSensitiveInformation = false;
    private static OutputType outputType = OutputType.PLAIN_TEXT;

    public static void setVerboseLevel(int verboseLevel) {
        LogConfigurator.verboseLevel = verboseLevel;
    }

    public static void setLogFile(File logFile) {
        LogConfigurator.logFile = logFile;
    }

    public static void setScrubSensitiveInformation(final boolean scrubSensitiveInformation) {
        LogConfigurator.scrubSensitiveInformation = scrubSensitiveInformation;
    }

    public static void setOutputType(final OutputType outputType) {
        LogConfigurator.outputType = outputType;
    }

    @Override
    public ExecutionStatus configure(LoggerContext lc) {
        final var rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);

        final var defaultLevel = verboseLevel > 1 ? Level.ALL : verboseLevel > 0 ? Level.INFO : Level.WARN;
        rootLogger.setLevel(defaultLevel);

        final var consoleLayout = verboseLevel == 0 || logFile != null
                ? createSimpleLoggingLayout(lc)
                : createDetailedLoggingLayout(lc);
        final var consoleAppender = createLoggingConsoleAppender(lc, createLayoutWrappingEncoder(consoleLayout));

        if(outputType == OutputType.PLAIN_TEXT) {
            rootLogger.addAppender(consoleAppender);
        }

        lc.getLogger("org.asamk").setLevel(verboseLevel > 1 ? Level.ALL : verboseLevel > 0 ? Level.DEBUG : Level.INFO);
        lc.getLogger("com.zaxxer.hikari.pool.PoolBase")
                .setLevel(verboseLevel > 2 ? Level.ALL : verboseLevel > 1 ? Level.INFO : Level.WARN);
        lc.getLogger("org.sqlite.core.NativeDB")
                .setLevel(verboseLevel > 3 ? Level.ALL : verboseLevel > 1 ? Level.INFO : Level.WARN);

        if (logFile != null && outputType != OutputType.PLAIN_TEXT) {
            consoleAppender.addFilter(new Filter<>() {
                @Override
                public FilterReply decide(final ILoggingEvent event) {
                    return !"LibSignal".equals(event.getLoggerName()) && (
                            event.getLevel().isGreaterOrEqual(Level.WARN) || (
                                    event.getLevel().isGreaterOrEqual(Level.INFO) && event.getLoggerName()
                                            .startsWith("org.asamk")
                            )
                    )

                            ? FilterReply.NEUTRAL : FilterReply.DENY;
                }
            });

            final var fileLayout = createDetailedLoggingLayout(lc);
            final var fileAppender = createLoggingFileAppender(lc, createLayoutWrappingEncoder(fileLayout));
            rootLogger.addAppender(fileAppender);
        }
        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
    }

    private ConsoleAppender<ILoggingEvent> createLoggingConsoleAppender(
            final LoggerContext lc, final LayoutWrappingEncoder<ILoggingEvent> layoutEncoder
    ) {
        return new ConsoleAppender<>() {{
            setContext(lc);
            setName("console");
            setTarget("System.err");
            setEncoder(layoutEncoder);
            start();
        }};
    }

    private FileAppender<ILoggingEvent> createLoggingFileAppender(
            final LoggerContext lc, final LayoutWrappingEncoder<ILoggingEvent> layoutEncoder
    ) {
        return new FileAppender<>() {{
            setContext(lc);
            setName("file");
            setFile(logFile.getAbsolutePath());
            setEncoder(layoutEncoder);
            start();
        }};
    }

    private LayoutWrappingEncoder<ILoggingEvent> createLayoutWrappingEncoder(final Layout<ILoggingEvent> l) {
        return new LayoutWrappingEncoder<>() {{
            setContext(l.getContext());
            setLayout(l);
        }};
    }

    private PatternLayout createSimpleLoggingLayout(final LoggerContext lc) {
        final var patternLayout = getPatternLayout();
        patternLayout.setPattern("%-5level %logger{0} - %msg%n");
        patternLayout.setContext(lc);
        patternLayout.start();
        return patternLayout;
    }

    private PatternLayout createDetailedLoggingLayout(final LoggerContext lc) {
        final var patternLayout = getPatternLayout();
        patternLayout.setPattern("%d{yyyy-MM-dd'T'HH:mm:ss.SSSXX} [%thread] %-5level %logger{36} - %msg%n");
        patternLayout.setContext(lc);
        patternLayout.start();
        return patternLayout;
    }

    private PatternLayout getPatternLayout() {
        if (scrubSensitiveInformation) {
            return new ScrubberPatternLayout();
        } else {
            return new PatternLayout();
        }
    }
}
