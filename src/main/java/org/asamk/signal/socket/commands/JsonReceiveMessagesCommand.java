/*
  Copyright (C) 2021 sehaas and contributors

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
package org.asamk.signal.socket.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.socket.json.JsonErrorResponse;
import org.asamk.signal.socket.json.JsonMessageAttachment;
import org.asamk.signal.socket.json.JsonReceivedMessage;
import org.asamk.signal.socket.json.JsonReceivedMessageResponse;
import org.asamk.signal.socket.json.JsonResponse;
import org.asamk.signal.socket.json.JsonResponse.StatusCode;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = JsonReceiveMessagesCommand.class)
public class JsonReceiveMessagesCommand extends AbstractCommand {
	private long timeout = 1000;
	private boolean ignoreAttachments = false;

	private final FileAttribute<?> tempFileAttributes = PosixFilePermissions
			.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

	@Override
	public JsonResponse apply(final Manager manager) {
		final JsonReceivedMessageResponse result = new JsonReceivedMessageResponse(this);
		try {
			manager.receiveMessages(getTimeout(), TimeUnit.MILLISECONDS, true, false,
					(final SignalServiceEnvelope envelope, final SignalServiceContent content, final Throwable e) -> {
						if (content != null) {
							try {
								final JsonReceivedMessage msg = new JsonReceivedMessage(content.getTimestamp(),
										content.getSender().getNumber().orNull());
								if (content.getDataMessage().isPresent()) {
									final SignalServiceDataMessage message = content.getDataMessage().get();
									msg.withBody(message.getBody().orNull());

									Optional.ofNullable(message.getGroupContext().orNull()).map(GroupUtils::getGroupId)
											.map(GroupId::toBase64).ifPresent(msg::withGroupId);

									if (!isIgnoreAttachments() && message.getAttachments().isPresent()) {
										final List<SignalServiceAttachment> attachments = message.getAttachments()
												.get();
										for (final SignalServiceAttachment a : attachments) {
											final JsonMessageAttachment jsonAttachment = new JsonMessageAttachment();
											if (a.isPointer()) {
												final SignalServiceAttachmentPointer pointer = a.asPointer();
												final File tempFile = Files
														.createTempFile(null, null, tempFileAttributes).toFile();
												jsonAttachment.setData(IOUtils.readFully(
														manager.retrieveAttachmentAsStream(pointer, tempFile)));
												tempFile.delete();
												jsonAttachment.setFilename(pointer.getFileName().orNull());
											} else if (a.isStream()) {
												final SignalServiceAttachmentStream stream = a.asStream();
												jsonAttachment.setData(IOUtils.readFully(stream.getInputStream()));
												jsonAttachment.setFilename(stream.getFileName().orNull());
											}
											msg.withAttachment(jsonAttachment);
										}
									}
								}
								result.addMessage(msg);
							} catch (final IOException | InvalidMessageException | MissingConfigurationException ex) {
								result.addMessage(
										new JsonErrorResponse(StatusCode.MESSAGE_PARSING_ERROR, ex.getMessage()));
							}
						} else {
							result.addMessage(
									new JsonErrorResponse(StatusCode.MISSING_MESSAGE_CONTENT, "missing content"));
						}
						if (e != null) {
							result.addMessage(new JsonErrorResponse(StatusCode.MESSAGE_ERROR, e.getMessage()));
						}
					});
			return result;
		} catch (final IOException e) {
			return new JsonErrorResponse(this, StatusCode.UNKNOWN, e.getMessage());
		}
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(final long timeout) {
		this.timeout = timeout;
	}

	public boolean isIgnoreAttachments() {
		return ignoreAttachments;
	}

	public void setIgnoreAttachments(final boolean ignoreAttachments) {
		this.ignoreAttachments = ignoreAttachments;
	}
}
