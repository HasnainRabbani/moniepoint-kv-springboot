package com.moniepoint.kv.util;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moniepoint.kv.model.Crc32s;
import com.moniepoint.kv.model.KvPair;
import com.moniepoint.kv.model.Position;

/**
 * Bitcask-like KV store with a per-record CRC32 (key||value). Record layout
 * (little-endian): int keyLen int valLen byte flags (bit 0 = tombstone) int
 * crc32 (over key||value) byte[keyLen] key (UTF-8) byte[valLen] value
 */
@Component
public final class BitcaskStore implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(BitcaskStore.class);

	// --- Sync mode ---
	public enum SyncMode {
		ALWAYS, EVERY_N // (batch)
	}

	// --- Constants ---
	private static final byte FLAG_TOMBSTONE = 0x1;
	private static final int HEADER_SIZE = 13; // 4 + 4 + 1 + 4

	// --- State ---
	private final Path dataDir;
	private final SyncMode syncMode;
	private final int batchSyncEvery;
	private final long syncIntervalMs;
	private final long compactThresholdBytes; // (reserved; not used yet)

	private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
	private final TreeMap<Integer, SegmentFile> segments = new TreeMap<>(); // fileId -> segment
	private SegmentFile active;

	// fast point lookup
	private final Map<String, Position> index = new ConcurrentHashMap<>();
	// sorted key set for ranges
	private final ConcurrentSkipListMap<String, Boolean> keySet = new ConcurrentSkipListMap<>();

	// counters for sync strategies
	private long writesSinceLastSync = 0;
	private ScheduledThreadPoolExecutor intervalFlusher;

	// --- Constructors ---

	public BitcaskStore() {
		this.dataDir = null;
		this.syncMode = SyncMode.ALWAYS;
		this.batchSyncEvery = 100;
		this.syncIntervalMs = 50L;
		this.compactThresholdBytes = 0L;
	}

	public BitcaskStore(Path dataDir) throws IOException {
		this(dataDir, SyncMode.ALWAYS, 100, 50L, 0L);
	}

	public BitcaskStore(Path dataDir, SyncMode syncMode, int batchSyncEvery, long syncIntervalMs,
			long compactThresholdBytes) throws IOException {
		this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
		this.syncMode = Objects.requireNonNull(syncMode, "syncMode");
		this.batchSyncEvery = batchSyncEvery <= 0 ? 100 : batchSyncEvery;
		this.syncIntervalMs = syncIntervalMs < 0 ? 0 : syncIntervalMs;
		this.compactThresholdBytes = Math.max(0, compactThresholdBytes);

		log.info("[STORE] init dataDir={} syncMode={} batchSyncEvery={} syncIntervalMs={} compactThresholdBytes={}",
				dataDir.toAbsolutePath(), syncMode, this.batchSyncEvery, this.syncIntervalMs,
				this.compactThresholdBytes);

		initOrRecover(this.dataDir);

		if (this.syncMode == SyncMode.EVERY_N) {
			log.debug("[STORE] using EVERY_N mode (n={})", this.batchSyncEvery);
		}

	}

	// --- Public API ---

	public void put(String key, byte[] value) throws IOException {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(value, "value");
		log.info("[STORE.put] key='{}' bytes={}", key, value.length);

		byte[] k = key.getBytes(StandardCharsets.UTF_8);

		rw.writeLock().lock();
		try {
			long off = active.append(k, value, false);
			index.put(key, new Position(active.fileId(), off, value.length, false));
			keySet.put(key, Boolean.TRUE);
			if (log.isDebugEnabled())
				log.debug("[INDEX] upsert key='{}' -> {}:{}", key, active.fileId(), off);

			postWriteSync();
		} catch (IOException ioe) {
			log.error("[STORE.put] failed key='{}'", key, ioe);
			throw ioe;
		} finally {
			rw.writeLock().unlock();
		}
	}

	public Optional<byte[]> get(String key) throws IOException {
		Objects.requireNonNull(key, "key");
		log.info("[STORE.get] key='{}'", key);

		rw.readLock().lock();
		try {
			Position p = index.get(key);
			if (p == null || p.tombstone) {
				log.debug("[STORE.get] miss key='{}'", key);
				return Optional.empty();
			}
			SegmentFile sf = segments.get(p.fileId);
			if (sf == null) {
				log.warn("[STORE.get] segment missing: fileId={} for key='{}'", p.fileId, key);
				return Optional.empty();
			}
			byte[] val = sf.readValueAt(p.offset);

			log.debug("[STORE.get] hit key='{}' size={}B", key, val == null ? -1 : val.length);
			return Optional.ofNullable(val);
		} catch (IOException ioe) {
			log.error("[STORE.get] failed key='{}'", key, ioe);
			throw ioe;
		} finally {
			rw.readLock().unlock();
		}
	}

	public void delete(String key) throws IOException {
		Objects.requireNonNull(key, "key");
		log.info("[STORE.del] key='{}'", key);

		byte[] k = key.getBytes(StandardCharsets.UTF_8);
		rw.writeLock().lock();
		try {
			long off = active.append(k, new byte[0], true);
			index.put(key, new Position(active.fileId(), off, 0, true));
			keySet.remove(key);
			log.debug("[INDEX] tombstone key='{}' -> {}:{}", key, active.fileId(), off);

			postWriteSync();
		} catch (IOException ioe) {
			log.error("[STORE.del] failed key='{}'", key, ioe);
			throw ioe;
		} finally {
			rw.writeLock().unlock();
		}
	}

	public void batchPut(List<KvPair> items) throws IOException {
		if (items == null || items.isEmpty())
			return;

		log.info("[STORE.batchPut] key size='{}'", items.size());
		rw.writeLock().lock();
		try {
			for (KvPair kv : items) {
				String key = kv.key();
				byte[] value = kv.value();
				byte[] k = key.getBytes(StandardCharsets.UTF_8);
				long off = active.append(k, value, false);
				index.put(key, new Position(active.fileId(), off, value.length, false));
				keySet.put(key, Boolean.TRUE);
				postWriteSync();
			}
		} finally {
			rw.writeLock().unlock();
		}
	}

	public List<KvPair> getRange(String start, String end) throws IOException {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		if (start.compareTo(end) > 0) {
			String t = start;
			start = end;
			end = t;
		}
		List<KvPair> out = new ArrayList<>();
		log.info("[STORE.range] start='{}' end='{}' count={}", start, end, out.size());

		rw.readLock().lock();
		try {
			for (Map.Entry<String, Boolean> e : keySet.subMap(start, true, end, true).entrySet()) {
				String k = e.getKey();
				Position p = index.get(k);
				if (p == null || p.tombstone)
					continue;
				SegmentFile sf = segments.get(p.fileId);
				if (sf == null)
					continue;
				byte[] v = sf.readValueAt(p.offset);
				if (v != null)
					out.add(new KvPair(k, v));
			}
		} finally {
			rw.readLock().unlock();
		}
		return out;
	}

	@Override
	public void close() throws IOException {
		rw.writeLock().lock();
		try {
			if (intervalFlusher != null) {
				intervalFlusher.shutdownNow();
			}
			for (SegmentFile sf : segments.values()) {
				try {
					sf.force();
				} catch (Exception ignored) {
				}
				sf.close();
			}
			segments.clear();
			active = null;
		} finally {
			rw.writeLock().unlock();
		}
	}

	// --- Internals ---

	private void postWriteSync() throws IOException {
		writesSinceLastSync++;

		log.debug("[SYNC] mode: {}", syncMode);
		switch (syncMode) {
		case ALWAYS -> active.force();
		case EVERY_N -> {
			if (writesSinceLastSync >= batchSyncEvery) {
				active.force();
				writesSinceLastSync = 0;
			}
		}
		}
	}

	private void initOrRecover(Path dir) throws IOException {
		log.info("[RECOVER] scanning dir {}", dir.toAbsolutePath());

		if (!Files.exists(dir))
			Files.createDirectories(dir);

		// Discover all segment files with numeric names (with or without extension).
		List<Path> segs = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
			for (Path p : ds) {
				if (!Files.isRegularFile(p))
					continue;
				String n = p.getFileName().toString();
				if (n.startsWith("segment-") && n.endsWith(".log")) {
					segs.add(p);
				}
			}
		}

		// Sort by numeric id
		Collections.sort(segs, Comparator.comparingInt(BitcaskStore::parseFileId));

		// Open segments and recover index
		for (Path p : segs) {
			int fid = parseFileId(p);
			SegmentFile sf = new SegmentFile(fid, p, READ, WRITE); // open RW; last one becomes active
			segments.put(fid, sf);
		}

		if (segments.isEmpty()) {
			// create first segment file "1.data"
			int fid = 1;
			Path p = filePath(dir, fid);
			SegmentFile sf = new SegmentFile(fid, p, CREATE, READ, WRITE);
			segments.put(fid, sf);
			active = sf;
		} else {
			// recover all, then set active = last
			for (SegmentFile sf : segments.values()) {
				recoverSegment(sf);
			}
			active = segments.get(segments.lastKey());
		}

	}

	private static int parseFileId(Path p) {
		String name = p.getFileName().toString(); // e.g., segment-000123.log
		// segment-(\d{6}).log
		if (!name.startsWith("segment-") || !name.endsWith(".log"))
			throw new IllegalArgumentException("Not a segment: " + name);
		String digits = name.substring("segment-".length(), name.length() - ".log".length());
		return Integer.parseInt(digits); // 123
	}

	private static Path filePath(Path dir, int id) {
		// stable extension; existing files with other extensions are still discovered
		return dir.resolve(formatSegmentName(id));
	}

	private static String formatSegmentName(int id) {
		return String.format("segment-%06d.log", id);
	}

	private void recoverSegment(SegmentFile sf) throws IOException {
		// we read the segment using a separate FileChannel on its path
		try (FileChannel ch = FileChannel.open(sf.path(), READ)) {
			long pos = 0L;
			long size = ch.size();
			while (pos + HEADER_SIZE <= size) {
				// read header
				ByteBuffer hdr = ByteBuffer.allocate(HEADER_SIZE).order(LITTLE_ENDIAN);
				if (!readFully(ch, hdr, pos))
					break; // truncated
				hdr.flip();

				int klen = hdr.getInt();
				int vlen = hdr.getInt();
				byte flags = hdr.get();
				int crc = hdr.getInt();

				if (klen < 0 || vlen < 0) {
					System.err.println("[RECOVER] invalid lens at file=" + sf.fileId() + " off=" + pos);
					break;
				}

				long keyPos = pos + HEADER_SIZE;
				long valPos = keyPos + klen;
				long nextPos = valPos + vlen;

				if (nextPos > size) {
					// torn write at end
					System.err
							.println("[RECOVER] torn write at file=" + sf.fileId() + " off=" + pos + " stopping scan");
					break;
				}

				// read key
				ByteBuffer kb = ByteBuffer.allocate(klen);
				if (!readFully(ch, kb, keyPos))
					break;
				kb.flip();
				byte[] key = new byte[klen];
				kb.get(key);

				// read value
				byte[] value = new byte[vlen];
				if (vlen > 0) {
					ByteBuffer vb = ByteBuffer.allocate(vlen);
					if (!readFully(ch, vb, valPos))
						break;
					vb.flip();
					vb.get(value);
				}

				// CRC check
				int got = Crc32s.of(key, value);
				if (got != crc) {
					System.err.println("[RECOVER] CRC mismatch file=" + sf.fileId() + " off=" + pos + " expected=" + crc
							+ " got=" + got + " stopping scan");
					break; // classic bitcask: stop on first corruption
				}

				String k = new String(key, StandardCharsets.UTF_8);
				boolean tombstone = (flags & FLAG_TOMBSTONE) != 0;
				if (tombstone) {
					index.put(k, new Position(sf.fileId(), pos, 0, true));
					keySet.remove(k);
					log.info("[RECOVER] file= {}, off= {}, key= {}, ts=true, vlen= 0", sf.fileId(), pos, k);
				} else {
					index.put(k, new Position(sf.fileId(), pos, vlen, false));
					keySet.put(k, Boolean.TRUE);
					log.info("[RECOVER] file= {}, off= {}, key= {}, ts=false, vlen= {}", sf.fileId(), pos, k, vlen);
				}

				pos = nextPos;
			}
		}
	}

	private static boolean readFully(FileChannel ch, ByteBuffer dst, long pos) throws IOException {
		long p = pos;
		while (dst.hasRemaining()) {
			int n = ch.read(dst, p);
			if (n < 0)
				return false;
			p += n;
		}
		return true;
	}
}
