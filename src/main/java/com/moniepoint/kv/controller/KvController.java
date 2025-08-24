package com.moniepoint.kv.controller;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moniepoint.kv.model.KvEntry;
import com.moniepoint.kv.service.KvService;
import com.moniepoint.kv.util.Utils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Moniepoint API Endpoints")
@RestController
@RequestMapping(value = "${service.endpoint}")
public class KvController {

	private static final Logger log = LoggerFactory.getLogger(KvController.class);

	@Autowired
	private KvService kvService;

	@Autowired
	private Utils utils;

	@Operation(summary = "Health check", responses = @ApiResponse(responseCode = "200"))
	@GetMapping("/health")
	public ResponseEntity<String> health() {
		log.debug("[API] GET /health");

		return ResponseEntity.ok("OK");
	}

	@Operation(summary = "Put/overwrite value for key", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = {
			@Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(type = "string")) }), responses = {
					@ApiResponse(responseCode = "204", description = "Stored"),
					@ApiResponse(responseCode = "400", description = "Invalid key", content = @Content),
					@ApiResponse(responseCode = "413", description = "Value too large", content = @Content) })
	@PutMapping(value = "/{key}", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<Void> put(@Parameter(description = "Key (ASCII; reasonable length)") @PathVariable String key,
			@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Raw value bytes (text/plain)") @RequestBody(required = false) byte[] value)
			throws Exception {
		log.info("[API] PUT key='{}'", key);

		if (!utils.sanitize(key) || !utils.sanitize(String.valueOf(value))) {
			log.warn("[API] PUT invalid input (null/empty)");
			throw new IllegalArgumentException("key/value can not be null/empty");
		}

		return kvService.put(key, value);
	}

	@Operation(summary = "Read value by key", responses = {
			@ApiResponse(responseCode = "200", description = "Value as plain text", content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(type = "string"))),
			@ApiResponse(responseCode = "404", description = "Not found", content = @Content) })
	@GetMapping(value = "/{key}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<byte[]> read(@PathVariable String key) throws Exception {
		log.info("[API] READ key='{}'", key);

		if (!utils.sanitize(key)) {
			log.warn("[API] READ invalid input (null/empty)");
			throw new IllegalArgumentException("key can not be null/empty");
		}

		Optional<byte[]> v = kvService.read(key);
		log.debug("[API] READ key='{}' -> 200 ({} bytes)", key, v.map(b -> b.length).orElse(0));

		return v.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());

	}

	@Operation(summary = "Read key/value pairs in (start, end)", description = "Returns lines of 'key=value' (UTF-8). End is exclusive.", responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(type = "string"))))
	@GetMapping(value = "/range", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> readKeyRange(
			@Parameter(description = "Inclusive start key") @RequestParam String start,
			@Parameter(description = "Exclusive end key") @RequestParam String end) throws IOException {
		log.info("[API] READKEYRANGE start='{}' end='{}'", start, end);

		if (!utils.sanitize(start) || !utils.sanitize(String.valueOf(end))) {
			log.warn("[API] READKEYRANGE invalid input (null/empty)");
			throw new IllegalArgumentException("start/end can not be null/empty");
		}

		StringBuilder sb = kvService.readKeyRange(start, end);
		log.debug("[API] RANGE start='{}' end='{}' -> 200 ({} pairs)", start, end,
				sb.toString() == null ? 0 : sb.toString().length());

		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(sb.toString());

	}

	@Operation(summary = "Batch put values for keys", description = "Accepts a JSON array of {key, value} objects and stores them atomically for this request.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "Sample batch", value = "[\n"
			+ "  {\"key\":\"k1\",\"value\":\"v1\"},\n" + "  {\"key\":\"k2\",\"value\":\"v2\"},\n"
			+ "  {\"key\":\"k3\",\"value\":\"v3\"}\n" + "]"))), responses = {
					@ApiResponse(responseCode = "204", description = "Stored"),
					@ApiResponse(responseCode = "400", description = "Invalid input", content = @Content) })
	@PostMapping(value = "/batchPut", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> batchPut(
			@org.springframework.web.bind.annotation.RequestBody java.util.List<KvEntry> entries) throws Exception {
		log.info("[API] BATCHPUT entries={}", entries == null ? 0 : entries.size());

		if (entries == null || entries.isEmpty()) {
			log.warn("[API] BATCHPUT invalid input (null/empty)");
			return ResponseEntity.badRequest().build();
		}

		return kvService.batchPut(entries);
	}

	@Operation(summary = "Delete a key", responses = {
			@ApiResponse(responseCode = "204", description = "Deleted (idempotent)"),
			@ApiResponse(responseCode = "400", description = "Invalid input", content = @Content) })
	@DeleteMapping("/{key}")
	public ResponseEntity<Void> delete(@PathVariable String key) throws IOException {
		log.info("[API] DELETE key='{}'", key);

		if (!utils.sanitize(key)) {
			log.warn("[API] DELETE invalid input (null/empty)");
			throw new IllegalArgumentException("key can not be null/empty");
		}

		return kvService.delete(key);

	}
}
