package org.asamk.signal.commands;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.DefaultSettings;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputType;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.api.HistoryExportResult;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCommandParsingTest {

    @Test
    void linkKeepsHistoryDisabledByDefault() throws Exception {
        assertFalse(runLink(false));
    }

    @Test
    void linkPassesImportHistoryToBothProvisioningSteps() throws Exception {
        assertTrue(runLink(true));
    }

    @Test
    void exportKeepsTransferredStateByDefault() throws Exception {
        assertFalse(runExport(false));
    }

    @Test
    void exportPassesDeleteAfterExportAndSupportsOnlyJson() throws Exception {
        final var command = new ExportHistoryCommand();
        assertEquals(List.of(OutputType.JSON), command.getSupportedOutputTypes());
        assertTrue(runExport(true));
    }

    private static boolean runLink(final boolean importHistory) throws Exception {
        final var parsed = parse(new LinkCommand(), importHistory ? new String[]{"link", "--import-history"} : new String[]{"link"});
        final var requestedHistory = new AtomicBoolean();
        final var finishedHistory = new AtomicBoolean();
        final var manager = (ProvisioningManager) Proxy.newProxyInstance(ProvisioningManager.class.getClassLoader(),
                new Class<?>[]{ProvisioningManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDeviceLinkUri" -> {
                        requestedHistory.set(args != null && args.length == 1 && Boolean.TRUE.equals(args[0]));
                        yield URI.create("sgnl://linkdevice?uuid=test&pub_key=key");
                    }
                    case "finishDeviceLink" -> {
                        finishedHistory.set(args != null && args.length == 2 && Boolean.TRUE.equals(args[1]));
                        yield "+15551234567";
                    }
                    default -> null;
                });

        new LinkCommand().handleCommand(parsed, manager, NO_OP_PLAIN_WRITER);
        assertEquals(requestedHistory.get(), finishedHistory.get());
        return requestedHistory.get();
    }

    private static boolean runExport(final boolean deleteAfterExport) throws Exception {
        final var parsed = parse(new ExportHistoryCommand(),
                deleteAfterExport
                        ? new String[]{"export-history", "--delete-after-export"}
                        : new String[]{"export-history"});
        final var captured = new AtomicBoolean();
        final var delegate = ManagerMock.create("+15551234567");
        final var manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("exportHistory")) {
                        captured.set(Boolean.TRUE.equals(args[1]));
                        return new HistoryExportResult(0, 0);
                    }
                    return method.invoke(delegate, args);
                });

        new ExportHistoryCommand().handleCommand(parsed, manager, (JsonWriter) ignored -> {
        });
        return captured.get();
    }

    private static Namespace parse(final CliCommand command, final String[] arguments) throws Exception {
        final var parser = ArgumentParsers.newFor("signal-cli", DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true)
                .build();
        command.attachToSubparser(parser.addSubparsers().addParser(command.getName()));
        return parser.parseArgs(arguments);
    }

    private static final PlainTextWriter NO_OP_PLAIN_WRITER = new PlainTextWriter() {
        @Override
        public void println(final String format, final Object... args) {
        }

        @Override
        public PlainTextWriter indentedWriter() {
            return this;
        }
    };
}
