package alan.core;

import java.util.concurrent.CompletableFuture;


/**
 * 
 */
public class TapeLog {
  public <R> CompletableFuture<R> execute(TapeCommand<R> command) {
    return CompletableFuture.completedFuture(null);
  }
}