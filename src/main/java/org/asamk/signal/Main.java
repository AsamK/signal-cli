/*
  Copyright (C) 2015-2018 AsamK

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
import net.sourceforge.argparse4j.inf.*;
import org.apache.http.util.TextUtils;
import org.asamk.Signal;
import org.asamk.signal.manager.BaseConfig;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.protocol.JsonIdentityKeyStore;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.asamk.signal.util.ErrorUtils.*;

public class Main {

    private static final String SIGNAL_BUSNAME = "org.asamk.Signal";
    private static final String SIGNAL_OBJECTPATH = "/org/asamk/Signal";

    public static void main(String[] args) {
        // Workaround for BKS truststore
        Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);

        Namespace ns = parseArgs(args);
        if (ns == null) {
            System.exit(1);
        }

        int res = handleCommands(ns);
        System.exit(res);
    }

    private static int handleCommands(Namespace ns) {
        final String username = ns.getString("username");
        Manager m;
        Signal ts;
        DBusConnection dBusConn = null;
        try {
            if (ns.getBoolean("dbus") || ns.getBoolean("dbus_system")) {
                try {
                    m = null;
                    int busType;
                    if (ns.getBoolean("dbus_system")) {
                        busType = DBusConnection.SYSTEM;
                    } else {
                        busType = DBusConnection.SESSION;
                    }
                    dBusConn = DBusConnection.getConnection(busType);
                    ts = dBusConn.getRemoteObject(
                            SIGNAL_BUSNAME, SIGNAL_OBJECTPATH,
                            Signal.class);
                } catch (UnsatisfiedLinkError e) {
                    System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                    return 1;
                } catch (DBusException e) {
                    e.printStackTrace();
                    if (dBusConn != null) {
                        dBusConn.disconnect();
                    }
                    return 3;
                }
            } else {
                String settingsPath = ns.getString("config");
                if (TextUtils.isEmpty(settingsPath)) {
                    settingsPath = System.getProperty("user.home") + "/.config/signal";
                    if (!new File(settingsPath).exists()) {
                        String legacySettingsPath = System.getProperty("user.home") + "/.config/textsecure";
                        if (new File(legacySettingsPath).exists()) {
                            settingsPath = legacySettingsPath;
                        }
                    }
                }

                m = new Manager(username, settingsPath);
                ts = m;
                try {
                    m.init();
                } catch (Exception e) {
                    System.err.println("Error loading state file: " + e.getMessage());
                    return 2;
                }
            }

            switch (ns.getString("command")) {
                case "register":
                    if (dBusConn != null) {
                        System.err.println("register is not yet implemented via dbus");
                        return 1;
                    }
                    try {
                        m.register(ns.getBoolean("voice"));
                    } catch (IOException e) {
                        System.err.println("Request verify error: " + e.getMessage());
                        return 3;
                    }
                    break;
                case "unregister":
                    if (dBusConn != null) {
                        System.err.println("unregister is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    try {
                        m.unregister();
                    } catch (IOException e) {
                        System.err.println("Unregister error: " + e.getMessage());
                        return 3;
                    }
                    break;
                case "updateAccount":
                    if (dBusConn != null) {
                        System.err.println("updateAccount is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    try {
                        m.updateAccountAttributes();
                    } catch (IOException e) {
                        System.err.println("UpdateAccount error: " + e.getMessage());
                        return 3;
                    }
                    break;
                case "setPin":
                    if (dBusConn != null) {
                        System.err.println("setPin is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    try {
                        String registrationLockPin = ns.getString("registrationLockPin");
                        m.setRegistrationLockPin(Optional.of(registrationLockPin));
                    } catch (IOException e) {
                        System.err.println("Set pin error: " + e.getMessage());
                        return 3;
                    }
                    break;
                case "removePin":
                    if (dBusConn != null) {
                        System.err.println("removePin is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    try {
                        m.setRegistrationLockPin(Optional.<String>absent());
                    } catch (IOException e) {
                        System.err.println("Remove pin error: " + e.getMessage());
                        return 3;
                    }
                    break;
                case "verify":
                    if (dBusConn != null) {
                        System.err.println("verify is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.userHasKeys()) {
                        System.err.println("User has no keys, first call register.");
                        return 1;
                    }
                    if (m.isRegistered()) {
                        System.err.println("User registration is already verified");
                        return 1;
                    }
                    try {
                        String verificationCode = ns.getString("verificationCode");
                        String pin = ns.getString("pin");
                        m.verifyAccount(verificationCode, pin);
                    } catch (LockedException e) {
                        System.err.println("Verification failed! This number is locked with a pin. Hours remaining until reset: " + (e.getTimeRemaining() / 1000 / 60 / 60));
                        System.err.println("Use '--pin PIN_CODE' to specify the registration lock PIN");
                        return 3;
                    } catch (IOException e) {
                        System.err.println("Verify error: " + e.getMessage());
                        return 3;
                    }
                    break;
                case "link":
                    if (dBusConn != null) {
                        System.err.println("link is not yet implemented via dbus");
                        return 1;
                    }

                    String deviceName = ns.getString("name");
                    if (deviceName == null) {
                        deviceName = "cli";
                    }
                    try {
                        System.out.println(m.getDeviceLinkUri());
                        m.finishDeviceLink(deviceName);
                        System.out.println("Associated with: " + m.getUsername());
                    } catch (TimeoutException e) {
                        System.err.println("Link request timed out, please try again.");
                        return 3;
                    } catch (IOException e) {
                        System.err.println("Link request error: " + e.getMessage());
                        return 3;
                    } catch (AssertionError e) {
                        handleAssertionError(e);
                        return 1;
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                        return 2;
                    } catch (UserAlreadyExists e) {
                        System.err.println("The user " + e.getUsername() + " already exists\nDelete \"" + e.getFileName() + "\" before trying again.");
                        return 1;
                    }
                    break;
                case "addDevice":
                    if (dBusConn != null) {
                        System.err.println("link is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    try {
                        m.addDeviceLink(new URI(ns.getString("uri")));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return 3;
                    } catch (InvalidKeyException | URISyntaxException e) {
                        e.printStackTrace();
                        return 2;
                    } catch (AssertionError e) {
                        handleAssertionError(e);
                        return 1;
                    }
                    break;
                case "listDevices":
                    if (dBusConn != null) {
                        System.err.println("listDevices is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    try {
                        List<DeviceInfo> devices = m.getLinkedDevices();
                        for (DeviceInfo d : devices) {
                            System.out.println("Device " + d.getId() + (d.getId() == m.getDeviceId() ? " (this device)" : "") + ":");
                            System.out.println(" Name: " + d.getName());
                            System.out.println(" Created: " + DateUtils.formatTimestamp(d.getCreated()));
                            System.out.println(" Last seen: " + DateUtils.formatTimestamp(d.getLastSeen()));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return 3;
                    }
                    break;
                case "removeDevice":
                    if (dBusConn != null) {
                        System.err.println("removeDevice is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    try {
                        int deviceId = ns.getInt("deviceId");
                        m.removeLinkedDevices(deviceId);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return 3;
                    }
                    break;
                case "send":
                    if (dBusConn == null && !m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }

                    if (ns.getBoolean("endsession")) {
                        if (ns.getList("recipient") == null) {
                            System.err.println("No recipients given");
                            System.err.println("Aborting sending.");
                            return 1;
                        }
                        try {
                            ts.sendEndSessionMessage(ns.<String>getList("recipient"));
                        } catch (IOException e) {
                            handleIOException(e);
                            return 3;
                        } catch (EncapsulatedExceptions e) {
                            handleEncapsulatedExceptions(e);
                            return 3;
                        } catch (AssertionError e) {
                            handleAssertionError(e);
                            return 1;
                        } catch (DBusExecutionException e) {
                            handleDBusExecutionException(e);
                            return 1;
                        }
                    } else {
                        String messageText = ns.getString("message");
                        if (messageText == null) {
                            try {
                                messageText = IOUtils.readAll(System.in, Charset.defaultCharset());
                            } catch (IOException e) {
                                System.err.println("Failed to read message from stdin: " + e.getMessage());
                                System.err.println("Aborting sending.");
                                return 1;
                            }
                        }

                        try {
                            List<String> attachments = ns.getList("attachment");
                            if (attachments == null) {
                                attachments = new ArrayList<>();
                            }
                            if (ns.getString("group") != null) {
                                byte[] groupId = Util.decodeGroupId(ns.getString("group"));
                                ts.sendGroupMessage(messageText, attachments, groupId);
                            } else {
                                ts.sendMessage(messageText, attachments, ns.<String>getList("recipient"));
                            }
                        } catch (IOException e) {
                            handleIOException(e);
                            return 3;
                        } catch (EncapsulatedExceptions e) {
                            handleEncapsulatedExceptions(e);
                            return 3;
                        } catch (AssertionError e) {
                            handleAssertionError(e);
                            return 1;
                        } catch (GroupNotFoundException e) {
                            handleGroupNotFoundException(e);
                            return 1;
                        } catch (NotAGroupMemberException e) {
                            handleNotAGroupMemberException(e);
                            return 1;
                        } catch (AttachmentInvalidException e) {
                            System.err.println("Failed to add attachment: " + e.getMessage());
                            System.err.println("Aborting sending.");
                            return 1;
                        } catch (DBusExecutionException e) {
                            handleDBusExecutionException(e);
                            return 1;
                        } catch (GroupIdFormatException e) {
                            handleGroupIdFormatException(e);
                            return 1;
                        }
                    }

                    break;
                case "receive":
                    if (dBusConn != null) {
                        try {
                            dBusConn.addSigHandler(Signal.MessageReceived.class, new DBusSigHandler<Signal.MessageReceived>() {
                                @Override
                                public void handle(Signal.MessageReceived s) {
                                    System.out.print(String.format("Envelope from: %s\nTimestamp: %s\nBody: %s\n",
                                            s.getSender(), DateUtils.formatTimestamp(s.getTimestamp()), s.getMessage()));
                                    if (s.getGroupId().length > 0) {
                                        System.out.println("Group info:");
                                        System.out.println("  Id: " + Base64.encodeBytes(s.getGroupId()));
                                    }
                                    if (s.getAttachments().size() > 0) {
                                        System.out.println("Attachments: ");
                                        for (String attachment : s.getAttachments()) {
                                            System.out.println("-  Stored plaintext in: " + attachment);
                                        }
                                    }
                                    System.out.println();
                                }
                            });
                            dBusConn.addSigHandler(Signal.ReceiptReceived.class, new DBusSigHandler<Signal.ReceiptReceived>() {
                                @Override
                                public void handle(Signal.ReceiptReceived s) {
                                    System.out.print(String.format("Receipt from: %s\nTimestamp: %s\n",
                                            s.getSender(), DateUtils.formatTimestamp(s.getTimestamp())));
                                }
                            });
                        } catch (UnsatisfiedLinkError e) {
                            System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                            return 1;
                        } catch (DBusException e) {
                            e.printStackTrace();
                            return 1;
                        }
                        while (true) {
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                return 0;
                            }
                        }
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    double timeout = 5;
                    if (ns.getDouble("timeout") != null) {
                        timeout = ns.getDouble("timeout");
                    }
                    boolean returnOnTimeout = true;
                    if (timeout < 0) {
                        returnOnTimeout = false;
                        timeout = 3600;
                    }
                    boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
                    try {
                        final Manager.ReceiveMessageHandler handler = ns.getBoolean("json") ? new JsonReceiveMessageHandler(m) : new ReceiveMessageHandler(m);
                        m.receiveMessages((long) (timeout * 1000), TimeUnit.MILLISECONDS, returnOnTimeout, ignoreAttachments, handler);
                    } catch (IOException e) {
                        System.err.println("Error while receiving messages: " + e.getMessage());
                        return 3;
                    } catch (AssertionError e) {
                        handleAssertionError(e);
                        return 1;
                    }
                    break;
                case "quitGroup":
                    if (dBusConn != null) {
                        System.err.println("quitGroup is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }

                    try {
                        m.sendQuitGroupMessage(Util.decodeGroupId(ns.getString("group")));
                    } catch (IOException e) {
                        handleIOException(e);
                        return 3;
                    } catch (EncapsulatedExceptions e) {
                        handleEncapsulatedExceptions(e);
                        return 3;
                    } catch (AssertionError e) {
                        handleAssertionError(e);
                        return 1;
                    } catch (GroupNotFoundException e) {
                        handleGroupNotFoundException(e);
                        return 1;
                    } catch (NotAGroupMemberException e) {
                        handleNotAGroupMemberException(e);
                        return 1;
                    } catch (GroupIdFormatException e) {
                        handleGroupIdFormatException(e);
                        return 1;
                    }

                    break;
                case "updateGroup":
                    if (dBusConn == null && !m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }

                    try {
                        byte[] groupId = null;
                        if (ns.getString("group") != null) {
                            groupId = Util.decodeGroupId(ns.getString("group"));
                        }
                        if (groupId == null) {
                            groupId = new byte[0];
                        }
                        String groupName = ns.getString("name");
                        if (groupName == null) {
                            groupName = "";
                        }
                        List<String> groupMembers = ns.getList("member");
                        if (groupMembers == null) {
                            groupMembers = new ArrayList<>();
                        }
                        String groupAvatar = ns.getString("avatar");
                        if (groupAvatar == null) {
                            groupAvatar = "";
                        }
                        byte[] newGroupId = ts.updateGroup(groupId, groupName, groupMembers, groupAvatar);
                        if (groupId.length != newGroupId.length) {
                            System.out.println("Creating new group \"" + Base64.encodeBytes(newGroupId) + "\" …");
                        }
                    } catch (IOException e) {
                        handleIOException(e);
                        return 3;
                    } catch (AttachmentInvalidException e) {
                        System.err.println("Failed to add avatar attachment for group\": " + e.getMessage());
                        System.err.println("Aborting sending.");
                        return 1;
                    } catch (GroupNotFoundException e) {
                        handleGroupNotFoundException(e);
                        return 1;
                    } catch (NotAGroupMemberException e) {
                        handleNotAGroupMemberException(e);
                        return 1;
                    } catch (EncapsulatedExceptions e) {
                        handleEncapsulatedExceptions(e);
                        return 3;
                    } catch (GroupIdFormatException e) {
                        handleGroupIdFormatException(e);
                        return 1;
                    }

                    break;
                case "listGroups":
                    if (dBusConn != null) {
                        System.err.println("listGroups is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }

                    List<GroupInfo> groups = m.getGroups();
                    boolean detailed = ns.getBoolean("detailed");

                    for (GroupInfo group : groups) {
                        printGroup(group, detailed);
                    }
                    break;
                case "listIdentities":
                    if (dBusConn != null) {
                        System.err.println("listIdentities is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    if (ns.get("number") == null) {
                        for (Map.Entry<String, List<JsonIdentityKeyStore.Identity>> keys : m.getIdentities().entrySet()) {
                            for (JsonIdentityKeyStore.Identity id : keys.getValue()) {
                                printIdentityFingerprint(m, keys.getKey(), id);
                            }
                        }
                    } else {
                        String number = ns.getString("number");
                        for (JsonIdentityKeyStore.Identity id : m.getIdentities(number)) {
                            printIdentityFingerprint(m, number, id);
                        }
                    }
                    break;
                case "trust":
                    if (dBusConn != null) {
                        System.err.println("trust is not yet implemented via dbus");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    String number = ns.getString("number");
                    if (ns.getBoolean("trust_all_known_keys")) {
                        boolean res = m.trustIdentityAllKeys(number);
                        if (!res) {
                            System.err.println("Failed to set the trust for this number, make sure the number is correct.");
                            return 1;
                        }
                    } else {
                        String fingerprint = ns.getString("verified_fingerprint");
                        if (fingerprint != null) {
                            fingerprint = fingerprint.replaceAll(" ", "");
                            if (fingerprint.length() == 66) {
                                byte[] fingerprintBytes;
                                try {
                                    fingerprintBytes = Hex.toByteArray(fingerprint.toLowerCase(Locale.ROOT));
                                } catch (Exception e) {
                                    System.err.println("Failed to parse the fingerprint, make sure the fingerprint is a correctly encoded hex string without additional characters.");
                                    return 1;
                                }
                                boolean res = m.trustIdentityVerified(number, fingerprintBytes);
                                if (!res) {
                                    System.err.println("Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.");
                                    return 1;
                                }
                            } else if (fingerprint.length() == 60) {
                                boolean res = m.trustIdentityVerifiedSafetyNumber(number, fingerprint);
                                if (!res) {
                                    System.err.println("Failed to set the trust for the safety number of this phone number, make sure the phone number and the safety number are correct.");
                                    return 1;
                                }
                            } else {
                                System.err.println("Fingerprint has invalid format, either specify the old hex fingerprint or the new safety number");
                                return 1;
                            }
                        } else {
                            System.err.println("You need to specify the fingerprint you have verified with -v FINGERPRINT");
                            return 1;
                        }
                    }
                    break;
                case "daemon":
                    if (dBusConn != null) {
                        System.err.println("Stop it.");
                        return 1;
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        return 1;
                    }
                    DBusConnection conn = null;
                    try {
                        try {
                            int busType;
                            if (ns.getBoolean("system")) {
                                busType = DBusConnection.SYSTEM;
                            } else {
                                busType = DBusConnection.SESSION;
                            }
                            conn = DBusConnection.getConnection(busType);
                            conn.exportObject(SIGNAL_OBJECTPATH, m);
                            conn.requestBusName(SIGNAL_BUSNAME);
                        } catch (UnsatisfiedLinkError e) {
                            System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                            return 1;
                        } catch (DBusException e) {
                            e.printStackTrace();
                            return 2;
                        }
                        ignoreAttachments = ns.getBoolean("ignore_attachments");
                        try {
                            m.receiveMessages(1, TimeUnit.HOURS, false, ignoreAttachments, ns.getBoolean("json") ? new JsonDbusReceiveMessageHandler(m, conn, Main.SIGNAL_OBJECTPATH) : new DbusReceiveMessageHandler(m, conn, Main.SIGNAL_OBJECTPATH));
                        } catch (IOException e) {
                            System.err.println("Error while receiving messages: " + e.getMessage());
                            return 3;
                        } catch (AssertionError e) {
                            handleAssertionError(e);
                            return 1;
                        }
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }

                    break;
            }
            return 0;
        } finally {
            if (dBusConn != null) {
                dBusConn.disconnect();
            }
        }
    }

    private static void printIdentityFingerprint(Manager m, String theirUsername, JsonIdentityKeyStore.Identity theirId) {
        String digits = Util.formatSafetyNumber(m.computeSafetyNumber(theirUsername, theirId.getIdentityKey()));
        System.out.println(String.format("%s: %s Added: %s Fingerprint: %s Safety Number: %s", theirUsername,
                theirId.getTrustLevel(), theirId.getDateAdded(), Hex.toStringCondensed(theirId.getFingerprint()), digits));
    }

    private static void printGroup(GroupInfo group, boolean detailed) {
        if (detailed) {
            System.out.println(String.format("Id: %s Name: %s  Active: %s Members: %s",
                    Base64.encodeBytes(group.groupId), group.name, group.active, group.members));
        } else {
            System.out.println(String.format("Id: %s Name: %s  Active: %s", Base64.encodeBytes(group.groupId),
                    group.name, group.active));
        }
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("signal-cli")
                .build()
                .defaultHelp(true)
                .description("Commandline interface for Signal.")
                .version(BaseConfig.PROJECT_NAME + " " + BaseConfig.PROJECT_VERSION);

        parser.addArgument("-v", "--version")
                .help("Show package version.")
                .action(Arguments.version());
        parser.addArgument("--config")
                .help("Set the path, where to store the config (Default: $HOME/.config/signal).");

        MutuallyExclusiveGroup mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("-u", "--username")
                .help("Specify your phone number, that will be used for verification.");
        mut.addArgument("--dbus")
                .help("Make request via user dbus.")
                .action(Arguments.storeTrue());
        mut.addArgument("--dbus-system")
                .help("Make request via system dbus.")
                .action(Arguments.storeTrue());

        Subparsers subparsers = parser.addSubparsers()
                .title("subcommands")
                .dest("command")
                .description("valid subcommands")
                .help("additional help");

        Subparser parserLink = subparsers.addParser("link");
        parserLink.addArgument("-n", "--name")
                .help("Specify a name to describe this new device.");

        Subparser parserAddDevice = subparsers.addParser("addDevice");
        parserAddDevice.addArgument("--uri")
                .required(true)
                .help("Specify the uri contained in the QR code shown by the new device.");

        Subparser parserDevices = subparsers.addParser("listDevices");

        Subparser parserRemoveDevice = subparsers.addParser("removeDevice");
        parserRemoveDevice.addArgument("-d", "--deviceId")
                .type(int.class)
                .required(true)
                .help("Specify the device you want to remove. Use listDevices to see the deviceIds.");

        Subparser parserRegister = subparsers.addParser("register");
        parserRegister.addArgument("-v", "--voice")
                .help("The verification should be done over voice, not sms.")
                .action(Arguments.storeTrue());

        Subparser parserUnregister = subparsers.addParser("unregister");
        parserUnregister.help("Unregister the current device from the signal server.");

        Subparser parserUpdateAccount = subparsers.addParser("updateAccount");
        parserUpdateAccount.help("Update the account attributes on the signal server.");

        Subparser parserSetPin = subparsers.addParser("setPin");
        parserSetPin.addArgument("registrationLockPin")
                .help("The registration lock PIN, that will be required for new registrations (resets after 7 days of inactivity)");

        Subparser parserRemovePin = subparsers.addParser("removePin");

        Subparser parserVerify = subparsers.addParser("verify");
        parserVerify.addArgument("verificationCode")
                .help("The verification code you received via sms or voice call.");
        parserVerify.addArgument("-p", "--pin")
                .help("The registration lock PIN, that was set by the user (Optional)");

        Subparser parserSend = subparsers.addParser("send");
        parserSend.addArgument("-g", "--group")
                .help("Specify the recipient group ID.");
        parserSend.addArgument("recipient")
                .help("Specify the recipients' phone number.")
                .nargs("*");
        parserSend.addArgument("-m", "--message")
                .help("Specify the message, if missing standard input is used.");
        parserSend.addArgument("-a", "--attachment")
                .nargs("*")
                .help("Add file as attachment");
        parserSend.addArgument("-e", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());

        Subparser parserLeaveGroup = subparsers.addParser("quitGroup");
        parserLeaveGroup.addArgument("-g", "--group")
                .required(true)
                .help("Specify the recipient group ID.");

        Subparser parserUpdateGroup = subparsers.addParser("updateGroup");
        parserUpdateGroup.addArgument("-g", "--group")
                .help("Specify the recipient group ID.");
        parserUpdateGroup.addArgument("-n", "--name")
                .help("Specify the new group name.");
        parserUpdateGroup.addArgument("-a", "--avatar")
                .help("Specify a new group avatar image file");
        parserUpdateGroup.addArgument("-m", "--member")
                .nargs("*")
                .help("Specify one or more members to add to the group");

        Subparser parserListGroups = subparsers.addParser("listGroups");
        parserListGroups.addArgument("-d", "--detailed").action(Arguments.storeTrue())
                .help("List members of each group");
        parserListGroups.help("List group name and ids");

        Subparser parserListIdentities = subparsers.addParser("listIdentities");
        parserListIdentities.addArgument("-n", "--number")
                .help("Only show identity keys for the given phone number.");

        Subparser parserTrust = subparsers.addParser("trust");
        parserTrust.addArgument("number")
                .help("Specify the phone number, for which to set the trust.")
                .required(true);
        MutuallyExclusiveGroup mutTrust = parserTrust.addMutuallyExclusiveGroup();
        mutTrust.addArgument("-a", "--trust-all-known-keys")
                .help("Trust all known keys of this user, only use this for testing.")
                .action(Arguments.storeTrue());
        mutTrust.addArgument("-v", "--verified-fingerprint")
                .help("Specify the fingerprint of the key, only use this option if you have verified the fingerprint.");

        Subparser parserReceive = subparsers.addParser("receive");
        parserReceive.addArgument("-t", "--timeout")
                .type(double.class)
                .help("Number of seconds to wait for new messages (negative values disable timeout)");
        parserReceive.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        parserReceive.addArgument("--json")
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());

        Subparser parserDaemon = subparsers.addParser("daemon");
        parserDaemon.addArgument("--system")
                .action(Arguments.storeTrue())
                .help("Use DBus system bus instead of user bus.");
        parserDaemon.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        parserDaemon.addArgument("--json")
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());

        try {
            Namespace ns = parser.parseArgs(args);
            if ("link".equals(ns.getString("command"))) {
                if (ns.getString("username") != null) {
                    parser.printUsage();
                    System.err.println("You cannot specify a username (phone number) when linking");
                    System.exit(2);
                }
            } else if (!ns.getBoolean("dbus") && !ns.getBoolean("dbus_system")) {
                if (ns.getString("username") == null) {
                    parser.printUsage();
                    System.err.println("You need to specify a username (phone number)");
                    System.exit(2);
                }
                if (!PhoneNumberFormatter.isValidNumber(ns.getString("username"))) {
                    System.err.println("Invalid username (phone number), make sure you include the country code.");
                    System.exit(2);
                }
            }
            if (ns.getList("recipient") != null && !ns.getList("recipient").isEmpty() && ns.getString("group") != null) {
                System.err.println("You cannot specify recipients by phone number and groups a the same time");
                System.exit(2);
            }
            return ns;
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            return null;
        }
    }
}
