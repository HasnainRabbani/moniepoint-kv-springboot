package com.moniepoint.kv.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class Bytes {
	private Bytes() {
	}

	static byte[] ofUtf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	static void putInt(ByteBuffer bb, int v) {
		bb.putInt(v);
	}

	static int getInt(ByteBuffer bb) {
		return bb.getInt();
	}
}
