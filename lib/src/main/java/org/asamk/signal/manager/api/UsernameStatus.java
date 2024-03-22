package org.asamk.signal.manager.api;

import java.util.UUID;

public record UsernameStatus(String username, UUID uuid, boolean unrestrictedUnidentifiedAccess) {}
