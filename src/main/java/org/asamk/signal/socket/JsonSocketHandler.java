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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.socket.commands.AbstractCommand;
import org.asamk.signal.socket.json.JsonErrorResponse;
import org.asamk.signal.socket.json.JsonResponse;
import org.asamk.signal.socket.json.JsonResponse.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSocketHandler implements Runnable {
	private final static Logger logger = LoggerFactory.getLogger(JsonSocketHandler.class);

	private final Manager manager;
	private final Socket socket;
	private final InputStream inputStream;
	private final OutputStream outputStream;
	private final JsonParser parser;
	private final JsonGenerator writer;

	public JsonSocketHandler(final Manager manager, final Socket socket) throws IOException {
		this.manager = manager;
		this.socket = socket;
		inputStream = socket.getInputStream();
		outputStream = socket.getOutputStream();
		final JsonFactory factory = new MappingJsonFactory();
		final ObjectMapper objectMapper = new ObjectMapper(factory).enable(Feature.AUTO_CLOSE_SOURCE)
				.enable(Feature.ALLOW_SINGLE_QUOTES).enable(Feature.ALLOW_UNQUOTED_FIELD_NAMES)
				.enable(Feature.ALLOW_TRAILING_COMMA).enable(Feature.ALLOW_UNQUOTED_FIELD_NAMES)
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).disable(Feature.AUTO_CLOSE_SOURCE);
		factory.setCodec(objectMapper);
		parser = factory.createParser(inputStream);
		writer = factory.createGenerator(outputStream);
	}

	@Override
	public void run() {
		try {
			do {
				try {
					final JsonToken curToken = parser.nextToken();
					if (curToken == null) {
						break;
					}
					switch (curToken) {
					case START_OBJECT:
						final AbstractCommand command = parser.readValueAs(AbstractCommand.class);
						final JsonResponse result = command.apply(manager);
						writer.writeObject(result);
						break;
					case START_ARRAY:
						break;
					case END_ARRAY:
						break;
					default:
						writer.writeObject(new JsonErrorResponse(null, StatusCode.INVALID_JSON, curToken.asString()));
					}
				} catch (final JsonEOFException eof) {
					break;
				} catch (final JsonParseException e) {
					writer.writeObject(new JsonErrorResponse(null, StatusCode.INVALID_JSON, e.getMessage()));
				}
			} while (true);

			writer.flush();
			socket.close();
		} catch (final IOException e) {
			logger.error("Connection failed with '{}'", e.getMessage(), e);
		}
	}

}
