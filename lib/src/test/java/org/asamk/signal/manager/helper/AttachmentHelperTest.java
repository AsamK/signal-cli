package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentHelperTest {

    private static final String PAYLOAD = Base64.getEncoder()
            .encodeToString("private attachment marker ".repeat(200).getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path tempDir;

    @Test
    void preparationFailureIsAttachmentInvalidAndNothingIsUploaded() {
        final var uploader = new FakeUploader();
        uploader.failSpecOnCall = 1;
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(uploader).uploadAttachments(List.of(inline("one"), inline("two"))));

        assertEquals("inline attachment #1: spec failed", error.getMessage());
        assertRedacted(error);
        assertEquals(0, uploader.uploads.size());
    }

    @Test
    void uploadFailureOnSecondInlineAttachmentStopsBeforeTheThird() {
        final var uploader = new FakeUploader();
        uploader.failUploadOnCall = 2;
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(uploader).uploadAttachments(List.of(inline("one"), inline("two"), inline("three"))));

        assertEquals("inline attachment #2: upload failed", error.getMessage());
        assertSame(uploader.uploadFailure, error.getCause());
        assertRedacted(error);
        assertEquals(List.of("one", "two"), uploader.uploads);
    }

    @Test
    void uploadFailureOnSecondFileAttachmentNamesThePath() throws IOException {
        final var files = List.of(file("a.txt"), file("b.txt"), file("c.txt"));
        final var uploader = new FakeUploader();
        uploader.failUploadOnCall = 2;
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(uploader).uploadAttachments(files));

        assertEquals(files.get(1) + ": upload failed", error.getMessage());
        assertInstanceOf(SocketException.class, error.getCause());
        assertEquals(List.of("a.txt", "b.txt"), uploader.uploads);
    }

    @Test
    void successfulUploadsKeepInputOrder() throws Exception {
        final var uploader = new FakeUploader();
        final var pointers = helper(uploader).uploadAttachments(List.of(inline("one"),
                file("two.txt"),
                inline("three")));

        assertEquals(List.of("one", "two.txt", "three"),
                pointers.stream()
                        .map(SignalServiceAttachment::asPointer)
                        .map(p -> p.getFileName().orElseThrow())
                        .toList());
        assertEquals(3, uploader.specs);
    }

    @Test
    void malformedInlineAttachmentDoesNotExposePayload() {
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(new FakeUploader()).uploadAttachments(List.of("data:application/octet-stream;base64,"
                        + PAYLOAD
                        + "!")));

        assertEquals("inline attachment #1: Invalid data URI", error.getMessage());
        assertRedacted(error);
    }

    @Test
    void upperCaseInlineAttachmentIsNotTreatedAsAFilePath() throws Exception {
        // Longer than PATH_MAX, so canonicalizing it as a file path would fail
        final var uploader = new FakeUploader();
        final var pointers = helper(uploader).uploadAttachments(List.of(
                "DATA:application/octet-stream;filename=upper;base64," + PAYLOAD));

        assertEquals(1, pointers.size());
        assertEquals(List.of("upper"), uploader.uploads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"data:text/plain;base64", "data://", "data:;base64//", "DATA://"})
    void inlineAttachmentWithoutCommaIsNotExposed(final String prefix) {
        // Short enough to reach file opening if inline input is incorrectly treated as a path.
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(new FakeUploader()).uploadAttachments(List.of(prefix + PAYLOAD.substring(0, 80))));

        assertEquals("inline attachment #1: Invalid data URI", error.getMessage());
        assertRedacted(error);
    }

    @Test
    void longInlineAttachmentWithoutCommaIsRejected() {
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(new FakeUploader()).uploadAttachments(List.of("data:" + PAYLOAD.repeat(160))));

        assertEquals("inline attachment #1: Invalid data URI", error.getMessage());
        assertRedacted(error);
    }

    @Test
    void defaultUploadUsesTheSameErrorContract() {
        final var uploader = new FakeUploader();
        uploader.failUploadOnCall = 1;
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(uploader).uploadAttachment(inline("image")));

        assertEquals("inline attachment: upload failed", error.getMessage());
        assertSame(uploader.uploadFailure, error.getCause());
        assertRedacted(error);
    }

    @Test
    void stickerPreparationFailureIsLabelled() {
        final var uploader = new FakeUploader();
        uploader.failSpecOnCall = 1;
        final var stream = new ByteArrayInputStream(new byte[4]);
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(uploader).createStickerAttachmentStream(new StreamDetails(stream, "image/webp", 4)));

        assertEquals("sticker: spec failed", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void longTextFailureNeverEchoesTheText(final boolean preparation) {
        final var uploader = new FakeUploader();
        uploader.failSpecOnCall = preparation ? 1 : 0;
        uploader.failUploadOnCall = preparation ? 0 : 1;
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> helper(uploader).uploadLongTextAttachment(PAYLOAD.getBytes(StandardCharsets.UTF_8)));

        assertEquals("long text attachment: " + (preparation ? "spec" : "upload") + " failed", error.getMessage());
        assertRedacted(error);
        assertInstanceOf(SocketException.class, error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quote thumbnail #1", "link preview image #1"})
    void imageFailuresUseTheirLabel(final String label) {
        final var uploader = new FakeUploader();
        uploader.failSpecOnCall = 1;
        final var helper = helper(uploader);
        final var preparationError = assertThrows(AttachmentInvalidException.class,
                () -> helper.uploadAttachment(inline("image"), label));
        assertEquals(label + ": spec failed", preparationError.getMessage());
        assertRedacted(preparationError);
        assertEquals(0, uploader.uploads.size());

        uploader.failUploadOnCall = 1;
        final var uploadError = assertThrows(AttachmentInvalidException.class,
                () -> helper.uploadAttachment(inline("image"), label));
        assertEquals(label + ": upload failed", uploadError.getMessage());
        assertRedacted(uploadError);
    }

    @Test
    void filesInTheDataDirectoryAreStillRejected() throws IOException {
        final var dataDir = Files.createDirectory(tempDir.resolve("data")).toFile();
        final var secret = new File(dataDir, "account.db");
        Files.writeString(secret.toPath(), "secret");
        final var uploader = new FakeUploader();
        final var error = assertThrows(AttachmentInvalidException.class,
                () -> new AttachmentHelper(uploader, () -> dataDir).uploadAttachments(List.of(secret.getPath())));

        assertEquals(secret.getPath() + ": Attaching files from the signal-cli data directory is not allowed",
                error.getMessage());
        assertEquals(0, uploader.specs);
    }

    @Test
    void invalidWithoutCauseMessageFallsBackToClassName() {
        final var cause = new SocketException();
        final var error = AttachmentHelper.invalid("inline attachment #1", cause);

        assertEquals("inline attachment #1: java.net.SocketException", error.getMessage());
        assertSame(cause, error.getCause());
    }

    private AttachmentHelper helper(final FakeUploader uploader) {
        return new AttachmentHelper(uploader, () -> tempDir.resolve("data").toFile());
    }

    private String file(final String name) throws IOException {
        final var path = tempDir.resolve(name);
        Files.writeString(path, PAYLOAD);
        return path.toString();
    }

    private static String inline(final String filename) {
        return "data:application/octet-stream;filename=" + filename + ";base64," + PAYLOAD;
    }

    private static void assertRedacted(final AttachmentInvalidException error) {
        final var trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains(PAYLOAD.substring(0, 32)));
    }

    private static final class FakeUploader implements AttachmentHelper.Uploader {

        final IOException uploadFailure = new SocketException("upload failed");
        int failSpecOnCall;
        int failUploadOnCall;
        int specs;
        final List<String> uploads = new ArrayList<>();

        @Override
        public ResumableUploadSpec getResumableUploadSpec(final long ciphertextLength) throws IOException {
            if (++specs == failSpecOnCall) {
                throw new SocketException("spec failed");
            }
            return new ResumableUploadSpec(new byte[64],
                    new byte[16],
                    "cdn-key",
                    3,
                    "https://cdn.invalid/upload",
                    Long.MAX_VALUE,
                    Map.of());
        }

        @Override
        public SignalServiceAttachmentPointer upload(final SignalServiceAttachmentStream stream) throws IOException {
            final var fileName = stream.getFileName().orElse("");
            uploads.add(fileName);
            if (uploads.size() == failUploadOnCall) {
                throw uploadFailure;
            }
            return new SignalServiceAttachmentPointer(3,
                    SignalServiceAttachmentRemoteId.from("cdn-key", 3),
                    stream.getContentType(),
                    new byte[64],
                    Optional.of((int) stream.getLength()),
                    Optional.empty(),
                    0,
                    0,
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    Optional.of(fileName),
                    false,
                    false,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    UUID.randomUUID());
        }
    }
}
