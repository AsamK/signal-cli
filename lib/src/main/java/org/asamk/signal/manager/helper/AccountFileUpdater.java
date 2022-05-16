package org.asamk.signal.manager.helper;

import org.whispersystems.signalservice.api.push.ACI;

public interface AccountFileUpdater {

    void updateAccountIdentifiers(String number, ACI aci);

    void removeAccount();
}
