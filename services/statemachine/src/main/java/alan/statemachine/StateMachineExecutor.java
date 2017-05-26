package alan.statemachine;

import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alan.core.ExecutionId;
import alan.core.Machine;
import static alan.core.Machine.Response;
import static alan.core.Machine.Response.*;


/**
 *
 */
class ExecutionException extends Exception {
  public ExecutionException() {}
  public ExecutionException(String message) {
    super(message);
  }
}

/**
 * State Machine executor
 * @ThreadSafe
 */
public class StateMachineExecutor<S, SMC> {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private volatile int status = 0;
  private final LinkedBlockingQueue<ExecutionTask> taskQueue;

  private final StateMachineDef<S, SMC> stateMachineDef;
  private int eventRetries = 3;
  private int maxWaitForAction;

  // fork join pool for handling events, ie executing StateExecutionAction
  private final ForkJoinPool forkJoinPool;
  private final ScheduledThreadPoolExecutor scheduler;

  // executor service used by state machine internally if any
  protected final ExecutorService stateMachineExecutorService;

  private final ExecutionPersistance<S, SMC> persistance;

  private final ConcurrentMap<ExecutionId, ExecutionLock> locks;
  {
    locks = new ConcurrentHashMap<>();
  }

  /**
   * [StateMachineExecutor description]
   * @param  stateMachineDef  [description]
   * @param  parallelism      [description]
   * @param  failureThreshold [description]
   * @return                  [description]
   */
  public StateMachineExecutor(StateMachineDef<S, SMC> stateMachineDef, int parallelism, int failureThreshold, int maxWaitForAction) {
    if (parallelism <= 0 || parallelism > MAX_PARALLEL ||
        failureThreshold <= 0 || failureThreshold > MAX_FAILURE_THRESHOLD)
      throw new IllegalArgumentException();

    this.maxWaitForAction = maxWaitForAction;
    this.stateMachineDef = stateMachineDef;
    this.stateMachineExecutorService = stateMachineDef.createExecutorService();
    this.taskQueue = new LinkedBlockingQueue<>();
    this.forkJoinPool = new ForkJoinPool(parallelism * ASYNC_TASK_PER_EXECUTION,
                                        ForkJoinPool. defaultForkJoinWorkerThreadFactory,
                                        (t, e) -> {}, // TODO
                                        true); // true -> FIFO
    this.scheduler = new ScheduledThreadPoolExecutor(parallelism); // [TODO]
    this.status = ((-parallelism) & EC_MASK) | (((-failureThreshold) << FC_SHIFT) & FC_MASK);
    this.persistance = new InMemoryExecutionPersistance<>(stateMachineDef, this.forkJoinPool);
  }

  /**
   * [StateMachineExecutor description]
   * @param  stateMachineDef [description]
   * @return                 [description]
   */
  public StateMachineExecutor(StateMachineDef<S, SMC> stateMachineDef) {
    this(stateMachineDef, Math.min(MAX_PARALLEL, Runtime.getRuntime().availableProcessors()), 10, 60000);
  }

  /**
   * [getPersistance description]
   * @return [description]
   */
  public ExecutionPersistance<S, SMC> getPersistance() {
    return persistance;
  }

  /**
   * [getStateMachineId description]
   * @return [description]
   */
  public final String getStateMachineId() {
    return stateMachineDef.getId();
  }

  /**
   *
   */
  public final Set<Class<?>> getEventTypes() {
    return stateMachineDef.getEventTypes();
  }

  /**
   * [hasEvents description]
   * @return [description]
   */
  public final boolean hasEvents() {
    return !taskQueue.isEmpty();
  }

  /**
   * [receiveTask description]
   * @param  event [description]
   * @return       [description]
   */
  private final boolean receiveTask(ExecutionTask task) {
    return taskQueue.offer(task);
  }

  /**
   * [next description]
   * @return [description]
   */
  private final ExecutionTask next() {
    return taskQueue.poll();
  }

  /**
   * [notifyStateExecutionActionCompletion description]
   */
  private void notifyStateExecutionActionCompletion() {
    int s;
    do {} while (!U.compareAndSwapInt(this, STATUS_OFFSET, s = status, s - EC_UNIT));
  }

  /**
   * [watch description]
   * @param future [description]
   */
  private void watch(CompletableFuture<Response> future) {
    scheduler.schedule(new Runnable() {
      public void run() {
        if (!future.isDone()) {
          future.complete(Response.FAILED_ACTION_TIMEOUT);
        }
      }
    }, this.maxWaitForAction, TimeUnit.MILLISECONDS);
  }

  ///////////////////////////// Event Execution Methods ///////////////////////////////////////////

  /**
   * [newAction description]
   * @return [description]
   */
  private final StateExecutionAction<S, SMC> newAction() {
    return new StateExecutionAction<>(this);
  }

  /**
   * Receive an event if it's not Suspended or Terminated
   * if cannot receive then the executor is Suspended
   * @param  event [description]
   * @return       true if successfully scheduled the event
   *               false if either executor failed to schedule the event
   *               or is suspended or terminated
   */
  public final boolean receive(Object event) {
    ExecutionId id = stateMachineDef.getExecutionId(event);
    Machine machine = new StateMachine<S, SMC>(id, stateMachineDef, forkJoinPool); // [TODO] cached instance
    ExecutionTask task = new ExecutionTask(id, machine, event, eventRetries);
    return receive(task);
  }

  /**
   * Receives an execution task.
   * @param  task [description]
   * @return      false - if Suspended or Terminated, or some other reason
   *              true - if successfully scheduled
   */
  private final boolean receive(ExecutionTask task) {
    int s;
    if ((s = status) > 0 && (byte)(s >> FC_SHIFT) < 0) {
      if (receiveTask(task)) {
        tryAddStateExecution();
        return true;
      } else {
        do {} while ((byte)((s = status) >> FC_SHIFT) < 0 &&
          !U.compareAndSwapInt(this, STATUS_OFFSET, s, s + FC_UNIT));
        return false;
      }
    }
    return false;
  }

  /**
   * [tryAddStateExecution description]
   */
  private final void tryAddStateExecution() {
    int s;
    StateExecutionAction<S, SMC> action = newAction();
    while ((s = status) > 0 && (byte)(s >> FC_SHIFT) < 0 && (short)s < 0) {
      if (U.compareAndSwapInt(this, STATUS_OFFSET, s, s + EC_UNIT)) {
        tryRunExecution(action);
        break;
      }
    }
  }

  /**
   * [tryRerunExecution description]
   * @param action [description]
   */
  private final void tryRunExecution(StateExecutionAction<S, SMC> action) {
    int s;
    try {
      forkJoinPool.execute(action);
      return;
    } catch (RejectedExecutionException ex) { // only when the resource is exhausted
      try { // retry
        forkJoinPool.execute(action);
        return;
      } catch (RejectedExecutionException exr) {
        do {} while (!U.compareAndSwapInt(this, STATUS_OFFSET, s = status, s - EC_UNIT));
        do {} while ((byte)((s = status) >> FC_SHIFT) < 0 &&
          !U.compareAndSwapInt(this, STATUS_OFFSET, s, s + FC_UNIT));
      }
    }
  }

  /**
   * [getLock description]
   * @param  id [description]
   * @return    [description]
   */
  private final ExecutionLock getLock(ExecutionId id) {
    ExecutionLock lock = locks.get(id);
    if (lock == null) {
      lock = new InMemoryExecutionLock(id);
      ExecutionLock old = locks.putIfAbsent(id, lock);
      if (old != null) lock = old;
    }
    return lock;
  }

  ////////////////////////////// Status Methods ////////////////////////////////////////////

  /**
   * [isActive description]
   * @return [description]
   */
  public final boolean isSuspended() {
    return (byte)(status >> FC_SHIFT) == 0;
  }

  /**
   * [isActive description]
   * @return [description]
   */
  public final boolean isActive() {
    return !isSuspended();
  }

  /**
   * [isTerminated description]
   * @return [description]
   */
  public final boolean isTerminated() {
    return status < 0;
  }

  ///////////////////////// STATICS ///////////////////////////////

  private static final sun.misc.Unsafe U = Unsafe.instance;

  private static long STATUS_OFFSET;
  static {
    try {
      STATUS_OFFSET = Unsafe.instance.objectFieldOffset(StateMachineExecutor.class.getDeclaredField("status"));
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }

  /**
   * Bits and masks for Status variable
   *
   * Field status is int packed with:
   * TR: true if executor is terminating (1 bit)
   * unused (7 bits)
   * FC: number of failures minus target failure threshold before suspension (8 bits) (byte)
   * EC: number of parallel StateExecutionActions minus target parallelism (16 bits)
   *
   * (s = status) < 0 -> Terminated
   * (s = status) & SUMASK > 0 -> Suspended
   * Failure count = (byte)((s = status) >>> FC_SHIFT)
   * Execution count = (short)(s = status)
   *
   * incrementing counts
   * Failure count -> (s + FC_UNIT)
   * Execution count -> (s + EC_UNIT)
   */

  // bit positions for fields
  private static final int FC_SHIFT = 16;

  // bounds
  private static final int BMASK = 0x00ff; // byte bits
  private static final int SMASK = 0xffff;
  private static final int MAX_PARALLEL = 0x7fff;
  private static final int MAX_FAILURE_THRESHOLD = 0x7f;

  // masks
  private static final int EC_MASK  = SMASK;
  private static final int FC_MASK  = BMASK << FC_SHIFT;
  private static final int RESET_FC = ~FC_MASK;

  // units for incrementing decrementing
  private static final int FC_UNIT = 1 << FC_SHIFT;
  private static final int EC_UNIT = 1;

  private static final int ASYNC_TASK_PER_EXECUTION = 4;

  /**
   *
   */
  public static class ExecutionTask {
    public final ExecutionId id;
    public final Machine machine;
    public final Object event;
    public final int retries;

    public ExecutionTask(ExecutionId id, Machine machine, Object event, int retries) {
      this.id = id;
      this.machine = machine;
      this.event = event;
      this.retries = retries;
    }

    /**
     * [decrement description]
     * @return [description]
     */
    public ExecutionTask decrement() {
      return new ExecutionTask(id, machine, event, retries - 1);
    }
  }

  /**
   * IMP: StateExecutionAction is only created (if not limit) when a new event is received
   *      i.e. Executor doesnot maintain the count at the limit and therefore
   *      if no events are received for a long time, then eventually all StateExectionAction
   *      will complete. This happens when queue is empty
   *      Otherwise StateExecutionAction, will continue polling for tasks and executing
   *      And remain alive by re submitting itself to the forkJoinPool
   *
   * IMP: Executor service is LIFO(ayncMode=false) ForkJoinPool
   *
   * Algorithm:
   * - dequeue an event from the queue
   *   - block until new events arrive or timeout
   *   - retry with exponential backoff
   *   - on finish set stateMachineExecutor status to Idle
   * - if ! acquireLock(execution id), also retrieve the current execution stage step
   *   - enqueue(event) with retry++
   *   - new StateExecutionAction().fork()
   * - Future = get execution stage from the store (if no step increment .. cached execution stage)
   * - create a fork join task with the target action to run
   * - run the action
   * - create new execution stage
   * - insert execution stage in the store
   * - unlock the execution id id
   * - recursively call itself
   * - on exception
   *   - if retries available
   *     - increment retry counter and enqueue the message again
   * - Has internal statuses like Idle, Scheduled, Processing, Suspended, Terminated.
   * - A ForkJoinTask that gets a State object and the context and runs it by calling the action
   */
  protected static class StateExecutionAction<S, SMC> extends RecursiveAction {
    private final StateMachineExecutor<S, SMC> executor;
    private final ExecutionPersistance<S, SMC> persistance;
    private final StateMachineDef<S, SMC> stateMachineDef;
    private final ForkJoinPool es;

    Logger logger = LoggerFactory.getLogger(this.getClass());

    public StateExecutionAction(StateMachineExecutor<S, SMC> executor) {
      this.executor = executor;
      this.persistance = executor.getPersistance();
      this.stateMachineDef = executor.stateMachineDef;
      this.es = executor.forkJoinPool;
    }

    protected void compute() {
      ExecutionTask task = executor.next();
      if (task == null || task.retries == 0) {
        executor.notifyStateExecutionActionCompletion();
        return;
      }

      CompletableFuture<Response> runF = task.machine.run(task.event);

      executor.watch(runF);

      runF
        .exceptionally((exception) -> {
          exception.printStackTrace();
          return FAILED;
        })
        .thenAcceptAsync((response) -> {
          logger.debug("Task {} ended with {}", task.id, response);
          switch (response) {
            case SUCCESS: break; // TODO
            case RETRY_TASK:
              executor.receive(task.decrement());
              break;
            case FAILED_TO_PERSIST_NEXT_STAGE: break; // TODO
            case FAILED_INCONSISTENT_LOCK: break; // TODO
            case FAILED_ACTION_TIMEOUT: break;
            case FAILED_UNHANDLED_EVENT:
              // send to deadletter queue
              break;
            case FAILED_ALREADY_COMPLETE: break;
            case FAILED_TO_START: break;
            case FAILED_INVALID_TRANSITION: break;
            case FAILED: break;
          }

          executor.tryRunExecution(new StateExecutionAction<>(executor));
        }, es);
    }
  }

}