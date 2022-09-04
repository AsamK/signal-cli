/*
  Copyright (C) 2015-2022 AsamK and contributors

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.DefaultSettings;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.logging.LogConfigurator;
import org.asamk.signal.manager.ManagerLogger;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.security.Security;

public class Main {

    public static void main(String[] args) {
        // enable unlimited strength crypto via Policy, supported on relevant JREs
        Security.setProperty("crypto.policy", "unlimited");
        installSecurityProviderWorkaround();

        // Configuring the logger needs to happen before any logger is initialized

        final var nsLog = parseArgs(args);
        final var verboseLevel = nsLog == null ? 0 : nsLog.getInt("verbose");
        final var logFile = nsLog == null ? null : nsLog.<File>get("log-file");
        final var scrubLog = nsLog != null && nsLog.getBoolean("scrub-log");
        configureLogging(verboseLevel, logFile, scrubLog);

        var parser = App.buildArgumentParser();

        var ns = parser.parseArgsOrFail(args);

        int status = 0;
        try {
            new App(ns).init();
        } catch (CommandException e) {
            System.err.println(e.getMessage());
            if (verboseLevel > 0 && e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            status = getStatusForError(e);
        } catch (Throwable e) {
            e.printStackTrace();
            status = 2;
        }
        System.exit(status);
    }

    private static void installSecurityProviderWorkaround() {
        // Register our own security provider
        Security.insertProviderAt(new SecurityProvider(), 1);
        Security.addProvider(new BouncyCastleProvider());
    }

    private static Namespace parseArgs(String[] args) {
        var parser = ArgumentParsers.newFor("signal-cli", DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true)
                .build()
                .defaultHelp(false);
        parser.addArgument("-v", "--verbose").action(Arguments.count());
        parser.addArgument("--log-file").type(File.class);
        parser.addArgument("--scrub-log").action(Arguments.storeTrue());

        try {
            return parser.parseKnownArgs(args, null);
        } catch (ArgumentParserException e) {
            return null;
        }
    }

    private static void configureLogging(final int verboseLevel, final File logFile, final boolean scrubLog) {
        LogConfigurator.setVerboseLevel(verboseLevel);
        LogConfigurator.setLogFile(logFile);
        LogConfigurator.setScrubSensitiveInformation(scrubLog);

        if (verboseLevel > 0) {
            java.util.logging.Logger.getLogger("")
                    .setLevel(verboseLevel > 2 ? java.util.logging.Level.FINEST : java.util.logging.Level.INFO);
            ManagerLogger.initLogger();
        }
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    private static int getStatusForError(final CommandException e) {
        if (e instanceof UserErrorException) {
            return 1;
        } else if (e instanceof UnexpectedErrorException) {
            return 2;
        } else if (e instanceof IOErrorException) {
            return 3;
        } else if (e instanceof UntrustedKeyErrorException) {
            return 4;
        } else {
            return 2;
        }
    }
}
