package com.moniepoint.kv.model;

import java.util.Arrays;
import java.util.Objects;

public final class KvPair {
	private final String key;
	private final byte[] value;

	public KvPair(String key, byte[] value) {
		this.key = Objects.requireNonNull(key, "key");
		this.value = Objects.requireNonNull(value, "value");
	}

	public String key() {
		return key;
	}

	public byte[] value() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof KvPair))
			return false;
		KvPair that = (KvPair) o;
		return key.equals(that.key) && Arrays.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		int result = key.hashCode();
		result = 31 * result + Arrays.hashCode(value);
		return result;
	}

	@Override
	public String toString() {
		return "KvPair{key='" + key + "', valueLen=" + value.length + "}";
	}
}
