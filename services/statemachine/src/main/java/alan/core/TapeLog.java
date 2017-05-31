package alan.core;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


/**
 *
 */
public interface TapeLog<T extends Tape> {
  public <R> CompletableFuture<R> execute(TapeCommand<R> command);
  public CompletableFuture<Boolean> execute(Collection<TapeCommand<?>> commands);

  public static interface Factory {
    public <T extends Tape> TapeLog<T> create(Schema<T> schema, ExecutorService service);
  }
}