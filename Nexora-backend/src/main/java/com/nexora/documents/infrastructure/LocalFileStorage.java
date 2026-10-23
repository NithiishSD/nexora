package com.nexora.documents.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nexora.documents.application.FileStorage;

// Files under one root directory (nexora.documents.storage-root), one file per generated key. A key that would
// leave the root is refused, and an existing file is never overwritten (CREATE_NEW).
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path root;

    public LocalFileStorage(@Value("${nexora.documents.storage-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) {
        try {
            Files.createDirectories(root);
            Files.write(resolve(key), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Document storage is unavailable.", e);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Document storage is unavailable.", e);
        }
    }

    @Override
    public void discard(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("event=document_discard_failed key={} error={}", key, e.getMessage());
        }
    }

    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.getParent().equals(root)) {
            throw new IllegalArgumentException("Storage key outside the storage root: " + key);
        }
        return file;
    }
}
