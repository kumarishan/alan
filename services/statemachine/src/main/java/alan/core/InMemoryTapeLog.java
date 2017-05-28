package alan.core;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Comparator;

import alan.core.TapeLog;
import alan.core.Schema;
import alan.core.ExecutionId;
import alan.core.Tape;
import alan.core.ExecutionLock;
import alan.core.InMemoryExecutionLock;

import static alan.util.FutureUtil.completedF;
import static alan.core.TapeCommand.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 */
public class InMemoryTapeLog<T extends Tape> implements TapeLog<T> {
  private static Logger LOG = LoggerFactory.getLogger(InMemoryTapeLog.class);

  private final ExecutorService executor;
  private final Schema<T> schema;
  private final ConcurrentMap<ExecutionId, ExecutionLock> locks; // [TODO] cache
  private final ConcurrentMap<ExecutionId, TreeMap<Integer, Schema.Tape>> tapes;
  private final ConcurrentMap<ExecutionId, Map<String, TreeMap<Integer, byte[]>>> stateContexts;
  {
    locks = new ConcurrentHashMap<>();
    tapes = new ConcurrentHashMap<>();
    stateContexts = new ConcurrentHashMap<>();
  }

  public InMemoryTapeLog(Schema<T> schema, ExecutorService executor) {
    this.schema = schema;
    this.executor = executor;
  }

  /**
   * [execute description]
   * @param  command [description]
   * @return         [description]
   */
  @SuppressWarnings("unchecked")
  public <R> CompletableFuture<R> execute(TapeCommand<R> command) {
    if (command instanceof Push) {
      Push push = (Push)command;
      return (CompletableFuture<R>)push(push.id, (T)push.tape);
    } else if (command instanceof Peek) {
      Peek<T> peek = (Peek<T>)command;
      return (CompletableFuture<R>)peek(peek.id);
    } else if (command instanceof GetStateContext) {
      GetStateContext getStateContext = (GetStateContext)command;
      return (CompletableFuture<R>)getStateContext(getStateContext.id, getStateContext.state);
    } else if (command instanceof AcquireLock) {
      AcquireLock acquireLock = (AcquireLock)command;
      return (CompletableFuture<R>)acquireLock(acquireLock.id);
    } else if (command instanceof ReleaseLock) {
      ReleaseLock releaseLock = (ReleaseLock)command;
      return (CompletableFuture<R>)releaseLock(releaseLock.id);
    }
    return completedF(null);
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

  /**
   * [push description]
   * @param  id   [description]
   * @param  tape [description]
   * @return      [description]
   */
  private CompletableFuture<Boolean> push(ExecutionId id, T tape) {
    Schema.Tape row = schema.toSchemaTape(tape);
    TreeMap<Integer, Schema.Tape> log = tapes.get(id);
    if (log == null) {
      log = new TreeMap<>(Comparator.reverseOrder());
      tapes.put(id, log);
    }
    log.put(row.step, row);

    Set<Schema.StateContext> contexts = schema.getSchemaStateContext(tape);
    Map<String, TreeMap<Integer, byte[]>> sc = stateContexts.get(id);
    if (sc == null) {
      sc = new HashMap<>();
      stateContexts.put(id, sc);
    }

    for (Schema.StateContext context : contexts) {
      TreeMap<Integer, byte[]> cs = sc.get(context.state);
      if (cs == null) {
        cs = new TreeMap<>(Comparator.reverseOrder());
        sc.put(context.state, cs);
      }
      cs.put(context.step, context.context);
    }
    return completedF(true);
  }

  /**
   * [peek description]
   * @param  id [description]
   * @return    [description]
   */
  private CompletableFuture<T> peek(ExecutionId id) {
    TreeMap<Integer, Schema.Tape> log = tapes.get(id);
    if (log == null) return completedF(null);
    else return completedF((T)schema.tapeFromSchemaTape(log.firstEntry().getValue()));
  }

  /**
   *
   */
  private CompletableFuture<byte[]> getStateContext(ExecutionId id, String state) {
    Map<String, TreeMap<Integer, byte[]>> contexts = stateContexts.get(id);
    if (contexts == null) return completedF(null);

    TreeMap<Integer, byte[]> steps = contexts.get(state);
    if (steps == null) return completedF(null);

    return completedF(steps.firstEntry().getValue());
  }

  /**
   * [acquireLock description]
   * @param  id [description]
   * @return    [description]
   */
  private CompletableFuture<Boolean> acquireLock(ExecutionId id) {
    ExecutionLock lock = locks.get(id);
    if (lock == null) {
      lock = new InMemoryExecutionLock(id);
      ExecutionLock old = locks.putIfAbsent(id, lock);
      if (old != null) lock = old;
    }
    return lock.acquire();
  }

  private CompletableFuture<Boolean> releaseLock(ExecutionId id) {
    ExecutionLock lock = locks.get(id);
    if (lock == null) return completedF(true);
    return lock.release();
  }
}