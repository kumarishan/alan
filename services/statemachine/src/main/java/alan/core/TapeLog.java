package alan.core;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;


/**
 *
 */
public interface TapeLog<T extends Tape> {
  public <R> CompletableFuture<R> execute(TapeCommand<R> command);
  public CompletableFuture<Boolean> execute(Collection<TapeCommand<?>> commands);
}