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
package org.asamk.signal.commands;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.socket.JsonSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class SocketCommand implements LocalCommand {
	private final static Logger logger = LoggerFactory.getLogger(SocketCommand.class);

	@Override
	public void attachToSubparser(final Subparser subparser) {
		subparser.addArgument("-p", "--port").type(Integer.class).setDefault(6789).help("Port to bind");
		subparser.addArgument("-a", "--address").setDefault("127.0.0.1").help("Address to bind");
	}

	@Override
	public int handleCommand(final Namespace ns, final Manager m) {
		final Integer port = ns.getInt("port");
		InetAddress address = null;
		final String addressParam = ns.getString("address");
		try {
			address = InetAddress.getByName(addressParam);
		} catch (final UnknownHostException e1) {
			logger.error("Invalid bind address: %s\n", addressParam);
			return 1;
		}
		try (ServerSocket serverSocket = new ServerSocket(port, 0, address)) {
			while (true) {
				try {
					final Socket socket = serverSocket.accept();
					final InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
					logger.debug("Client connected from {}:{}", remote.getHostName(), remote.getPort());
					new Thread(new JsonSocketHandler(m, socket)).start();
				} catch (final IOException ioe) {
					logger.error("Client connection failed with '{}'", ioe.getMessage(), ioe);
				}
			}
		} catch (final IOException e) {
			logger.error("Cannot open socket ({}:{}): {}", addressParam, port, e.getMessage());
			return 1;
		}
	}

}
