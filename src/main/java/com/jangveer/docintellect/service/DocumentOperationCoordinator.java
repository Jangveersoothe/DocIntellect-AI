package com.jangveer.docintellect.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class DocumentOperationCoordinator {

    private final ConcurrentHashMap<UUID, LockReference> locks = new ConcurrentHashMap<>();

    public void execute(UUID documentId, Runnable operation) {
        LockReference reference = locks.compute(documentId, (id, current) -> {
            LockReference lockReference = current == null ? new LockReference() : current;
            lockReference.references++;
            return lockReference;
        });

        reference.lock.lock();
        try {
            operation.run();
        } finally {
            reference.lock.unlock();
            locks.computeIfPresent(documentId, (id, current) -> {
                if (current != reference) {
                    return current;
                }
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    private static final class LockReference {

        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
