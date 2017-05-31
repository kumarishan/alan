package alan.core;

import java.util.concurrent.CompletableFuture;

public interface ExecutionLock {
  public ExecutionId getId();
  public CompletableFuture<Boolean> acquire();
  public CompletableFuture<Boolean> release();
  public boolean isLocked();
}