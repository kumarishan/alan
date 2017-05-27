package alan.core;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;


/**
 * 
 */
public class TapeLog {
  public <R> CompletableFuture<R> execute(TapeCommand<R> command) {
    return CompletableFuture.completedFuture(null);
  }

  public CompletableFuture<Boolean> execute(Collection<TapeCommand<?>> commands) {
    return CompletableFuture.completedFuture(true);
  }
}