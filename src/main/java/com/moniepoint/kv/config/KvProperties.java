package com.moniepoint.kv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.moniepoint.kv.util.BitcaskStore;

@ConfigurationProperties(prefix = "kv")
public class KvProperties {
	private String dataDir = "./data";
	private BitcaskStore.SyncMode syncMode = BitcaskStore.SyncMode.ALWAYS;
	private int batchSyncEvery = 100;
	private long syncIntervalMs = 50;
	private long compactThresholdBytes = 0;

	public String getDataDir() {
		return dataDir;
	}

	public void setDataDir(String dataDir) {
		this.dataDir = dataDir;
	}

	public BitcaskStore.SyncMode getSyncMode() {
		return syncMode;
	}

	public void setSyncMode(BitcaskStore.SyncMode syncMode) {
		this.syncMode = syncMode;
	}

	public int getBatchSyncEvery() {
		return batchSyncEvery;
	}

	public void setBatchSyncEvery(int batchSyncEvery) {
		this.batchSyncEvery = batchSyncEvery;
	}

	public long getSyncIntervalMs() {
		return syncIntervalMs;
	}

	public void setSyncIntervalMs(long syncIntervalMs) {
		this.syncIntervalMs = syncIntervalMs;
	}

	public long getCompactThresholdBytes() {
		return compactThresholdBytes;
	}

	public void setCompactThresholdBytes(long compactThresholdBytes) {
		this.compactThresholdBytes = compactThresholdBytes;
	}

}
