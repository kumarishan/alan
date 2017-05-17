package markov.services.sm;

import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;


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

  private volatile int status = 0;
  private int receiveFailures = 0; // @GaurdedBy("failureLock")
  private final Object failureLock = new Object();
  private final LinkedBlockingQueue<ExecutionTask> taskQueue;

  protected final StateMachineDef<S, SMC> stateMachineDef;
  private int eventRetries = 3;

  // fork join pool for handling events, ie executing StateExecutionAction
  protected final ForkJoinPool forkJoinPool;

  // executor service used by state machine internally if any
  protected final ExecutorService stateMachineExecutorService;

  protected AtomicInteger numProcessing = new AtomicInteger(0);


  /**
   * [StateMachineExecutor description]
   * @param  stateMachineDef  [description]
   * @param  parallelism      [description]
   * @param  failureThreshold [description]
   * @return                  [description]
   */
  public StateMachineExecutor(StateMachineDef<S, SMC> stateMachineDef, int parallelism, int failureThreshold) {
    if (parallelism <= 0 || parallelism > MAX_PARALLEL ||
        failureThreshold <= 0 || failureThreshold > MAX_FAILURE_THRESHOLD)
      throw new IllegalArgumentException();


    this.stateMachineDef = stateMachineDef;
    this.stateMachineExecutorService = stateMachineDef.createExecutorService();
    this.taskQueue = new LinkedBlockingQueue<>();
    this.forkJoinPool = new ForkJoinPool(parallelism * ASYNC_TASK_PER_EXECUTION,
                                        ForkJoinPool. defaultForkJoinWorkerThreadFactory,
                                        (t, e) -> {}, // TODO
                                        true); // true -> FIFO
    this.status = (-parallelism) | ((-failureThreshold) << FC_SHIFT);
  }

  /**
   * [StateMachineExecutor description]
   * @param  stateMachineDef [description]
   * @return                 [description]
   */
  public StateMachineExecutor(StateMachineDef<S, SMC> stateMachineDef) {
    this(stateMachineDef, Math.min(MAX_PARALLEL, Runtime.getRuntime().availableProcessors()), 10);
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
    do {} while (!U.compareAndSwapInt(this, STATUS_OFFSET, s = status, s & RESET_FC));
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
    ExecutionTask task = new ExecutionTask(event, id, eventRetries);
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
      if (!U.compareAndSwapInt(this, STATUS_OFFSET, s, s + EC_UNIT)) {
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
  private static final int MAX_PARALLEL = 0x7fff;
  private static final int MAX_FAILURE_THRESHOLD = 0x7f;

  // masks
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
    public final Object event;
    public final ExecutionId id;
    public final int retries;

    public ExecutionTask(Object event, ExecutionId id, int retries) {
      this.event = event;
      this.id = id;
      this.retries = retries;
    }

    public ExecutionTask decrement() {
      return new ExecutionTask(event, id, retries - 1);
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

    public StateExecutionAction(StateMachineExecutor<S, SMC> executor) {
      this.executor = executor;
    }

    protected void compute() {
      ExecutionTask task = executor.next();
      if (task == null) {
        executor.notifyStateExecutionActionCompletion();
        return;
      }
      // TODO
      // - acquire lock
      // - get execution stage
      // - run stage
      // - persist stage
      // - release lock
      // finally re run the exeution
      CompletableFuture.supplyAsync(() -> {
        executor.tryRunExecution(this);
        return true;
      });
    }
  }

}