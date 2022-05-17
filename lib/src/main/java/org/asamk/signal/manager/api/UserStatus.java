package org.asamk.signal.manager.api;

import java.util.UUID;

public record UserStatus(String number, UUID uuid, boolean unrestrictedUnidentifiedAccess) {}
