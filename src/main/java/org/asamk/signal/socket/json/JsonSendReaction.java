package org.asamk.signal.socket.json;

public class JsonSendReaction {
	private String emoji;
	private String author;
	private boolean remove = false;
	private long timestamp;

	public String getEmoji() {
		return emoji;
	}

	public void setEmoji(final String emoji) {
		this.emoji = emoji;
	}

	public void setAuthor(final String author) {
		this.author = author;
	}

	public String getAuthor() {
		return author;
	}

	public boolean isRemove() {
		return remove;
	}

	public void setRemove(final boolean remove) {
		this.remove = remove;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(final long timestamp) {
		this.timestamp = timestamp;
	}
}
