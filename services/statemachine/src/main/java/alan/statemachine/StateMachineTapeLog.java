package alan.statemachine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.Collection;

import alan.core.TapeLog;
import alan.core.TapeCommand;
import alan.core.Store;

import static alan.core.TapeCommand.*;


/**
 *
 */
public class StateMachineTapeLog implements TapeLog<StateMachineTape> {
  private final Store store;
  private final ExecutorService executor;

  public StateMachineTapeLog(Store store, ExecutorService executor) {
    this.store = store;
    this.executor = executor;
  }

  @SuppressWarnings("unchecked")
  public <R> CompletableFuture<R> execute(TapeCommand<R> command) {
    if (command instanceof Push) {
      Push push = (Push)command;
      return (CompletableFuture<R>)store.push(push.id, push.tape);
    } else if (command instanceof Peek) {
      Peek peek = (Peek)command;
      return (CompletableFuture<R>)store.peek(peek.id);
    } else if (command instanceof GetStateContext) {
      GetStateContext getStateContext = (GetStateContext)command;
      return (CompletableFuture<R>)store.getStateContext(getStateContext.id, getStateContext.state);
    } else if (command instanceof AcquireLock) {
      AcquireLock acquireLock = (AcquireLock)command;
      return (CompletableFuture<R>)store.acquireLock(acquireLock.id);
    } else if (command instanceof ReleaseLock) {
      ReleaseLock releaseLock = (ReleaseLock)command;
      return (CompletableFuture<R>)store.releaseLock(releaseLock.id);
    }

    // throw execption...
    return CompletableFuture.completedFuture(null);
  }

  public CompletableFuture<Boolean> execute(Collection<TapeCommand<?>> commands) {
    CompletableFuture<Boolean> result = completedF(true);
    for (TapeCommand<?> command : commands)
      result = result.thenComposeAsync((success) -> {
        if (success) return execute(command).thenApplyAsync((r) -> success);
        else return completedF(false);
      }, executor);
    return result;
  }

  private <T> CompletableFuture<T> completedF(T value) {
    return CompletableFuture.completedFuture(value);
  }
}