package com.sapphire.hawe.global.storage;

import java.util.Collection;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class FileCleanup {
    private static final Logger log = LoggerFactory.getLogger(FileCleanup.class);
    private final FileStorage storage;

    public FileCleanup(FileStorage storage) {
        this.storage = storage;
    }

    public void deleteAfterCommit(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String key : keys) if (key != null && !key.isBlank()) unique.add(key);
        if (unique.isEmpty()) return;
        Runnable delete = () -> {
            for (String key : unique) {
                try { storage.delete(key); }
                catch (RuntimeException e) { log.warn("Could not delete file {}", key, e); }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { delete.run(); }
            });
        } else delete.run();
    }
}
