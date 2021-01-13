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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.socket.json.JsonErrorResponse;
import org.asamk.signal.socket.json.JsonResponse;
import org.asamk.signal.socket.json.JsonResponse.StatusCode;
import org.asamk.signal.socket.json.JsonSendMessageResponse;
import org.asamk.signal.socket.json.JsonSendReaction;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = JsonSendReactionCommand.class)
public class JsonSendReactionCommand extends AbstractSendCommand {
	private JsonSendReaction reaction;

	@Override
	public JsonResponse apply(final Manager manager) {
		final JsonSendReaction r = getReaction();
		if (r == null) {
			return new JsonErrorResponse(this, StatusCode.MISSING_REACTION, "incomplete request");
		}
		try {
			Pair<Long, List<SendMessageResult>> result;
			if (recipient != null && groupId == null) {
				result = manager.sendMessageReaction(r.getEmoji(), r.isRemove(), r.getAuthor(), r.getTimestamp(),
						Arrays.asList(getRecipient()));
			} else if (groupId != null && recipient == null) {
				result = manager.sendGroupMessageReaction(r.getEmoji(), r.isRemove(), r.getAuthor(), r.getTimestamp(),
						GroupId.fromBase64(groupId));
			} else {
				return new JsonErrorResponse(this, StatusCode.INVALID_RECIPIENT,
						"'recipient' xor 'groupId' must be set");
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

	public JsonSendReaction getReaction() {
		return reaction;
	}

	public void setReaction(final JsonSendReaction reaction) {
		this.reaction = reaction;
	}
}
