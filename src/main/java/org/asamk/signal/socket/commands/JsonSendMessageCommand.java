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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.socket.json.JsonErrorResponse;
import org.asamk.signal.socket.json.JsonMessageAttachment;
import org.asamk.signal.socket.json.JsonResponse;
import org.asamk.signal.socket.json.JsonResponse.StatusCode;
import org.asamk.signal.socket.json.JsonSendMessageData;
import org.asamk.signal.socket.json.JsonSendMessageResponse;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.StreamDetails;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = JsonSendMessageCommand.class)
public class JsonSendMessageCommand extends AbstractSendCommand {

	private JsonSendMessageData dataMessage;

	@Override
	public JsonResponse apply(final Manager manager) {
		final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
				.withBody(dataMessage.getMessage());

		if (dataMessage.getAttachments() != null) {
			final SignalServiceMessageSender messageSender = manager.createMessageSender();
			final List<SignalServiceAttachment> attachmentPointers = new ArrayList<>();
			try {
				for (final JsonMessageAttachment a : dataMessage.getAttachments()) {
					final ByteArrayInputStream stream = new ByteArrayInputStream(a.getData());
					final int size = stream.available();
					final String mime = URLConnection.guessContentTypeFromStream(stream);

					final SignalServiceAttachmentStream attachment = AttachmentUtils.createAttachment(
							new StreamDetails(stream, mime, size), Optional.fromNullable(a.getFilename()));

					if (attachment.isStream()) {
						attachmentPointers.add(messageSender.uploadAttachment(attachment.asStream()));
					} else if (attachment.isPointer()) {
						attachmentPointers.add(attachment.asPointer());
					}
				}
			} catch (final IOException e) {
				return new JsonErrorResponse(this, StatusCode.INVALID_ATTACHMENT, e.getMessage());
			}
			messageBuilder.withAttachments(attachmentPointers);
		}
		try {
			Pair<Long, List<SendMessageResult>> result;
			if (recipient != null && groupId == null) {
				result = manager.sendMessage(messageBuilder,
						manager.getSignalServiceAddresses(Arrays.asList(recipient)));
			} else if (groupId != null && recipient == null) {
				result = manager.sendGroupMessage(messageBuilder, GroupId.fromBase64(groupId));
			} else {
				return new JsonErrorResponse(this, StatusCode.INVALID_RECIPIENT,
						"'recipient' or 'groupId' must be set");
			}
			return new JsonSendMessageResponse(this, StatusCode.SUCCESS, result.first());
		} catch (final InvalidNumberException e) {
			return new JsonErrorResponse(this, StatusCode.INVALID_NUMBER, e.getMessage());
		} catch (final GroupNotFoundException e) {
			return new JsonErrorResponse(this, StatusCode.GROUP_NOT_FOUND, e.getMessage());
		} catch (final NotAGroupMemberException e) {
			return new JsonErrorResponse(this, StatusCode.NOT_A_GROUP_MEMBER, e.getMessage());
		} catch (final GroupIdFormatException e) {
			return new JsonErrorResponse(this, StatusCode.INVALID_NUMBER, e.getMessage());
		} catch (final IOException e) {
			return new JsonErrorResponse(this, StatusCode.UNKNOWN, e.getMessage());
		}
	}

	public JsonSendMessageData getDataMessage() {
		return dataMessage;
	}

	public void setDataMessage(final JsonSendMessageData dataMessage) {
		this.dataMessage = dataMessage;
	}
}
