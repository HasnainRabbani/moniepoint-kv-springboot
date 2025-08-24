package com.moniepoint.kv.model;

import java.util.zip.CRC32;

public final class Crc32s {
	public static int of(byte[] a, byte[] b) {
		CRC32 crc = new CRC32();
		crc.update(a, 0, a.length);
		crc.update(b, 0, b.length);
		return (int) crc.getValue(); // CRC32 is 32-bit
	}

	public static int of(byte[] a) {
		CRC32 crc = new CRC32();
		crc.update(a, 0, a.length);
		return (int) crc.getValue();
	}

	private Crc32s() {
	}
}
