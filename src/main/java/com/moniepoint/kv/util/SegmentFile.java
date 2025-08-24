package com.moniepoint.kv.util;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moniepoint.kv.model.Crc32s;

final class SegmentFile implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(SegmentFile.class);
	private static final byte FLAG_TOMBSTONE = 0x1;

	private final int fileId;
	private final Path path;
	private final FileChannel ch;
	private long writePos;

	SegmentFile(int fileId, Path path, OpenOption... opts) throws IOException {
		this.fileId = fileId;
		this.path = path;
		this.ch = FileChannel.open(path, opts);
		this.writePos = ch.size(); // append at EOF
		log.debug("[SEG.open] fileId={} path={}", fileId, path);
	}

	int fileId() {
		return fileId;
	}

	Path path() {
		return path;
	}

	/**
	 * Append record with CRC32(key||value).
	 * 
	 * @return offset at which header begins
	 */
	synchronized long append(byte[] key, byte[] value, boolean tombstone) throws IOException {
		int keyLen = key.length;
		int valLen = value.length;
		byte flags = tombstone ? FLAG_TOMBSTONE : 0;
		int crc32 = Crc32s.of(key, value);

		// header: 4 + 4 + 1 + 4 = 13 bytes
		ByteBuffer hdr = ByteBuffer.allocate(13).order(LITTLE_ENDIAN);
		hdr.putInt(keyLen);
		hdr.putInt(valLen);
		hdr.put(flags);
		hdr.putInt(crc32);
		hdr.flip();

		long off = writePos;

		// write header
		while (hdr.hasRemaining()) {
			writePos += ch.write(hdr, writePos);
		}
		// write key
		ByteBuffer kb = ByteBuffer.wrap(key);
		while (kb.hasRemaining()) {
			writePos += ch.write(kb, writePos);
		}
		// write value
		ByteBuffer vb = ByteBuffer.wrap(value);
		while (vb.hasRemaining()) {
			writePos += ch.write(vb, writePos);
		}

		if (log.isDebugEnabled()) {
			log.debug("[SEG.append] fileId={} off={} keyLen={} valLen={} tombstone={}", this.fileId, off, key.length,
					value.length, tombstone);
		}
		return off; // start of header
	}

	/**
	 * Read only the value at a record offset (start of header).
	 */
	synchronized byte[] readValueAt(long offset) throws IOException {
		// read header
		ByteBuffer hdr = ByteBuffer.allocate(13).order(LITTLE_ENDIAN);
		readFully(ch, hdr, offset);
		hdr.flip();

		int keyLen = hdr.getInt();
		int valLen = hdr.getInt();
		byte flags = hdr.get();
		/* int crc = */ hdr.getInt(); // not used on random read

		long keyPos = offset + 13;
		long valPos = keyPos + keyLen;

		if ((flags & FLAG_TOMBSTONE) != 0)
			return null;

		ByteBuffer vb = ByteBuffer.allocate(valLen);
		readFully(ch, vb, valPos);
		vb.flip();
		byte[] value = new byte[valLen];
		vb.get(value);
		return value;
	}

	static void readFully(FileChannel ch, ByteBuffer dst, long pos) throws IOException {
		long p = pos;
		while (dst.hasRemaining()) {
			int n = ch.read(dst, p);
			if (n < 0)
				throw new EOFException("EOF");
			p += n;
		}
	}

	synchronized void force() throws IOException {
		try {
			ch.force(true);
			if (log.isTraceEnabled())
				log.trace("[SEG.fsync] fileId={}", this.fileId);
		} catch (IOException ioe) {
			log.error("[SEG.fsync] failed fileId={}", this.fileId, ioe);
			throw new RuntimeException(ioe); // keep behavior similar
		}
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			ch.force(true);
			ch.close();
			log.debug("[SEG.close] fileId={} closed", this.fileId);
		} catch (IOException ioe) {
			log.error("[SEG.close] failed fileId={}", this.fileId, ioe);
			throw ioe;
		}
	}
}
