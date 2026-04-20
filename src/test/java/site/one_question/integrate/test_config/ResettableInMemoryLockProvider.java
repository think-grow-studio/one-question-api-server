package site.one_question.integrate.test_config;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.inmemory.InMemoryLockProvider;

public class ResettableInMemoryLockProvider implements LockProvider {

    private final AtomicReference<InMemoryLockProvider> delegate =
        new AtomicReference<>(new InMemoryLockProvider());

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        return delegate.get().lock(lockConfiguration);
    }

    public void reset() {
        delegate.set(new InMemoryLockProvider());
    }
}
