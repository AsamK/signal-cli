package org.asamk.signal.manager;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Objects;

interface HandleAction {

    void execute(Manager m) throws Throwable;
}

class SendReceiptAction implements HandleAction {

    private final SignalServiceAddress address;
    private final long timestamp;

    public SendReceiptAction(final SignalServiceAddress address, final long timestamp) {
        this.address = address;
        this.timestamp = timestamp;
    }

    @Override
    public void execute(Manager m) throws Throwable {
        m.sendReceipt(address, timestamp);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SendReceiptAction that = (SendReceiptAction) o;
        return timestamp == that.timestamp && address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, timestamp);
    }
}

class SendSyncContactsAction implements HandleAction {

    private static final SendSyncContactsAction INSTANCE = new SendSyncContactsAction();

    private SendSyncContactsAction() {
    }

    public static SendSyncContactsAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Manager m) throws Throwable {
        m.sendContacts();
    }
}

class SendSyncGroupsAction implements HandleAction {

    private static final SendSyncGroupsAction INSTANCE = new SendSyncGroupsAction();

    private SendSyncGroupsAction() {
    }

    public static SendSyncGroupsAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Manager m) throws Throwable {
        m.sendGroups();
    }
}

class SendSyncBlockedListAction implements HandleAction {

    private static final SendSyncBlockedListAction INSTANCE = new SendSyncBlockedListAction();

    private SendSyncBlockedListAction() {
    }

    public static SendSyncBlockedListAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Manager m) throws Throwable {
        m.sendBlockedList();
    }
}

class SendGroupInfoRequestAction implements HandleAction {

    private final SignalServiceAddress address;
    private final GroupIdV1 groupId;

    public SendGroupInfoRequestAction(final SignalServiceAddress address, final GroupIdV1 groupId) {
        this.address = address;
        this.groupId = groupId;
    }

    @Override
    public void execute(Manager m) throws Throwable {
        m.sendGroupInfoRequest(groupId, address);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SendGroupInfoRequestAction that = (SendGroupInfoRequestAction) o;

        if (!address.equals(that.address)) return false;
        return groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + groupId.hashCode();
        return result;
    }
}

class SendGroupUpdateAction implements HandleAction {

    private final SignalServiceAddress address;
    private final GroupIdV1 groupId;

    public SendGroupUpdateAction(final SignalServiceAddress address, final GroupIdV1 groupId) {
        this.address = address;
        this.groupId = groupId;
    }

    @Override
    public void execute(Manager m) throws Throwable {
        m.sendUpdateGroupMessage(groupId, address);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SendGroupUpdateAction that = (SendGroupUpdateAction) o;

        if (!address.equals(that.address)) return false;
        return groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + groupId.hashCode();
        return result;
    }
}
