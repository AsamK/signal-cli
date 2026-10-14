package org.asamk.signal.commands;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.DefaultSettings;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendCommandParsingTest {

    private static final List<String> ATTACHMENTS = List.of("photo.jpg", "portrait.mp4", "other.mp4");
    private static final String BLUR_HASH = "LEHV6nWB2yk8pyo0adR*.7kCMdnj";

    @Test
    void cliPreservesEmptyAndMissingDimensionPositions() throws Exception {
        final var parser = ArgumentParsers.newFor("signal-cli", DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true)
                .build();
        new SendCommand().attachToSubparser(parser.addSubparsers().addParser("send"));
        final var message = send(parser.parseArgs(new String[]{
                "send",
                "--note-to-self",
                "-a",
                "photo.jpg",
                "portrait.mp4",
                "other.mp4",
                "--attachment-dimensions",
                "",
                "1080x1920"
        }));

        assertEquals(ATTACHMENTS, message.attachments());
        assertEquals(Arrays.asList(null, new Message.AttachmentDimensions(1080, 1920)), message.attachmentDimensions());
    }

    @Test
    void jsonRpcPassesDimensionsIntoTheSentMessage() throws Exception {
        final var message = send(namespace(List.of("", "1920x1080")));
        assertEquals(ATTACHMENTS, message.attachments());
        assertEquals(Arrays.asList(null, new Message.AttachmentDimensions(1920, 1080)), message.attachmentDimensions());
    }

    @Test
    void cliPreservesBlurHashPositions() throws Exception {
        final var parser = ArgumentParsers.newFor("signal-cli", DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true)
                .build();
        new SendCommand().attachToSubparser(parser.addSubparsers().addParser("send"));
        final var message = send(parser.parseArgs(new String[]{
                "send",
                "--note-to-self",
                "-a",
                "photo.jpg",
                "portrait.mp4",
                "other.mp4",
                "--attachment-blurhash",
                "",
                BLUR_HASH
        }));

        assertEquals(List.of("", BLUR_HASH), message.attachmentBlurHashes());
    }

    @Test
    void jsonRpcPassesBlurHashesWithDimensions() throws Exception {
        final var message = send(new JsonRpcNamespace(Map.of("noteToSelf",
                true,
                "attachments",
                ATTACHMENTS,
                "attachmentDimensions",
                List.of("", "1920x1080"),
                "attachmentBlurhash",
                List.of("", BLUR_HASH))));

        assertEquals(Arrays.asList(null, new Message.AttachmentDimensions(1920, 1080)), message.attachmentDimensions());
        assertEquals(List.of("", BLUR_HASH), message.attachmentBlurHashes());
    }

    @Test
    void omittedDimensionsKeepExistingBehavior() throws Exception {
        final var message = send(new JsonRpcNamespace(Map.of("noteToSelf", true, "attachments", ATTACHMENTS)));
        assertEquals(List.of(), message.attachmentDimensions());
        assertEquals(List.of(), message.attachmentBlurHashes());
    }

    @Test
    void rejectsMalformedDimensionsBeforeSending() {
        for (final var value : List.of("0x1080", "1920x0", "-1x2", "1.5x2", "1920")) {
            assertThrows(UserErrorException.class, () -> send(namespace(List.of(value))), value);
        }
    }

    @Test
    void rejectsMalformedBlurHashesBeforeSending() {
        // Wrong length for its size digit, an unknown size digit, too short.
        for (final var value : List.of(BLUR_HASH + "0", "!" + BLUR_HASH.substring(1), "00000")) {
            assertThrows(UserErrorException.class, () -> send(blurHashNamespace(List.of(value))), value);
        }
    }

    @Test
    void cliPassesTimestampIntoTheSentMessage() throws Exception {
        final var parser = ArgumentParsers.newFor("signal-cli", DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true)
                .build();
        new SendCommand().attachToSubparser(parser.addSubparsers().addParser("send"));
        final var timestamp = System.currentTimeMillis() - 5000;
        final var message = send(parser.parseArgs(new String[]{
                "send",
                "--note-to-self",
                "-m",
                "hello",
                "--timestamp",
                String.valueOf(timestamp)
        }));

        assertEquals(Optional.of(timestamp), message.timestamp());
    }

    @Test
    void jsonRpcPassesTimestampIntoTheSentMessage() throws Exception {
        final var timestamp = System.currentTimeMillis() - 5000;
        final var message = send(timestampNamespace(timestamp));
        assertEquals(Optional.of(timestamp), message.timestamp());
    }

    @Test
    void jsonRpcAcceptsIntegerTimestamp() throws Exception {
        // JSON-RPC delivers small numbers as Integer
        final var message = send(timestampNamespace(12345));
        assertEquals(Optional.of(12345L), message.timestamp());
    }

    @Test
    void omittedTimestampKeepsExistingBehavior() throws Exception {
        final var message = send(new JsonRpcNamespace(Map.of("noteToSelf", true, "message", "hello")));
        assertEquals(Optional.empty(), message.timestamp());
    }

    @Test
    void rejectsInvalidTimestampsBeforeSending() {
        final var now = System.currentTimeMillis();
        for (final Object value : new Object[]{0L, -1L, now + 86_400_000L, 1.7548e12, "12345"}) {
            assertThrows(UserErrorException.class, () -> send(timestampNamespace(value)), String.valueOf(value));
        }
    }

    @Test
    void rejectsTimestampWithEditTimestamp() {
        final var e = assertThrows(UserErrorException.class,
                () -> send(new JsonRpcNamespace(Map.of("noteToSelf",
                        true,
                        "message",
                        "hello",
                        "timestamp",
                        System.currentTimeMillis() - 5000,
                        "editTimestamp",
                        System.currentTimeMillis() - 10000))));
        assertTrue(e.getMessage().contains("--edit-timestamp"));
    }

    @Test
    void rejectsTimestampWithEndSession() {
        final var e = assertThrows(UserErrorException.class,
                () -> send(new JsonRpcNamespace(Map.of("recipient",
                        "+15550000000",
                        "timestamp",
                        System.currentTimeMillis() - 5000,
                        "endSession",
                        true))));
        assertTrue(e.getMessage().contains("--end-session"));
    }

    // parseMessageTimestamp takes the current time as a parameter, so the bounds can be tested deterministically.

    @Test
    void parseMessageTimestampReturnsEmptyForNull() throws Exception {
        assertEquals(Optional.empty(), SendCommand.parseMessageTimestamp(null, 1000L));
    }

    @Test
    void parseMessageTimestampAcceptsTimestampJustBeforeNow() throws Exception {
        assertEquals(Optional.of(999L), SendCommand.parseMessageTimestamp(999L, 1000L));
    }

    @Test
    void parseMessageTimestampRejectsTimestampNotInThePast() {
        for (final var value : List.of(1000L, 1001L)) {
            final var e = assertThrows(UserErrorException.class,
                    () -> SendCommand.parseMessageTimestamp(value, 1000L));
            assertTrue(e.getMessage().contains("past"), String.valueOf(value));
        }
    }

    @Test
    void parseMessageTimestampRejectsZeroAndNegative() {
        for (final var value : List.of(0L, -1L)) {
            final var e = assertThrows(UserErrorException.class,
                    () -> SendCommand.parseMessageTimestamp(value, 1000L));
            assertTrue(e.getMessage().contains("positive"), String.valueOf(value));
        }
    }

    @Test
    void parseMessageTimestampRejectsNonIntegerTypes() {
        final var outOfRange = BigInteger.valueOf(2).pow(64).add(BigInteger.ONE);
        for (final Object value : new Object[]{outOfRange, 1.7548e12, "12345"}) {
            final var e = assertThrows(UserErrorException.class,
                    () -> SendCommand.parseMessageTimestamp(value, 1000000L));
            assertTrue(e.getMessage().contains("integer"), String.valueOf(value));
        }
    }

    private JsonRpcNamespace timestampNamespace(Object timestamp) {
        return new JsonRpcNamespace(Map.of("noteToSelf", true, "message", "hello", "timestamp", timestamp));
    }

    private JsonRpcNamespace blurHashNamespace(List<String> blurHashes) {
        return new JsonRpcNamespace(Map.of("noteToSelf",
                true,
                "attachments",
                ATTACHMENTS,
                "attachmentBlurhash",
                blurHashes));
    }

    private JsonRpcNamespace namespace(List<String> dimensions) {
        return new JsonRpcNamespace(Map.of("noteToSelf",
                true,
                "attachments",
                ATTACHMENTS,
                "attachmentDimensions",
                dimensions));
    }

    private Message send(Namespace namespace) throws Exception {
        final var captured = new AtomicReference<Message>();
        final var delegate = ManagerMock.create("+15551234567");
        final var manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) {
                        captured.set((Message) args[0]);
                        return new SendMessageResults(1, Map.of());
                    }
                    return method.invoke(delegate, args);
                });
        new SendCommand().handleCommand(namespace, manager, (JsonWriter) ignored -> {
        });
        return captured.get();
    }
}
