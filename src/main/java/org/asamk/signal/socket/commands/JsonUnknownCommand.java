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

import org.asamk.signal.manager.Manager;
import org.asamk.signal.socket.json.JsonResponse;
import org.asamk.signal.socket.json.JsonResponse.StatusCode;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = JsonUnknownCommand.class)
public class JsonUnknownCommand extends AbstractCommand {

	@Override
	public JsonResponse apply(final Manager manager) {
		return new JsonResponse(this, StatusCode.UNKNOWN_COMMAND);
	}

}
