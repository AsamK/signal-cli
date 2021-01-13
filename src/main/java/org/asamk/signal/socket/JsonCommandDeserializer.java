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
package org.asamk.signal.socket;

import java.io.IOException;
import java.util.Optional;

import org.asamk.signal.socket.commands.AbstractCommand;
import org.asamk.signal.socket.commands.JsonReceiveMessagesCommand;
import org.asamk.signal.socket.commands.JsonSendMessageCommand;
import org.asamk.signal.socket.commands.JsonSendReactionCommand;
import org.asamk.signal.socket.commands.JsonUnknownCommand;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonCommandDeserializer extends JsonDeserializer<AbstractCommand> {

	@Override
	public AbstractCommand deserialize(final JsonParser jp, final DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		final ObjectMapper mapper = (ObjectMapper) jp.getCodec();
		final ObjectNode root = mapper.readTree(jp);
		final Optional<String> command = Optional.ofNullable(root).map(r -> r.get("command")).map(JsonNode::asText);
		if (command.isEmpty()) {
			return new JsonUnknownCommand();
		}
		Class<? extends AbstractCommand> instanceClass;
		switch (command.get()) {
		case "send_message":
			instanceClass = JsonSendMessageCommand.class;
			break;
		case "receive_messages":
			instanceClass = JsonReceiveMessagesCommand.class;
			break;
		case "send_reaction":
			instanceClass = JsonSendReactionCommand.class;
			break;
		default:
			instanceClass = JsonUnknownCommand.class;
			break;
		}
		return mapper.treeToValue(root, instanceClass);
	}
}
