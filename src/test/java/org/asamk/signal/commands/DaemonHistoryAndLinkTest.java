package org.asamk.signal.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.api.HistoryExportResult;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaemonHistoryAndLinkTest {

    @Test
    void daemonLinkPassesHistoryFlagToBothProvisioningSteps() throws Exception {
        final var started = new AtomicBoolean();
        final var finished = new AtomicBoolean();
        final var uri = URI.create("sgnl://linkdevice?uuid=test&pub_key=key");
        final var provisioning = (ProvisioningManager) Proxy.newProxyInstance(
                ProvisioningManager.class.getClassLoader(), new Class<?>[]{ProvisioningManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "finishDeviceLink" -> {
                        finished.set(Boolean.TRUE.equals(args[1]));
                        yield "+15551234567";
                    }
                    default -> null;
                });
        final var account = ManagerMock.create("+15551234567");
        final var multi = (MultiAccountManager) Proxy.newProxyInstance(
                MultiAccountManager.class.getClassLoader(), new Class<?>[]{MultiAccountManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNewProvisioningDeviceLinkUri" -> {
                        started.set(Boolean.TRUE.equals(args[0]));
                        yield uri;
                    }
                    case "getProvisioningManagerFor" -> provisioning;
                    case "getManager" -> account;
                    default -> null;
                });
        final var startResult = new AtomicReference<Object>();
        new StartLinkCommand().handleCommand(new StartLinkCommand.StartLinkParams(true), multi, startResult::set);
        assertEquals(uri.toString(), new ObjectMapper().valueToTree(startResult.get()).get("deviceLinkUri").asText());
        final var finishResult = new AtomicReference<Object>();
        new FinishLinkCommand().handleCommand(
                new FinishLinkCommand.FinishLinkParams(uri.toString(), "dev", true), multi, finishResult::set);
        assertTrue(started.get());
        assertTrue(finished.get());
        assertEquals("+15551234567", new ObjectMapper().valueToTree(finishResult.get()).get("number").asText());
    }

    @Test
    void daemonHistoryIsPagedAndLeavesArchiveForReplay() throws Exception {
        final var retained = new AtomicBoolean();
        final var base = ManagerMock.create("+15551234567");
        final var manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("exportHistory")) {
                        retained.set(!Boolean.TRUE.equals(args[1]));
                        final var handler = (Manager.ReceiveMessageHandler) args[0];
                        for (int i = 0; i < 3; i++) {
                            handler.handleMessage(null, null);
                        }
                        return new HistoryExportResult(3, 1);
                    }
                    return method.invoke(base, args);
                });
        final var first = exportPage(manager, 0, 2);
        final var last = exportPage(manager, 2, 2);
        final var replayed = exportPage(manager, 0, 2);
        final var exhausted = exportPage(manager, 3, 2);
        assertTrue(retained.get());
        assertEquals(2, first.get("messages").size());
        assertTrue(first.get("hasMore").asBoolean());
        assertEquals(1, last.get("messages").size());
        assertFalse(last.get("hasMore").asBoolean());
        assertEquals(first, replayed);
        assertEquals(0, exhausted.get("messages").size());
        assertFalse(exhausted.get("hasMore").asBoolean());
        assertEquals(3, first.get("exportedMessages").asInt());
        assertEquals(1, first.get("skippedRecords").asInt());
        assertFalse(first.get("messages").get(0).has("envelope"));
    }

    private static JsonNode exportPage(final Manager manager, final int offset, final int limit) throws Exception {
        final var output = new AtomicReference<Object>();
        new ExportHistoryCommand().handleCommand(new ExportHistoryCommand.ExportHistoryParams(offset, limit),
                manager, output::set);
        return new ObjectMapper().valueToTree(output.get());
    }
}
