package org.asamk.signal.dbus;

import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public final class DbusMention extends Struct {

    @Position(0)
    public final String name;

    @Position(1)
    public final int start;

    @Position(2)
    public final int length;

    public DbusMention(SignalServiceDataMessage.Mention mention, Manager m) {
        this.name = getLegacyIdentifier(m.resolveSignalServiceAddress(new SignalServiceAddress(mention.getUuid(),
                null)));
        this.start = mention.getStart();
        this.length = mention.getLength();
    }
}
