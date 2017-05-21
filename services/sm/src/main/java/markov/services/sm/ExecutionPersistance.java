package markov.services.sm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static markov.services.sm.ExecutionProgress.Status;


/**
 *
 */
class ExecutionProgress {

  /**
   *
   */
  static enum Status {
    NEW, LIVE, FAILURE, SUCCESS;
  }

  private final ExecutionId id;
  private final int step;
  private final Status status;

  public ExecutionProgress(ExecutionId id, int step, Status status) {
    this.id = id;
    this.step = step;
    this.status = status;
  }

  /**
   * [getId description]
   * @return [description]
   */
  public ExecutionId getId() {
    return id;
  }

  /**
   * ]
   * @return [description]
   */
  public boolean isLive() {
    return status == Status.LIVE;
  }

  /**
   * [isNew description]
   * @return [description]
   */
  public boolean isNew() {
    return status == Status.NEW;
  }

  /**
   * [isComplete description]
   * @return [description]
   */
  public boolean isComplete() {
    return status == Status.SUCCESS || status == Status.FAILURE;
  }
}

/**
 *
 */
class ExecutionUpdate<S, SMC> {
  private final ExecutionId id;
  private final int step;
  private final SMC stateMachineContext;
  private final ExecutionProgress.Status status;

  public ExecutionUpdate(ExecutionId id, int step, SMC stateMachineContext, ExecutionProgress.Status status) {
    this.id = id;
    this.step = step;
    this.stateMachineContext = stateMachineContext;
    this.status = status;
  }

  public ExecutionId getId() { return id; }
  public int getStep() { return step; }
  public SMC getStateMachineContext() { return stateMachineContext; }
  public ExecutionProgress.Status getStatus() { return status; }
}

/**
 *
 */
class NewExecutionUpdate<S, SC, SMC> extends ExecutionUpdate<S, SMC> {
  private final S startState;
  private final SC startStateContext;

  public NewExecutionUpdate(ExecutionId id, S startState, SC startStateContext, SMC stateMachineContext) {
    super(id, 1, stateMachineContext, Status.NEW);
    this.startState = startState;
    this.startStateContext = startStateContext;
  }
}

/**
 *
 */
class StateExecutionUpdate<S, SC, SCR, SMC> extends ExecutionUpdate<S, SMC> {

  private final S nextState;
  private final SC overrideContext;
  private final Supplier<SC> contextFactory;

  private final S prevState;
  private final SCR resultContext;
  private final Class<?> eventType;

  /**
   * Constructor for intermediate states with override context
   * @param  id                  [description]
   * @param  step                [description]
   * @param  nextState           [description]
   * @param  overrideContext     [description]
   * @param  prevState           [description]
   * @param  resultContext       [description]
   * @param  stateMachineContext [description]
   * @param  eventType           [description]
   * @return                     [description]
   */
  public StateExecutionUpdate(ExecutionId id, int step, S nextState, SC overrideContext, S prevState, SCR resultContext, SMC stateMachineContext, Class<?> eventType) {
    super(id, step, stateMachineContext, Status.LIVE);

    this.nextState = nextState;
    this.overrideContext = overrideContext;
    this.contextFactory = null;

    this.prevState = prevState;
    this.resultContext = resultContext;
    this.eventType = eventType;
  }

  /**
   * Constructor for intermediate status with carry/new context
   * @param  id                  [description]
   * @param  step                [description]
   * @param  nextState           [description]
   * @param  contextFactory      [description]
   * @param  prevState           [description]
   * @param  resultContext       [description]
   * @param  stateMachineContext [description]
   * @param  eventType           [description]
   * @return                     [description]
   */
  public StateExecutionUpdate(ExecutionId id, int step, S nextState, Supplier<SC> contextFactory, S prevState, SCR resultContext, SMC stateMachineContext, Class<?> eventType) {
    super(id, step, stateMachineContext, Status.LIVE);

    this.nextState = nextState;
    this.overrideContext = null;
    this.contextFactory = contextFactory;

    this.prevState = prevState;
    this.resultContext = resultContext;
    this.eventType = eventType;
  }
}

/**
 *
 */
class SinkStateExecutionUpdate<S, SR, SCR, SMC> extends ExecutionUpdate<S, SMC> {

  private final S sinkState;
  private final SR sinkResult;

  private final S prevState;
  private final SCR resultContext;
  private final Class<?> eventType;

  /**
   * [SinkStateExecutionUpdate description]
   * @param  id                  [description]
   * @param  step                [description]
   * @param  sinkState           [description]
   * @param  sinkResult          [description]
   * @param  prevState           [description]
   * @param  resultContext       [description]
   * @param  stateMachineContext [description]
   * @param  eventType           [description]
   * @param  isSuccess           [description]
   * @return                     [description]
   */
  public SinkStateExecutionUpdate(ExecutionId id, int step, S sinkState, SR sinkResult, S prevState, SCR resultContext, SMC stateMachineContext, Class<?> eventType, boolean isSuccess) {
    super(id, step, stateMachineContext, getStatus(isSuccess));

    this.sinkState = sinkState;
    this.sinkResult = sinkResult;

    this.prevState = prevState;
    this.resultContext = resultContext;
    this.eventType = eventType;
  }

  private static ExecutionProgress.Status getStatus(boolean isSuccess) {
    if (isSuccess) return Status.SUCCESS;
    else return Status.FAILURE;
  }
}

/**
 *
 */
class StopExecutionUpdate<S, SCR, SMC> extends ExecutionUpdate<S, SMC> {

  private final Throwable exception;

  private final S prevState;
  private final SCR resultContext;
  private final Class<?> eventType;

  public StopExecutionUpdate(ExecutionId id, int step, Throwable exception, S prevState, SCR resultContext, SMC stateMachineContext, Class<?> eventType) {
    super(id, step, stateMachineContext, Status.FAILURE);

    this.exception = exception;

    this.prevState = prevState;
    this.resultContext = resultContext;
    this.eventType = eventType;
  }
}

/**
 * Sematics of state machine exection, from the data store (ds) perspective
 * -------------------------------------------------------------------
 * A state machine exection is identified by execution id
 * - Execution stage
 *   - execution id
 *   - step (ordered bys)
 *   - current state
 *   - context indirectly linked to current state's latest context
 *   - previous state
 *   - event at previous state that led to the current state
 * - Map (state -> state contexts)
 *   - contexts ordered by execution stage.step
 *   - if a state is never visited then its empty
 *   - when first visited, then either use factory or override and labeled [new/override]
 *   - after run of action, append the state's new context
 *   - any subsequent visit ot state will be updated with label [carry/override]
 *   - labels - new, override, result, carry
 * - List (step -> state machine context)
 *   - current state machine context
 *   - linked to step
 * - Execution Progress
 *   - execution id
 *   - current step
 *   - new/success/failure/live
 * - Sucesses
 *   - execution id
 *   - state
 *   - success result
 * - Failure
 *   - execution id
 *   - state / withException
 *   - failure result / Exception
 *
 * Flow
 * ----
 * - Initial
 *   - No entry for execution id implies execution has not begun
 *
 * - First event (provided execution id) (triggers the start of state machine execution)
 *   - insert execution stage
 *     - (id, step=1, start state, prev state null, prev event = null)
 *   - insert start state -> context (Map)
 *     - [new]
 *     - linked to step=1
 *   - insert state machine context
 *     - linked to step=1
 *   - @update progress (start state, 1, live)
 *   - @routine update (step=2, start state, next state, context/result, state machine context, event)
 *
 * - Subsequent event
 *   - nstep = ++step
 *   - @routine update (step=nstep, current state, next state, context/result event)
 *
 * - @define update sink state(step, next state, result)
 *   - update Success/Failure
 *   - linked to step=step
 *
 * - @define update progress (next state, step)
 *   - current step=step
 *   - If next state is sink state
 *     - then update success/failure
 *   - Else
 *     - live
 *
 * - @define routine update (step, current state, next state, context/result event)
 *   - @update progress(next state, step)
 *   - insert state machine context
 *     - linked to step=step
 *   - If next state isa sink state
 *     - @update sink state(step, next state, result)
 *   - Else
 *     - insert execution stage
 *       - (id, step=step, next state, prev state = current state, prev event = event)
 *     - insert current state -> context (Map)
 *       - [result]
 *       - linked to step=step
 *     - insert next state -> context (Map)
 *       - [override] if provided
 *       - [carry] if already
 *       - [new] otherwise
 *       - linked to step=step
 *     - insert state machine context
 *       - linked to step=step
 *
 */
interface ExecutionPersistance<S, SMC> {
  public CompletableFuture<ExecutionProgress> getExecutionProgress(ExecutionId id);
  public CompletableFuture<ExecutionStage<S, ?, SMC>> getExecutionStage(ExecutionId id);
  public CompletableFuture<Boolean> updateExecution(ExecutionUpdate<S, SMC> update);
}

/**
 *
 */
class InMemoryExecutionPersistance<S, SMC> implements ExecutionPersistance<S, SMC> {
  private final StateMachineDef<S, SMC> stateMachineDef;
  private final ExecutorService executor;

  public InMemoryExecutionPersistance(StateMachineDef<S, SMC> stateMachineDef, ExecutorService executor) {
    this.stateMachineDef = stateMachineDef;
    this.executor = executor;
  }

  /**
   * [getExecutionProgress description]
   * @param  id [description]
   * @return    [description]
   */
  public CompletableFuture<ExecutionProgress> getExecutionProgress(ExecutionId id) {
    return CompletableFuture.completedFuture(new ExecutionProgress(id, 1, ExecutionProgress.Status.LIVE));
  }

  /**
   * TODO
   * @param  id [description]
   * @return    [description]
   */
  public CompletableFuture<ExecutionStage<S, ?, SMC>> getExecutionStage(ExecutionId id) {
    return CompletableFuture.completedFuture(new ExecutionStage<>(id, 2, stateMachineDef, null, null, null, null));
  }

  /**
   * [saveExecutionStage description]
   * @param  stage [description]
   * @return       [description]
   */
  public CompletableFuture<Boolean> updateExecution(ExecutionUpdate<S, SMC> update) {
    return CompletableFuture.completedFuture(true);
  }

}