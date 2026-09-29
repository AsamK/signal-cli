package org.asamk.signal.manager.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProvisioningCapabilitiesTest {

    @Test
    void keepsNormalLinkNumberlessCapability() throws Exception {
        assertEquals("sgnl://linkdevice?uuid=test&pub_key=key&capabilities=nopni",
                ProvisioningManagerImpl.withCapabilities("sgnl://linkdevice?uuid=test&pub_key=key", false).toString());
    }

    @Test
    void addsNormalCapabilityWhenProvisioningUrlHasNoQueryYet() throws Exception {
        assertEquals("sgnl://linkdevice?capabilities=nopni",
                ProvisioningManagerImpl.withCapabilities("sgnl://linkdevice", false).toString());
    }

    @Test
    void combinesHistoryAndNumberlessCapabilitiesInOneParameter() throws Exception {
        assertEquals("sgnl://linkdevice?uuid=test&pub_key=key&capabilities=backup5,nopni",
                ProvisioningManagerImpl.withCapabilities(
                        "sgnl://linkdevice?uuid=test&pub_key=key&capabilities=backup5",
                        true).toString());
    }

    @Test
    void rejectsHistoryModeWithoutTypedBackupCapability() {
        assertThrows(Exception.class,
                () -> ProvisioningManagerImpl.withCapabilities("sgnl://linkdevice?uuid=test&pub_key=key", true));
        assertThrows(Exception.class,
                () -> ProvisioningManagerImpl.withCapabilities(
                        "sgnl://linkdevice?uuid=test&capabilities=backup5-extra",
                        true));
    }
}
