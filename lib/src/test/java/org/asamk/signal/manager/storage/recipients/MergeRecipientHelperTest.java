package org.asamk.signal.manager.storage.recipients;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MergeRecipientHelperTest {

    static final ServiceId SERVICE_ID_A = ServiceId.ACI.from(UUID.randomUUID());
    static final ServiceId SERVICE_ID_B = ServiceId.ACI.from(UUID.randomUUID());
    static final PNI PNI_A = PNI.from(UUID.randomUUID());
    static final PNI PNI_B = PNI.from(UUID.randomUUID());
    static final String NUMBER_A = "+AAA";
    static final String NUMBER_B = "+BBB";
    static final String USERNAME_A = "USER.1";
    static final String USERNAME_B = "USER.2";

    static final PartialAddresses ADDR_A = new PartialAddresses(SERVICE_ID_A, PNI_A, NUMBER_A, USERNAME_A);
    static final PartialAddresses ADDR_B = new PartialAddresses(SERVICE_ID_B, PNI_B, NUMBER_B, USERNAME_B);

    static final T[] testInstancesNone = new T[]{
            new T(Set.of(), ADDR_A.FULL, Set.of(rec(1000000, ADDR_A.FULL))),
            new T(Set.of(), ADDR_A.ACI_NUM, Set.of(rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(), ADDR_A.ACI_PNI, Set.of(rec(1000000, ADDR_A.ACI_PNI))),
            new T(Set.of(), ADDR_A.PNI_NUM, Set.of(rec(1000000, ADDR_A.PNI_NUM))),
            new T(Set.of(), ADDR_A.ACI_USERNAME, Set.of(rec(1000000, ADDR_A.ACI_USERNAME))),
            new T(Set.of(), ADDR_A.FULL_USERNAME, Set.of(rec(1000000, ADDR_A.FULL_USERNAME))),
    };

    static final T[] testInstancesSingle = new T[]{
            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.FULL_USERNAME)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL_USERNAME))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),

            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.PNI), rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)),
                    ADDR_A.ACI_NUM,
                    Set.of(rec(1, ADDR_A.PNI), rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.FULL))),

            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.ACI), rec(1000000, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(1000000, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.FULL))),

            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.NUM), rec(1000000, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),

            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_B.FULL, Set.of(rec(1, ADDR_A.FULL), rec(1000000, ADDR_B.FULL))),

            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.ACI_USERNAME, Set.of(rec(1, ADDR_A.FULL_USERNAME))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.ACI_USERNAME, Set.of(rec(1, ADDR_A.ACI_USERNAME))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)), ADDR_A.ACI_USERNAME, Set.of(rec(1, ADDR_A.ACI_NUM_USERNAME))),
    };

    static final T[] testInstancesTwo = new T[]{
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.ACI_NUM)), ADDR_A.FULL, Set.of(rec(2, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.ACI_PNI)), ADDR_A.FULL, Set.of(rec(2, ADDR_A.FULL))),

            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.ACI_PNI)), ADDR_A.ACI_NUM, Set.of(rec(2, ADDR_A.FULL))),

            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.PNI_NUM), rec(2, ADDR_A.ACI))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.ACI_PNI)), ADDR_A.PNI_NUM, Set.of(rec(2, ADDR_A.FULL))),

            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.ACI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(2, ADDR_A.FULL))),
    };

    static final T[] testInstancesThree = new T[]{
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI), rec(3, ADDR_A.NUM)),
                    ADDR_A.FULL,
                    Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI.withIdentifiersFrom(ADDR_B.PNI)), rec(2, ADDR_A.PNI), rec(3, ADDR_A.NUM)),
                    ADDR_A.FULL,
                    Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI.withIdentifiersFrom(ADDR_B.NUM)), rec(2, ADDR_A.PNI), rec(3, ADDR_A.NUM)),
                    ADDR_A.FULL,
                    Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI), rec(3, ADDR_A.NUM.withIdentifiersFrom(ADDR_B.ACI))),
                    ADDR_A.FULL,
                    Set.of(rec(1, ADDR_A.FULL), rec(3, ADDR_B.ACI))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI.withIdentifiersFrom(ADDR_B.ACI)), rec(3, ADDR_A.NUM)),
                    ADDR_A.FULL,
                    Set.of(rec(1, ADDR_A.FULL), rec(2, ADDR_B.ACI))),
    };

    @ParameterizedTest
    @MethodSource
    void resolveRecipientTrustedLocked_NoneExisting(T test) throws Exception {
        final var testStore = new TestStore(test.input);
        MergeRecipientHelper.resolveRecipientTrustedLocked(testStore, test.request);
        assertEquals(test.output, testStore.getRecipients());
    }

    private static Stream<Arguments> resolveRecipientTrustedLocked_NoneExisting() {
        return Arrays.stream(testInstancesNone).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void resolveRecipientTrustedLocked_SingleExisting(T test) throws Exception {
        final var testStore = new TestStore(test.input);
        MergeRecipientHelper.resolveRecipientTrustedLocked(testStore, test.request);
        assertEquals(test.output, testStore.getRecipients());
    }

    private static Stream<Arguments> resolveRecipientTrustedLocked_SingleExisting() {
        return Arrays.stream(testInstancesSingle).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void resolveRecipientTrustedLocked_TwoExisting(T test) throws Exception {
        final var testStore = new TestStore(test.input);
        MergeRecipientHelper.resolveRecipientTrustedLocked(testStore, test.request);
        assertEquals(test.output, testStore.getRecipients());
    }

    private static Stream<Arguments> resolveRecipientTrustedLocked_TwoExisting() {
        return Arrays.stream(testInstancesTwo).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void resolveRecipientTrustedLocked_ThreeExisting(T test) throws Exception {
        final var testStore = new TestStore(test.input);
        MergeRecipientHelper.resolveRecipientTrustedLocked(testStore, test.request);
        assertEquals(test.output, testStore.getRecipients());
    }

    private static Stream<Arguments> resolveRecipientTrustedLocked_ThreeExisting() {
        return Arrays.stream(testInstancesThree).map(Arguments::of);
    }

    private static RecipientWithAddress rec(long recipientId, RecipientAddress address) {
        return new RecipientWithAddress(new RecipientId(recipientId, null), address);
    }

    record T(
            Set<RecipientWithAddress> input, RecipientAddress request, Set<RecipientWithAddress> output
    ) {

        @Override
        public String toString() {
            return "T{#input=%s, request=%s_%s_%s, #output=%s}".formatted(input.size(),
                    request.serviceId().isPresent() ? "SVI" : "",
                    request.pni().isPresent() ? "PNI" : "",
                    request.number().isPresent() ? "NUM" : "",
                    output.size());
        }
    }

    static class TestStore implements MergeRecipientHelper.Store {

        final Set<RecipientWithAddress> recipients;
        long nextRecipientId = 1000000;

        TestStore(final Set<RecipientWithAddress> recipients) {
            this.recipients = new HashSet<>(recipients);
        }

        public Set<RecipientWithAddress> getRecipients() {
            return recipients;
        }

        @Override
        public Set<RecipientWithAddress> findAllByAddress(final RecipientAddress address) {
            return recipients.stream().filter(r -> r.address().matches(address)).collect(Collectors.toSet());
        }

        @Override
        public RecipientId addNewRecipient(final RecipientAddress address) {
            final var recipientId = new RecipientId(nextRecipientId++, null);
            recipients.add(new RecipientWithAddress(recipientId, address));
            return recipientId;
        }

        @Override
        public void updateRecipientAddress(
                final RecipientId recipientId, final RecipientAddress address
        ) {
            recipients.removeIf(r -> r.id().equals(recipientId));
            recipients.add(new RecipientWithAddress(recipientId, address));
        }

        @Override
        public void removeRecipientAddress(final RecipientId recipientId) {
            recipients.removeIf(r -> r.id().equals(recipientId));
        }
    }

    private record PartialAddresses(
            RecipientAddress FULL,
            RecipientAddress FULL_USERNAME,
            RecipientAddress ACI,
            RecipientAddress PNI,
            RecipientAddress NUM,
            RecipientAddress ACI_NUM,
            RecipientAddress ACI_NUM_USERNAME,
            RecipientAddress PNI_NUM,
            RecipientAddress ACI_PNI,
            RecipientAddress ACI_USERNAME
    ) {

        PartialAddresses(ServiceId serviceId, PNI pni, String number, String username) {
            this(new RecipientAddress(serviceId, pni, number),
                    new RecipientAddress(serviceId, pni, number, username),
                    new RecipientAddress(serviceId, null, null),
                    new RecipientAddress(null, pni, null),
                    new RecipientAddress(null, null, number),
                    new RecipientAddress(serviceId, null, number),
                    new RecipientAddress(serviceId, null, number, username),
                    new RecipientAddress(null, pni, number),
                    new RecipientAddress(serviceId, pni, null),
                    new RecipientAddress(serviceId, null, null, username));
        }
    }
}
