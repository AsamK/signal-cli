package org.asamk.signal.manager.helper;

import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;

import java.io.IOException;

public interface GroupAuthorizationProvider {

    GroupsV2AuthorizationString getAuthorizationForToday(GroupSecretParams groupSecretParams) throws IOException;
}
