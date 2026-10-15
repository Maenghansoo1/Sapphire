package com.sapphire.hawe.global.storage;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileCleanupTests {
    FileStorage storage = mock(FileStorage.class);
    FileCleanup cleanup = new FileCleanup(storage);

    @Test void deletesOnlyAfterCommitAndDeduplicatesKeys() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cleanup.deleteAfterCommit(Arrays.asList(null, "", "a", "a", "b", " "));
            verifyNoInteractions(storage);
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(storage, times(1)).delete("a");
            verify(storage, times(1)).delete("b");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void rollbackLeavesFilesUntouchedAndEmptyKeysRegisterNothing() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cleanup.deleteAfterCommit(Arrays.asList(null, " ", ""));
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            cleanup.deleteAfterCommit(List.of("keep"));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(storage);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void withoutSynchronizationDeletesImmediatelyAndContinuesOnFailure() {
        doThrow(new IllegalArgumentException("bad key")).when(storage).delete("bad");
        assertDoesNotThrow(() -> cleanup.deleteAfterCommit(List.of("bad", "good")));
        verify(storage).delete("bad");
        verify(storage).delete("good");
    }
}
