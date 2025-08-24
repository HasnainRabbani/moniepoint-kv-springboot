package com.moniepoint.kv.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;

import com.moniepoint.kv.model.KvEntry;

public interface KvService {

	ResponseEntity<Void> put(String key, byte[] value) throws IOException;

	Optional<byte[]> read(String key) throws IOException;

	StringBuilder readKeyRange(String start, String end) throws IOException;

	ResponseEntity<Void> batchPut(List<KvEntry> oentries) throws IOException;

	ResponseEntity<Void> delete(String key) throws IOException;

}
