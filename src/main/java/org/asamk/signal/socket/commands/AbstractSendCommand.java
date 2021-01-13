package org.asamk.signal.socket.commands;

public abstract class AbstractSendCommand extends AbstractCommand {

	protected String recipient;
	protected String groupId;

	public AbstractSendCommand() {
		super();
	}

	public void setRecipient(final String recipient) {
		this.recipient = recipient;
	}

	public String getRecipient() {
		return recipient;
	}

	public void setGroupId(final String groupId) {
		this.groupId = groupId;
	}

	public String getGroupId() {
		return groupId;
	}

}