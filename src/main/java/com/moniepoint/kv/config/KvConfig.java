package com.moniepoint.kv.config;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moniepoint.kv.util.BitcaskStore;

@Configuration
@EnableConfigurationProperties(KvProperties.class)
public class KvConfig {
	private static final Logger log = LoggerFactory.getLogger(KvConfig.class);

	private final KvProperties props;

	public KvConfig(KvProperties props) {
		this.props = props;
	}

	@Bean(destroyMethod = "close") // ensures close() called on shutdown
	public BitcaskStore store() throws IOException {
		log.info(
				"Booting KV store with config: dataDir='{}', syncMode='{}', batchSyncEvery={}, syncIntervalMs={}, compactThresholdBytes={}",
				props.getDataDir(), props.getSyncMode(), props.getBatchSyncEvery(), props.getSyncIntervalMs(),
				props.getCompactThresholdBytes());

		try {
			BitcaskStore s = new BitcaskStore(Path.of(props.getDataDir()),
					BitcaskStore.SyncMode.valueOf(props.getSyncMode().toString().trim().toUpperCase()),
					props.getBatchSyncEvery(), props.getSyncIntervalMs(), props.getCompactThresholdBytes());
			log.info("KV store bean created: store@{}", System.identityHashCode(s));
			return s;
		} catch (IllegalArgumentException e) {
			log.error("Invalid syncMode '{}'. Allowed: {}", props.getSyncMode(),
					java.util.Arrays.toString(BitcaskStore.SyncMode.values()));
			throw e; // keep behavior
		} catch (IOException ioe) {
			log.error("Failed to initialize KV store at '{}'", props.getDataDir(), ioe);
			throw ioe; // keep behavior
		}
	}
}
