package org.asamk.signal.manager.storage.recipients;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MergeRecipientHelperTest {

    static final ServiceId SERVICE_ID_A = ServiceId.from(UUID.randomUUID());
    static final ServiceId SERVICE_ID_B = ServiceId.from(UUID.randomUUID());
    static final ServiceId SERVICE_ID_C = ServiceId.from(UUID.randomUUID());
    static final PNI PNI_A = PNI.from(UUID.randomUUID());
    static final PNI PNI_B = PNI.from(UUID.randomUUID());
    static final PNI PNI_C = PNI.from(UUID.randomUUID());
    static final String NUMBER_A = "+AAA";
    static final String NUMBER_B = "+BBB";
    static final String NUMBER_C = "+CCC";

    static final PartialAddresses ADDR_A = new PartialAddresses(SERVICE_ID_A, PNI_A, NUMBER_A);
    static final PartialAddresses ADDR_B = new PartialAddresses(SERVICE_ID_B, PNI_B, NUMBER_B);

    static T[] testInstancesNone = new T[]{
            // 1
            new T(Set.of(), ADDR_A.FULL, Set.of(rec(1000000, ADDR_A.FULL))),
            new T(Set.of(), ADDR_A.ACI_NUM, Set.of(rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(), ADDR_A.ACI_PNI, Set.of(rec(1000000, ADDR_A.ACI_PNI))),
            new T(Set.of(), ADDR_A.PNI_S_NUM, Set.of(rec(1000000, ADDR_A.PNI_S_NUM))),
            new T(Set.of(), ADDR_A.PNI_NUM, Set.of(rec(1000000, ADDR_A.PNI_NUM))),
    };

    static T[] testInstancesSingle = new T[]{
            // 1
            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_S)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_S_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),

            // 10
            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.PNI), rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S)),
                    ADDR_A.ACI_NUM,
                    Set.of(rec(1, ADDR_A.PNI_S), rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)),
                    ADDR_A.ACI_NUM,
                    Set.of(rec(1, ADDR_A.PNI), rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S_NUM)),
                    ADDR_A.ACI_NUM,
                    Set.of(rec(1, ADDR_A.PNI_S), rec(1000000, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.FULL))),

            // 19
            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.ACI), rec(1000000, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(1000000, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S_NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.FULL))),

            // 28
            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)),
                    ADDR_A.PNI_S_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(1000000, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_S_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(1000000, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S_NUM)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.FULL))),

            // 37
            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.PNI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.PNI_S)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.NUM), rec(1000000, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.ACI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_S_NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI_PNI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),

            new T(Set.of(rec(1, ADDR_A.FULL)), ADDR_B.FULL, Set.of(rec(1, ADDR_A.FULL), rec(1000000, ADDR_B.FULL))),
    };

    static T[] testInstancesTwo = new T[]{
            // 1
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S_NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.ACI_NUM)), ADDR_A.FULL, Set.of(rec(2, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_S), rec(2, ADDR_A.NUM)), ADDR_A.FULL, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_S), rec(2, ADDR_A.ACI_NUM)), ADDR_A.FULL, Set.of(rec(2, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.PNI_S)), ADDR_A.FULL, Set.of(rec(2, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.ACI_PNI)), ADDR_A.FULL, Set.of(rec(2, ADDR_A.FULL))),

            // 12
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.ACI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)), ADDR_A.ACI_NUM, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S_NUM)),
                    ADDR_A.ACI_NUM,
                    Set.of(rec(1, ADDR_A.ACI_NUM), rec(2, ADDR_A.PNI_S))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.ACI_PNI)), ADDR_A.ACI_NUM, Set.of(rec(2, ADDR_A.FULL))),

            // 16
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.PNI_NUM), rec(2, ADDR_A.ACI))),
            new T(Set.of(rec(1, ADDR_A.PNI_S), rec(2, ADDR_A.NUM)), ADDR_A.PNI_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S), rec(2, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_NUM,
                    Set.of(rec(1, ADDR_A.PNI_NUM), rec(2, ADDR_A.ACI))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.PNI_S)), ADDR_A.PNI_NUM, Set.of(rec(2, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.ACI_PNI)), ADDR_A.PNI_NUM, Set.of(rec(2, ADDR_A.FULL))),

            // 24
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)),
                    ADDR_A.PNI_S_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S_NUM)),
                    ADDR_A.PNI_S_NUM,
                    Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.NUM)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.PNI_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_S_NUM,
                    Set.of(rec(1, ADDR_A.PNI_NUM), rec(2, ADDR_A.ACI))),
            new T(Set.of(rec(1, ADDR_A.PNI_S), rec(2, ADDR_A.NUM)), ADDR_A.PNI_S_NUM, Set.of(rec(1, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI_S), rec(2, ADDR_A.ACI_NUM)),
                    ADDR_A.PNI_S_NUM,
                    Set.of(rec(1, ADDR_A.PNI_S_NUM), rec(2, ADDR_A.ACI))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.PNI_S)), ADDR_A.PNI_S_NUM, Set.of(rec(2, ADDR_A.PNI_S_NUM))),
            new T(Set.of(rec(1, ADDR_A.NUM), rec(2, ADDR_A.ACI_PNI)), ADDR_A.PNI_S_NUM, Set.of(rec(2, ADDR_A.FULL))),

            // 32
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.ACI_PNI))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(1, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.ACI), rec(2, ADDR_A.PNI_S_NUM)),
                    ADDR_A.ACI_PNI,
                    Set.of(rec(1, ADDR_A.ACI_PNI), rec(2, ADDR_A.NUM))),
            new T(Set.of(rec(1, ADDR_A.PNI), rec(2, ADDR_A.ACI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(2, ADDR_A.FULL))),
            new T(Set.of(rec(1, ADDR_A.PNI_S), rec(2, ADDR_A.ACI_NUM)), ADDR_A.ACI_PNI, Set.of(rec(2, ADDR_A.FULL))),
    };

    static T[] testInstancesThree = new T[]{
            // 1
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
            RecipientAddress ACI,
            RecipientAddress PNI,
            RecipientAddress PNI_S,
            RecipientAddress NUM,
            RecipientAddress ACI_NUM,
            RecipientAddress PNI_NUM,
            RecipientAddress PNI_S_NUM,
            RecipientAddress ACI_PNI
    ) {

        PartialAddresses(ServiceId serviceId, PNI pni, String number) {
            this(new RecipientAddress(serviceId, pni, number),
                    new RecipientAddress(serviceId, null, null),
                    new RecipientAddress(null, pni, null),
                    new RecipientAddress(ServiceId.from(pni.uuid()), null, null),
                    new RecipientAddress(null, null, number),
                    new RecipientAddress(serviceId, null, number),
                    new RecipientAddress(null, pni, number),
                    new RecipientAddress(ServiceId.from(pni.uuid()), null, number),
                    new RecipientAddress(serviceId, pni, null));
        }
    }
}
