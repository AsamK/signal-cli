package org.asamk.signal.manager.config;

public class KeyBackupConfig {

    private final String enclaveName;
    private final byte[] serviceId;
    private final String mrenclave;

    public KeyBackupConfig(final String enclaveName, final byte[] serviceId, final String mrenclave) {
        this.enclaveName = enclaveName;
        this.serviceId = serviceId;
        this.mrenclave = mrenclave;
    }

    public String getEnclaveName() {
        return enclaveName;
    }

    public byte[] getServiceId() {
        return serviceId;
    }

    public String getMrenclave() {
        return mrenclave;
    }
}
