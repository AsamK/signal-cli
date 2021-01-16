/*
  Copyright (C) 2015-2021 AsamK and contributors

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
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.manager.LibSignalLogger;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class Main {

    public static void main(String[] args) {
        installSecurityProviderWorkaround();

        // Configuring the logger needs to happen before any logger is initialized
        configureLogging(isVerbose(args));

        ArgumentParser parser = Cli.buildArgumentParser();

        Namespace ns = parser.parseArgsOrFail(args);

        int res = new Cli(ns).init();
        System.exit(res);
    }

    private static void installSecurityProviderWorkaround() {
        // Register our own security provider
        Security.insertProviderAt(new SecurityProvider(), 1);
        Security.addProvider(new BouncyCastleProvider());
    }

    private static boolean isVerbose(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("signal-cli").build().defaultHelp(false);
        parser.addArgument("--verbose").action(Arguments.storeTrue());

        Namespace ns;
        try {
            ns = parser.parseKnownArgs(args, null);
        } catch (ArgumentParserException e) {
            return false;
        }

        return ns.getBoolean("verbose");
    }

    private static void configureLogging(final boolean verbose) {
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
            System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
            System.setProperty("org.slf4j.simpleLogger.showShortLogName", "false");
            System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
            System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSXX");
            LibSignalLogger.initLogger();
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
            System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
            System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");
            System.setProperty("org.slf4j.simpleLogger.showDateTime", "false");
        }
    }
}
