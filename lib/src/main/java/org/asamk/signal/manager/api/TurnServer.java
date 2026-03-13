package org.asamk.signal.manager.api;

import java.util.List;

public record TurnServer(
        String username,
        String password,
        List<String> urls
) {
}
