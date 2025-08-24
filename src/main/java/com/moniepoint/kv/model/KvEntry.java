package com.moniepoint.kv.model;

public class KvEntry {

	private String key;
	private String value;

	public KvEntry() {
		// default constructor needed by Jackson
	}

	public KvEntry(String key, String value) {
		this.key = key;
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
