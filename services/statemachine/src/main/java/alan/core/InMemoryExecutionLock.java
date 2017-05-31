package alan.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TODO
 */
public class InMemoryExecutionLock implements ExecutionLock {
  private AtomicBoolean locked;
  private final ExecutionId id;

  public InMemoryExecutionLock(ExecutionId id) {
    this.id = id;
    this.locked = new AtomicBoolean(false);
  }

  /**
   * [getId description]
   * @return [description]
   */
  public ExecutionId getId() {
    return id;
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