package com.moniepoint.kv.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.moniepoint.kv.model.KvEntry;
import com.moniepoint.kv.model.KvPair;
import com.moniepoint.kv.service.KvService;
import com.moniepoint.kv.util.BitcaskStore;

@Service
public class KvServiceImpl implements KvService {

	private static final Logger log = LoggerFactory.getLogger(KvServiceImpl.class);

	@Autowired
	private BitcaskStore store;

	@Override
	public ResponseEntity<Void> put(String key, byte[] value) throws IOException {
		store.put(key, value == null ? new byte[0] : value);

		log.debug("[API] PUT key='{}' -> 204", key);
		return ResponseEntity.noContent().build();
	}

	@Override
	public Optional<byte[]> read(String key) throws IOException {
		Optional<byte[]> v = store.get(key);
		return v;
	}

	@Override
	public StringBuilder readKeyRange(String start, String end) throws IOException {
		List<KvPair> pairs = store.getRange(start, end);
		StringBuilder sb = new StringBuilder();
		for (KvPair p : pairs) {
			sb.append(p.key()).append("=").append(new String(p.value(), StandardCharsets.UTF_8)).append("\n");
		}
		return sb;
	}

	@Override
	public ResponseEntity<Void> batchPut(List<KvEntry> entries) throws IOException {

		List<KvPair> items = new ArrayList<>(entries.size());
		for (KvEntry e : entries) {
			// Additional defensive checks (in case validation is off)
			if (e == null || e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
				return ResponseEntity.badRequest().build();
			}
			items.add(new KvPair(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8)));
		}

		store.batchPut(items);

		log.debug("[API] BATCHPUT stored -> 204");
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<Void> delete(String key) throws IOException {
		store.delete(key);

		log.debug("[API] DELETE key='{}' -> 204", key);
		return ResponseEntity.noContent().build();
	}

}
