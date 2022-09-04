package org.asamk.signal.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class ScrubberPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return Scrubber.scrub(super.doLayout(event)).toString();
    }
}
