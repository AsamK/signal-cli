package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.SignalControl;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.commands.CommandHandler;
import org.asamk.signal.commands.ListGroupsCommand;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupMember;
import org.asamk.signal.manager.api.GroupPermission;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.Recipient;
import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.internal.MultiAccountManagerImpl;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberlessDbusTest {

    private static final String NUMBER = "+12025550123";
    private static final String ACI = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_ACI = "22222222-2222-4222-8222-222222222222";
    private static final String THIRD_ACI = "33333333-3333-4333-8333-333333333333";
    private static final String NUMBERLESS_PATH = "/org/asamk/Signal/11111111_1111_4111_8111_111111111111";
    private static final String NUMBERED_PATH = "/org/asamk/Signal/_12025550123";

    @Test
    void resolvesNumberlessAndNumberedAccountsWithoutChangingNumberedPaths() {
        final var numberless = new TestAccount(null, ACI);
        final var numbered = new TestAccount(NUMBER, OTHER_ACI);
        final var accounts = new MultiAccountManagerImpl(List.of(numberless.manager, numbered.manager), null);
        final var control = new DbusSignalControlImpl(accounts, DbusConfig.getObjectPath());

        assertEquals(Set.of(new DBusPath(NUMBERLESS_PATH), new DBusPath(NUMBERED_PATH)),
                Set.copyOf(control.listAccounts()));
        assertEquals(NUMBERLESS_PATH, control.getAccount(ACI).getPath());
        assertEquals(NUMBERED_PATH, control.getAccount(NUMBER).getPath());
        assertEquals(NUMBERED_PATH, control.getAccount(OTHER_ACI).getPath());
        assertThrows(SignalControl.Error.Failure.class, () -> control.getAccount(THIRD_ACI));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = NUMBER)
    void mapsOptionalPhoneNumbersAndAddressesSelfByAnAvailableIdentifier(final String number) throws Exception {
        final var account = new TestAccount(number, ACI);
        final var signal = new DbusSignalImpl(account.manager, null, DbusConfig.getObjectPath(), true);
        final var client = new DbusManagerImpl(signal, null, "org.asamk.Signal.Test");

        assertEquals(number == null ? "" : number, signal.getSelfNumber());
        assertEquals(number, client.getSelfNumber());
        assertEquals(number == null ? ACI : number, client.getSelfIdentifier());
        checkSelfOperations(client,
                account,
                number == null
                        ? new RecipientIdentifier.Uuid(UUID.fromString(ACI))
                        : new RecipientIdentifier.Number(number));
    }

    // Run with: dbus-run-session -- ./gradlew --no-daemon :test --tests '*NumberlessDbusTest*' --rerun-tasks
    @Nested
    @Timeout(20)
    @EnabledIfEnvironmentVariable(named = "DBUS_SESSION_BUS_ADDRESS", matches = ".+")
    class SessionBus {

        @Test
        void exportsMixedAccountsRoutesAciSelectorsAndRemovesOnlyTheClosedAccount() throws Exception {
            final var numberless = new TestAccount(null, ACI);
            final var numbered = new TestAccount(NUMBER, OTHER_ACI);
            final var secondNumberless = new TestAccount(null, THIRD_ACI);
            final var accounts = new MultiAccountManagerImpl(List.of(numberless.manager,
                    numbered.manager,
                    secondNumberless.manager), null);
            final var busname = newBusname();
            try (final var server = new DbusHandler(false,
                    busname,
                    accounts,
                    true); final var connection = DBusConnectionBuilder.forSessionBus().withShared(false).build()) {
                server.init();
                final var control = connection.getRemoteObject(busname,
                        DbusConfig.getObjectPath(),
                        SignalControl.class);
                assertEquals(Set.of(NUMBERLESS_PATH, NUMBERED_PATH, DbusConfig.getObjectPath(THIRD_ACI)),
                        control.listAccounts().stream().map(DBusPath::getPath).collect(Collectors.toSet()));
                assertEquals(NUMBERED_PATH, control.getAccount(OTHER_ACI).getPath());
                assertEquals(NUMBERED_PATH, control.getAccount(NUMBER).getPath());
                assertEquals(NUMBERLESS_PATH, control.getAccount(ACI).getPath());
                assertEquals(ACI, selectAccount(ACI, connection, busname));
                assertEquals(OTHER_ACI, selectAccount(OTHER_ACI, connection, busname));
                assertEquals(OTHER_ACI, selectAccount(NUMBER, connection, busname));
                assertThrows(UserErrorException.class, () -> selectAccount(null, connection, busname));

                final var client = new DbusMultiAccountManagerImpl(control, connection, busname);
                assertEquals(3, client.getManagers().size());
                assertEquals(2, client.getManagers().stream().filter(m -> m.getSelfNumber() == null).count());
                assertNull(client.getManager(ACI).getSelfNumber());
                assertEquals(NUMBER, client.getManager(OTHER_ACI).getSelfNumber());

                final var removed = connection.getRemoteObject(busname, NUMBERLESS_PATH, Signal.class);
                numberless.manager.close();
                assertEquals(2, control.listAccounts().size());
                assertThrows(SignalControl.Error.Failure.class, () -> control.getAccount(ACI));
                assertThrows(DBusExecutionException.class, removed::getSelfACI);
                assertEquals(THIRD_ACI, client.getManager(THIRD_ACI).getSelfACI());
                assertEquals(NUMBER, client.getManager(NUMBER).getSelfNumber());
            }
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = NUMBER)
        void selectsSingleAccountDaemonsImplicitlyAndByAci(final String number) throws Exception {
            final var account = new TestAccount(number, ACI);
            final var busname = newBusname();
            try (final var server = new DbusHandler(false,
                    busname,
                    account.manager,
                    true); final var connection = DBusConnectionBuilder.forSessionBus().withShared(false).build()) {
                server.init();
                assertEquals(ACI, selectAccount(null, connection, busname));
                assertEquals(ACI, selectAccount(ACI, connection, busname));
                if (number != null) {
                    assertEquals(ACI, selectAccount(number, connection, busname));
                }
                assertThrows(UserErrorException.class, () -> selectAccount(OTHER_ACI, connection, busname));
            }
        }

        @Test
        void preservesAciAddressesInContactsGroupMembersAndIdentities() throws Exception {
            final var account = new TestAccount(null, ACI);
            final var contact = new RecipientAddress(UUID.fromString(OTHER_ACI));
            final var numberedContact = new RecipientAddress(NUMBER);
            account.recipients = List.of(Recipient.newBuilder().withAddress(contact).build(),
                    Recipient.newBuilder().withAddress(numberedContact).build());
            account.identities = List.of(new Identity(contact,
                    new byte[]{1},
                    "test",
                    new byte[]{2},
                    TrustLevel.TRUSTED_UNVERIFIED,
                    123));
            final var groupId = GroupId.unknownVersion(new byte[32]);
            account.groups = List.of(new Group(groupId,
                    "Test group",
                    "",
                    null,
                    Set.of(new GroupMember(contact, true, null, null),
                            new GroupMember(numberedContact, false, null, null)),
                    Set.of(contact),
                    Set.of(contact),
                    Set.of(contact),
                    false,
                    0,
                    GroupPermission.EVERY_MEMBER,
                    GroupPermission.EVERY_MEMBER,
                    GroupPermission.EVERY_MEMBER,
                    true,
                    true,
                    false));
            final var busname = newBusname();
            try (final var server = new DbusHandler(false,
                    busname,
                    account.manager,
                    true); final var connection = DBusConnectionBuilder.forSessionBus().withShared(false).build()) {
                server.init();
                final var signal = connection.getRemoteObject(busname, DbusConfig.getObjectPath(), Signal.class);
                assertEquals(List.of(NUMBER), signal.listNumbers());
                assertEquals(Set.of(OTHER_ACI, NUMBER), Set.copyOf(signal.listRecipientIdentifiers()));
                try (final var client = new DbusManagerImpl(signal, connection, busname)) {
                    final var contacts = client.getRecipients(false,
                            Optional.empty(),
                            Set.of(new RecipientIdentifier.Uuid(UUID.fromString(OTHER_ACI))),
                            Optional.empty());
                    assertEquals(1, contacts.size());
                    assertEquals(contact, contacts.getFirst().getAddress());
                    assertEquals(contact, client.getIdentities().getFirst().recipient());
                    final var group = client.getGroups().getFirst();
                    assertEquals(Set.of(contact, numberedContact),
                            group.members().stream().map(GroupMember::recipientAddress).collect(Collectors.toSet()));
                    assertEquals(Set.of(contact), group.pendingMembers());
                    assertEquals(Set.of(contact), group.requestingMembers());
                    assertEquals(Set.of(contact), group.bannedMembers());
                }
            }
        }

        @Test
        void sendsAndReceivesNumberlessDirectGroupAndSelfMessagesOverDbus() throws Exception {
            final var account = new TestAccount(null, ACI);
            final var accounts = new MultiAccountManagerImpl(List.of(account.manager), null);
            final var busname = newBusname();
            try (final var server = new DbusHandler(false,
                    busname,
                    accounts,
                    true); final var connection = DBusConnectionBuilder.forSessionBus().withShared(false).build()) {
                server.init();
                assertEquals(ACI, selectAccount(null, connection, busname));
                final var signal = connection.getRemoteObject(busname, NUMBERLESS_PATH, Signal.class);
                assertEquals("", signal.getSelfNumber());
                final var groupId = GroupId.unknownVersion(new byte[32]);
                assertEquals(42, signal.sendMessage("Direct", List.of(), OTHER_ACI));
                assertEquals(42, signal.sendGroupMessage("Group", List.of(), groupId.serialize()));
                assertEquals(42, signal.sendNoteToSelfMessage("Self", List.of()));
                assertEquals(Set.of(new RecipientIdentifier.Uuid(UUID.fromString(OTHER_ACI))),
                        account.calls.get(0).arguments().get(1));
                assertEquals(Set.of(new RecipientIdentifier.Group(groupId)), account.calls.get(1).arguments().get(1));
                assertEquals(Set.of(RecipientIdentifier.NoteToSelf.INSTANCE), account.calls.get(2).arguments().get(1));
                assertThrows(Signal.Error.InvalidNumber.class,
                        () -> signal.sendMessage("Invalid local number", List.of(), "2025550124"));

                account.calls.clear();
                try (final var client = new DbusManagerImpl(signal, connection, busname)) {
                    checkSelfOperations(client, account, new RecipientIdentifier.Uuid(UUID.fromString(ACI)));
                    final var received = new LinkedBlockingQueue<MessageEnvelope>();
                    client.addReceiveHandler((envelope, error) -> received.add(envelope));
                    for (final var group : List.of(Optional.<GroupId>empty(), Optional.of(groupId))) {
                        account.emit(messageEnvelope(group));
                        final var envelope = received.poll(5, TimeUnit.SECONDS);
                        assertNotNull(envelope);
                        assertEquals(OTHER_ACI, envelope.sourceAddress().orElseThrow().aci().orElseThrow());
                        assertEquals(Optional.empty(), envelope.sourceAddress().orElseThrow().number());
                        assertEquals("Incoming", envelope.data().orElseThrow().body().orElseThrow());
                        assertEquals(Optional.of(ACI),
                                envelope.data().orElseThrow().mentions().getFirst().recipient().aci());
                        assertEquals(group,
                                envelope.data()
                                        .orElseThrow()
                                        .groupContext()
                                        .map(MessageEnvelope.Data.GroupContext::groupId));
                    }
                }
                assertEquals(0, account.receivers.size());
            }
        }
    }

    private static void checkSelfOperations(
            final Manager client,
            final TestAccount account,
            final RecipientIdentifier.Single self
    ) throws Exception {
        final Set<RecipientIdentifier> recipients = Set.of(RecipientIdentifier.NoteToSelf.INSTANCE);
        client.sendTypingMessage(TypingAction.START, recipients);
        client.sendRemoteDeleteMessage(123, recipients);
        client.sendMessageReaction("\uD83D\uDC4D", false, self, 123, recipients, false, false);
        assertEquals(List.of("sendTypingMessage", "sendRemoteDeleteMessage", "sendMessageReaction"),
                account.calls.stream().map(Call::name).toList());
        assertEquals(Set.of(self), account.calls.get(0).arguments().get(1));
        assertEquals(Set.of(self), account.calls.get(1).arguments().get(1));
        assertEquals(Set.of(self), account.calls.get(2).arguments().get(4));
    }

    private static String selectAccount(
            final String account,
            final DBusConnection connection,
            final String busname
    ) throws Exception {
        final var selected = new AtomicReference<String>();
        final var handler = new CommandHandler(null, null) {
            @Override
            public void handleLocalCommand(final LocalCommand command, final Manager manager) {
                selected.set(manager.getSelfACI());
            }
        };
        DbusCommandHandler.handleCommand(new ListGroupsCommand(), account, connection, busname, handler);
        return selected.get();
    }

    private static String newBusname() {
        return "org.asamk.Signal.Test" + UUID.randomUUID().toString().replace("-", "");
    }

    private static MessageEnvelope messageEnvelope(final Optional<GroupId> groupId) {
        final var data = new MessageEnvelope.Data(123,
                groupId.map(id -> new MessageEnvelope.Data.GroupContext(id, false, 0)),
                Optional.empty(),
                Optional.empty(),
                Optional.of("Incoming"),
                0,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(new MessageEnvelope.Data.Mention(new RecipientAddress(UUID.fromString(ACI)), 0, 1)),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        return new MessageEnvelope(Optional.of(new RecipientAddress(UUID.fromString(OTHER_ACI))),
                1,
                123,
                0,
                0,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.of(data),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private record Call(String name, List<Object> arguments) {}

    private static final class TestAccount {

        private final Manager manager;
        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private final List<Runnable> closedListeners = new CopyOnWriteArrayList<>();
        private final List<Manager.ReceiveMessageHandler> receivers = new CopyOnWriteArrayList<>();
        private List<Recipient> recipients = List.of();
        private List<Group> groups = List.of();
        private List<Identity> identities = List.of();

        private TestAccount(final String number, final String aci) {
            manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                    new Class<?>[]{Manager.class},
                    (proxy, method, args) -> {
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        return switch (method.getName()) {
                            case "getSelfNumber" -> number;
                            case "getSelfACI" -> aci;
                            case "getLinkedDevices" -> List.of();
                            case "getGroups" -> groups;
                            case "getGroup" -> groups.getFirst();
                            case "getIdentities" -> identities;
                            case "getRecipients" -> recipients;
                            case "isContactBlocked" -> false;
                            case "getContactOrProfileName" -> "Test contact";
                            case "addAddressChangedListener" -> null;
                            case "addClosedListener" -> {
                                closedListeners.add((Runnable) args[0]);
                                yield null;
                            }
                            case "close" -> {
                                closedListeners.forEach(Runnable::run);
                                closedListeners.clear();
                                yield null;
                            }
                            case "addReceiveHandler" -> {
                                receivers.add((Manager.ReceiveMessageHandler) args[0]);
                                yield null;
                            }
                            case "removeReceiveHandler" -> {
                                receivers.remove(args[0]);
                                yield null;
                            }
                            case "sendMessage", "sendTypingMessage", "sendRemoteDeleteMessage",
                                 "sendMessageReaction" -> {
                                calls.add(new Call(method.getName(), List.of(args)));
                                yield new SendMessageResults(42, Map.of());
                            }
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    });
        }

        private void emit(final MessageEnvelope envelope) {
            receivers.forEach(receiver -> receiver.handleMessage(envelope, null));
        }
    }
}
