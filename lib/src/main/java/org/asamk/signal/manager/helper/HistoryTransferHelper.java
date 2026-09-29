package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.HistoryExportResult;
import org.signal.core.models.backup.MessageBackupKey;
import org.signal.libsignal.messagebackup.BackupJsonExporter;
import org.signal.network.NetworkResult;
import org.whispersystems.signalservice.api.link.TransferArchiveResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.concurrent.TimeoutException;

public final class HistoryTransferHelper {

    private static final Duration WAIT_TIMEOUT = Duration.ofMinutes(5);
    private final Context context;
    private final HistoryTransferStore store;

    public HistoryTransferHelper(final Context context) {
        this.context = context;
        this.store = new HistoryTransferStore(context.getAccount());
    }

    public DownloadOutcome saveAndDownload(final MessageBackupKey ephemeralBackupKey) throws IOException {
        store.createPending(context.getAccount().getAci().toString(), ephemeralBackupKey.getValue());
        return ensureArchiveAvailable();
    }

    public HistoryExportResult export(
            final Manager.ReceiveMessageHandler handler,
            final boolean deleteAfterExport
    ) throws IOException {
        final var metadata = store.readMetadata();
        if (!context.getAccount().getAci().toString().equals(metadata.aci())) {
            throw new IOException("History transfer belongs to another account");
        }
        if (!store.hasArchive(metadata)) {
            final var outcome = ensureArchiveAvailable();
            if (outcome == DownloadOutcome.DECLINED) {
                throw new IOException("The primary device continued without transferring history");
            }
        }

        final MessageBackupKey.BackupKeyMaterial keyMaterial;
        try {
            keyMaterial = new MessageBackupKey(metadata.decodedBackupKey())
                    .deriveBackupSecrets(context.getAccount().getAci(), null);
        } catch (IllegalArgumentException e) {
            throw new IOException("History transfer metadata contains an invalid backup key", e);
        }
        var exported = 0;
        var skipped = 0;
        final var converter = new HistoryMessageConverter(context.getAccount());

        try (final var reader = EncryptedHistoryReader.open(store, metadata, keyMaterial)) {
            final var backupInfo = reader.next();
            if (backupInfo == null) {
                throw new IOException("History archive is missing its backup header");
            }
            final var started = BackupJsonExporter.start(backupInfo.payload());
            try (final var exporter = started.getFirst()) {
                EncryptedHistoryReader.DelimitedMessage frame;
                while ((frame = reader.next()) != null) {
                    final var results = exporter.exportFrames(frame.framed());
                    if (results.size() != 1) {
                        throw new IOException("History decoder returned an unexpected frame count");
                    }
                    final var result = results.getFirst();
                    if (result.getErrorMessage() != null) {
                        throw new IOException("History archive validation failed: " + result.getErrorMessage());
                    }
                    if (result.getLine() == null) {
                        skipped++;
                        continue;
                    }
                    final var converted = converter.accept(result.getLine());
                    if (converted.skipped()) {
                        skipped++;
                    } else if (converted.envelope() != null) {
                        handler.handleMessage(converted.envelope(), null);
                        exported++;
                    }
                }
                final var finishError = exporter.finishExport();
                if (finishError != null) {
                    throw new IOException("History archive validation failed: " + finishError);
                }
            }
        } catch (org.signal.libsignal.messagebackup.ValidationError e) {
            throw new IOException("History archive is structurally invalid", e);
        }

        if (deleteAfterExport) {
            store.delete();
        }
        return new HistoryExportResult(exported, skipped);
    }

    private DownloadOutcome ensureArchiveAvailable() throws IOException {
        var metadata = store.readMetadata();
        if (store.hasArchive(metadata)) {
            return DownloadOutcome.AVAILABLE;
        }
        if (metadata.cdn() == null || metadata.cdnKey() == null) {
            final var archive = waitForArchive();
            final var decision = classifyTransferResponse(archive);
            if (decision.declined()) {
                store.delete();
                return DownloadOutcome.DECLINED;
            }
            metadata = store.withArchiveLocation(metadata, decision.cdn(), decision.cdnKey());
        }

        final var resolved = metadata;
        store.writeArchiveFile(resolved,
                path -> downloadArchive(resolved.cdn(), resolved.cdnKey(), path.toFile()));
        return DownloadOutcome.AVAILABLE;
    }

    private TransferArchiveResponse waitForArchive() throws IOException {
        return waitForArchive(this::pollForArchive, WAIT_TIMEOUT, System::nanoTime);
    }

    static TransferArchiveResponse waitForArchive(
            final ArchivePoller poller,
            final Duration timeout,
            final LongSupplier nanoTime
    ) throws IOException {
        final var startedAt = nanoTime.getAsLong();
        while (nanoTime.getAsLong() - startedAt < timeout.toNanos()) {
            final var response = poller.poll();
            if (response != null) {
                return response;
            }
        }
        throw new IOException("Timed out waiting for the primary device history archive",
                new TimeoutException("History transfer timed out"));
    }

    private TransferArchiveResponse pollForArchive() throws IOException {
        final NetworkResult<TransferArchiveResponse> result = context.getDependencies()
                .waitForPrimaryDeviceHistory();
        if (result instanceof NetworkResult.StatusCodeError<?> error && error.getCode() == 204) {
            return null;
        }
        try {
            return result.successOrThrow();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Failed while waiting for the primary device history archive", e);
        }
    }

    static TransferDecision classifyTransferResponse(final TransferArchiveResponse response) throws IOException {
        if (TransferArchiveResponse.ERROR_CONTINUE_WITHOUT_UPLOAD.equals(response.getError())) {
            return new TransferDecision(true, 0, null);
        }
        if (response.getError() != null) {
            throw new IOException("Primary device failed to transfer history: " + response.getError());
        }
        if (!response.getHasArchive() || response.getCdn() == null || response.getKey() == null) {
            throw new IOException("Primary device returned no history archive location");
        }
        return new TransferDecision(false, response.getCdn(), response.getKey());
    }

    private void downloadArchive(final int cdn, final String cdnKey, final java.io.File file) throws IOException {
        try {
            context.getDependencies()
                    .getMessageReceiver()
                    .retrieveLinkAndSyncBackup(cdn, cdnKey, file, null)
                    .successOrThrow();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("History archive download failed", e);
        }
    }

    public enum DownloadOutcome {
        AVAILABLE,
        DECLINED
    }

    record TransferDecision(boolean declined, int cdn, String cdnKey) {
    }

    @FunctionalInterface
    interface ArchivePoller {

        TransferArchiveResponse poll() throws IOException;
    }
}
