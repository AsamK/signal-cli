package org.asamk.signal.commands;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.api.UserAlreadyExistsException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

public class LinkCommand implements ProvisioningCommand {

    private static final Logger logger = LoggerFactory.getLogger(LinkCommand.class);

    @Override
    public String getName() {
        return "link";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Link to an existing device, instead of registering a new number.");
        subparser.addArgument("-n", "--name").help("Specify a name to describe this new device.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final ProvisioningManager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var writer = (PlainTextWriter) outputWriter;

        var deviceName = ns.getString("name");
        if (deviceName == null) {
            deviceName = "cli";
        }
        try {
            final URI deviceLinkUri = m.getDeviceLinkUri();
            if (System.console() != null) {
                printQrCode(writer, deviceLinkUri);
            }
            writer.println("{}", deviceLinkUri);
            var number = m.finishDeviceLink(deviceName);
            writer.println("Associated with: {}", number);
        } catch (TimeoutException e) {
            throw new UserErrorException("Link request timed out, please try again.");
        } catch (IOException e) {
            throw new IOErrorException("Link request error: " + e.getMessage(), e);
        } catch (UserAlreadyExistsException e) {
            throw new UserErrorException("The user "
                    + e.getNumber()
                    + " already exists\nDelete \""
                    + e.getFileName()
                    + "\" before trying again.");
        }
    }

    private void printQrCode(final PlainTextWriter writer, final URI deviceLinkUri) {
        try {
            var bitMatrix = new QRCodeWriter().encode(deviceLinkUri.toString(), BarcodeFormat.QR_CODE, 0, 0);
            for (int y = 0; y < bitMatrix.getHeight(); y += 2) {
                writer.println(formatQRCodeLinePair(bitMatrix, y));
            }
        } catch (WriterException e) {
            logger.error("Failed to generate QR code", e);
        }
    }

    private static String formatQRCodeLinePair(final BitMatrix bitMatrix, final int y) {
        var line = new StringBuilder();
        for (int x = 0; x < bitMatrix.getWidth(); x++) {
            boolean upper = bitMatrix.get(x, y);
            boolean lower = y + 1 < bitMatrix.getHeight() && bitMatrix.get(x, y + 1);
            line.append((upper && lower) ? "█" : (upper ? "▀" : (lower ? "▄" : " ")));
        }
        return line.toString();
    }
}
