package markov.services.sm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


interface ExecutionLock {
  public CompletableFuture<Boolean> acquire();
  public CompletableFuture<Boolean> release();
  public boolean isLocked();
}

/**
 * TODO
 */
class InMemoryExecutionLock implements ExecutionLock {
  private AtomicBoolean locked;
  private final ExecutionId id;

  public InMemoryExecutionLock(ExecutionId id) {
    this.id = id;
    this.locked = new AtomicBoolean(false);
  }

  /**
   * [acquire description]
   * @return [description]
   */
  public CompletableFuture<Boolean> acquire() {
    return CompletableFuture.completedFuture(locked.compareAndSet(false, true));
  }

  /**
   * [release description]
   * @return [description]
   */
  public CompletableFuture<Boolean> release() {
    return CompletableFuture.completedFuture(locked.compareAndSet(true, false));
  }

  /**
   * [isLocked description]
   * @return [description]
   */
  public boolean isLocked() {
    return locked.get() == true;
  }
}