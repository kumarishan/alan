package alan.core;

import java.util.concurrent.CompletableFuture;


public abstract class Store {

  public abstract CompletableFuture<Boolean> push(ExecutionId id, Tape tape);
  public abstract CompletableFuture<Tape> peek(ExecutionId id);
  public abstract CompletableFuture<byte[]> getStateContext(ExecutionId id, String state);
  public abstract CompletableFuture<Boolean> acquireLock(ExecutionId id);
  public abstract CompletableFuture<Boolean> releaseLock(ExecutionId id);
}