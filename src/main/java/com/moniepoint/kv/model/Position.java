package com.moniepoint.kv.model;

public final class Position {
	public final int fileId;
	public final long offset;
	public final int valueLen;
	public final boolean tombstone;

	public Position(int fileId, long offset, int valueLen, boolean tombstone) {
		this.fileId = fileId;
		this.offset = offset;
		this.valueLen = valueLen;
		this.tombstone = tombstone;
	}
}
