package org.asamk.signal.manager.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;
import org.signal.network.api.RegistrationApiV2;
import org.signal.network.rest.SignalRestClient;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.ByteString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberlessProvisioningTest {

    private static final ACI ACI_ID = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");
    private static final PNI PNI_ID = PNI.parseOrThrow("22222222-2222-4222-8222-222222222222");

    @TempDir
    Path directory;

    @Test
    void numberlessProvisioningRequiresGroupCredentialSalt() {
        assertThrows(IOException.class, () -> ProvisioningManagerImpl.parsePni(new ProvisionMessage.Builder().build()));
        final var emptySalt = new ProvisionMessage.Builder().authCredentialSalt(ByteString.EMPTY).build();
        assertThrows(IOException.class, () -> ProvisioningManagerImpl.parsePni(emptySalt));
        assertNull(ProvisioningManagerImpl.toRegistrationPreKeys(null));
    }

    @Test
    void acceptsNumberlessProvisioningButRejectsInconsistentPhoneIdentity() throws Exception {
        final var message = new ProvisionMessage.Builder().authCredentialSalt(ByteString.of(new byte[32]));
        assertNull(ProvisioningManagerImpl.parsePni(message.build()));
        assertThrows(IOException.class, () -> ProvisioningManagerImpl.parsePni(message.pni(PNI_ID.toString()).build()));
        assertThrows(IllegalArgumentException.class,
                () -> ProvisioningManagerImpl.parsePni(new ProvisionMessage.Builder().number("+12025550123").build()));
        assertEquals(PNI_ID,
                ProvisioningManagerImpl.parsePni(new ProvisionMessage.Builder().number("+12025550123")
                        .pni(PNI_ID.toString())
                        .build()));
    }

    @Test
    void numberlessLinkRequestOmitsPniAndAuthenticatesWithAci() throws Exception {
        checkLinkRequest(null, null, 200, null);
    }

    @Test
    void numberedLinkRequestStillIncludesPniKeys() throws Exception {
        checkLinkRequest("+12025550123", PNI_ID, 200, null);
    }

    @ParameterizedTest
    @CsvSource({
            "422, Signal rejected the device linking request",
            "403, [403] Device verification failed",
            "409, Linked device is missing a required account capability",
            "411, Account has reached its linked device limit",
            "429, Device linking rate limited; try again later"
    })
    void linkingErrorsAreReportedWithoutCrashingOrIncludingServerBody(
            final int statusCode,
            final String errorMessage
    ) throws Exception {
        checkLinkRequest(null, null, statusCode, errorMessage);
    }

    private void checkLinkRequest(
            final String number,
            final PNI pni,
            final int statusCode,
            final String errorMessage
    ) throws Exception {
        final var request = new AtomicReference<Request>();
        final var body = new AtomicReference<JsonNode>();
        final var mapper = new ObjectMapper();
        final var client = new OkHttpClient.Builder().addInterceptor(chain -> {
            request.set(chain.request());
            final var buffer = new Buffer();
            chain.request().body().writeTo(buffer);
            body.set(mapper.readTree(buffer.readUtf8()));
            return new Response.Builder().request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("Test response")
                    .body(ResponseBody.create(statusCode == 200 ? "{\"deviceId\":2}" : "secret payload",
                            MediaType.get("application/json")))
                    .build();
        }).build();
        final var config = ServiceConfig.getServiceEnvironmentConfig(ServiceEnvironment.STAGING, "signal-cli-test");
        final var restClient = new SignalRestClient(config.signalServiceConfiguration(),
                "signal-cli-test",
                null,
                false,
                1000L,
                new SecureRandom(),
                client);
        final var api = new RegistrationApiV2(restClient, false);

        try (final var account = SignalAccount.createLinkedAccount(directory.toFile(),
                "account",
                ServiceEnvironment.STAGING,
                Settings.DEFAULT)) {
            account.setProvisioningData(number,
                    ACI_ID,
                    pni,
                    "test-password",
                    new byte[]{1},
                    KeyUtils.generateIdentityKeyPair(),
                    pni == null ? null : KeyUtils.generateIdentityKeyPair(),
                    KeyUtils.createProfileKey(),
                    null,
                    new byte[32],
                    null);
            final var aciKeys = KeyUtils.generatePreKeysForType(account.getAccountData(ServiceIdType.ACI));
            final var pniKeys = pni == null
                    ? null
                    : KeyUtils.generatePreKeysForType(account.getAccountData(ServiceIdType.PNI));
            if (statusCode == 200) {
                assertEquals(2,
                        ProvisioningManagerImpl.registerLinkedDevice(api, account, "test-code", aciKeys, pniKeys));
            } else {
                final var error = assertThrows(IOException.class,
                        () -> ProvisioningManagerImpl.registerLinkedDevice(api,
                                account,
                                "test-code",
                                aciKeys,
                                pniKeys));
                assertEquals(errorMessage, error.getMessage());
                if (statusCode == 403) {
                    assertInstanceOf(AuthorizationFailedException.class, error);
                }
            }
            assertEquals("PUT", request.get().method());
            assertEquals("/v1/devices/link", request.get().url().encodedPath());
            assertEquals(Credentials.basic(ACI_ID.toString(), "test-password"), request.get().header("Authorization"));
            assertTrue(body.get()
                    .path("accountAttributes")
                    .path("capabilities")
                    .path("optionalPhoneNumber")
                    .asBoolean());
            assertTrue(body.get().hasNonNull("aciSignedPreKey"));
            assertTrue(body.get().hasNonNull("aciPqLastResortPreKey"));
            assertEquals(pni != null, body.get().has("pniSignedPreKey"));
            assertEquals(pni != null, body.get().has("pniPqLastResortPreKey"));
            assertEquals(pni != null, body.get().path("accountAttributes").has("pniRegistrationId"));
        } finally {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}
