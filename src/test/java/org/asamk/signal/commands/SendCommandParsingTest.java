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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SendCommandParsingTest {

    private static final List<String> ATTACHMENTS = List.of("photo.jpg", "portrait.mp4", "other.mp4");

    @Test
    void cliPreservesEmptyAndMissingDimensionPositions() throws Exception {
        final var parser = ArgumentParsers.newFor("signal-cli", DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true).build();
        new SendCommand().attachToSubparser(parser.addSubparsers().addParser("send"));
        final var message = send(parser.parseArgs(new String[]{"send", "--note-to-self",
                "-a", "photo.jpg", "portrait.mp4", "other.mp4", "--attachment-dimensions", "", "1080x1920"}));

        assertEquals(ATTACHMENTS, message.attachments());
        assertEquals(List.of(Message.AttachmentDimensions.UNKNOWN, new Message.AttachmentDimensions(1080, 1920)),
                message.attachmentDimensions());
    }

    @Test
    void jsonRpcPassesDimensionsIntoTheSentMessage() throws Exception {
        final var message = send(namespace(List.of("", "1920x1080")));
        assertEquals(ATTACHMENTS, message.attachments());
        assertEquals(List.of(Message.AttachmentDimensions.UNKNOWN, new Message.AttachmentDimensions(1920, 1080)),
                message.attachmentDimensions());
    }

    @Test
    void omittedDimensionsKeepExistingBehavior() throws Exception {
        assertEquals(List.of(), send(new JsonRpcNamespace(Map.of("noteToSelf", true,
                "attachments", ATTACHMENTS))).attachmentDimensions());
    }

    @Test
    void rejectsMalformedOrExcessDimensionsBeforeSending() {
        for (final var value : List.of("0x1080", "1920x0", "-1x2", "1.5x2", "1920", "2147483648x1")) {
            assertThrows(UserErrorException.class, () -> send(namespace(List.of(value))), value);
        }
        assertThrows(UserErrorException.class, () -> send(namespace(List.of("", "", "", "1x1"))));
    }

    private JsonRpcNamespace namespace(List<String> dimensions) {
        return new JsonRpcNamespace(Map.of("noteToSelf", true, "attachments", ATTACHMENTS,
                "attachmentDimensions", dimensions));
    }

    private Message send(Namespace namespace) throws Exception {
        final var captured = new AtomicReference<Message>();
        final var delegate = ManagerMock.create("+15551234567");
        final var manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) {
                        captured.set((Message) args[0]);
                        return new SendMessageResults(1, Map.of());
                    }
                    return method.invoke(delegate, args);
                });
        new SendCommand().handleCommand(namespace, manager, (JsonWriter) ignored -> {});
        return captured.get();
    }
}
