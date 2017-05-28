package alan.store;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Comparator;

import alan.core.Store;
import alan.core.Schema;
import alan.core.ExecutionId;
import alan.core.Tape;
import alan.core.ExecutionLock;
import alan.core.InMemoryExecutionLock;


public class InMemoryStore extends Store {
  private final Schema schema;
  private final ConcurrentMap<ExecutionId, ExecutionLock> locks; // [TODO] cache
  private final ConcurrentMap<ExecutionId, TreeMap<Integer, Schema.Tape>> tapes;
  private final ConcurrentMap<ExecutionId, Map<String, TreeMap<Integer, byte[]>>> stateContexts;
  {
    locks = new ConcurrentHashMap<>();
    tapes = new ConcurrentHashMap<>();
    stateContexts = new ConcurrentHashMap<>();
  }

  public InMemoryStore(Schema schema) {
    this.schema = schema;
  }

  /**
   * [push description]
   * @param  id   [description]
   * @param  tape [description]
   * @return      [description]
   */
  public CompletableFuture<Boolean> push(ExecutionId id, Tape tape) {
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
  public CompletableFuture<Tape> peek(ExecutionId id) {
    TreeMap<Integer, Schema.Tape> log = tapes.get(id);
    if (log == null) return completedF(null);
    else return completedF(schema.tapeFromSchemaTape(log.firstEntry().getValue()));
  }

  /**
   *
   */
  public CompletableFuture<byte[]> getStateContext(ExecutionId id, String state) {
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
  public CompletableFuture<Boolean> acquireLock(ExecutionId id) {
    ExecutionLock lock = locks.get(id);
    if (lock == null) {
      lock = new InMemoryExecutionLock(id);
      ExecutionLock old = locks.putIfAbsent(id, lock);
      if (old != null) lock = old;
    }
    return lock.acquire();
  }

  public CompletableFuture<Boolean> releaseLock(ExecutionId id) {
    ExecutionLock lock = locks.get(id);
    if (lock == null) return completedF(true);
    return lock.release();
  }

  /**
   * [completedF description]
   * @param  value [description]
   * @return       [description]
   */
  private <T> CompletableFuture<T> completedF(T value) {
    return CompletableFuture.completedFuture(value);
  }

}