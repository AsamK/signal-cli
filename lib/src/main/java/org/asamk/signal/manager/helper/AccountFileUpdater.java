package org.asamk.signal.manager.helper;

import org.signal.core.models.ServiceId.ACI;

public interface AccountFileUpdater {

    void updateAccountIdentifiers(String number, ACI aci);

    void removeAccount();
}
