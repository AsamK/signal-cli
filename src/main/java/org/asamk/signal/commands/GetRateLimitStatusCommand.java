package org.asamk.signal.commands;

import com.fasterxml.jackson.annotation.JsonInclude;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.RateLimitStatus;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

public class GetRateLimitStatusCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "getRateLimitStatus";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help(
                "Return the current rate-limit state for this account, or an inactive status if no rate limit is active.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var status = m.getRateLimitStatus();
        switch (outputWriter) {
            case JsonWriter writer -> writer.write(JsonRateLimitStatus.from(status));
            case PlainTextWriter writer -> {
                if (!status.active()) {
                    writer.println("Not rate limited");
                } else if (status.proofRequired()) {
                    writer.println("Rate limited (proof required), retry after {}s, challenge token: {}",
                            status.retryAfterSeconds(),
                            status.challengeToken());
                } else {
                    writer.println("Rate limited, retry after {}s", status.retryAfterSeconds());
                }
            }
        }
    }

    private record JsonRateLimitStatus(
            boolean active,
            boolean proofRequired,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long retryAfterSeconds,
            @JsonInclude(JsonInclude.Include.NON_NULL) String challengeToken,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long expiresAtEpochSeconds
    ) {

        static JsonRateLimitStatus from(RateLimitStatus status) {
            return new JsonRateLimitStatus(status.active(),
                    status.proofRequired(),
                    status.retryAfterSeconds(),
                    status.challengeToken(),
                    status.expiresAtEpochSeconds());
        }
    }
}
