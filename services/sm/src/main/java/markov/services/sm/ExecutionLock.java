package markov.services.sm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


interface ExecutionLock {
  public CompletableFuture<ExecutionLock> acquire();
  public CompletableFuture<ExecutionLock> release();
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
  public CompletableFuture<ExecutionLock> acquire() {
    locked.compareAndSet(false, true);
    return CompletableFuture.completedFuture(this);
  }

  /**
   * [release description]
   * @return [description]
   */
  public CompletableFuture<ExecutionLock> release() {
    locked.compareAndSet(true, false);
    return CompletableFuture.completedFuture(this);
  }

  /**
   * [isLocked description]
   * @return [description]
   */
  public boolean isLocked() {
    return locked.get() == true;
  }
}