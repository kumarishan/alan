package markov.services.sm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 */
class UnhandledEventException extends Exception {
  public UnhandledEventException(String message) {
    super(message);
  }
}

/**
 *
 */
class InvalidStateTransitionException extends Exception {
  public InvalidStateTransitionException(String message) {
    super(message);
  }
}

/**
 * Describes the current stage of the StateMachine for the given execution id
 * - current state
 * - current context (state context, state machine context)
 * - previous state
 * - trigger eventType at previous state
 * - default state context factory
 */
class ExecutionStage<S, SC, SMC> {
  private final ExecutionId id;
  private final int step;
  private final StateMachineDef<S, SMC> stateMachineDef;
  private final S currentState;
  private final StateMachineDef.Context<S, SC, SMC> context;
  private final S previousState;
  private final Class<?> prevTriggerEventType;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  public ExecutionStage(ExecutionId id, int step, StateMachineDef<S, SMC> stateMachineDef,
                        S currentState, StateMachineDef.Context<S, SC, SMC> context,
                        S previousState, Class<?> prevTriggerEventType) {
    this.id = id;
    this.step = step;
    this.stateMachineDef = stateMachineDef;
    this.currentState = currentState;
    this.context = context;
    this.previousState = previousState;
    this.prevTriggerEventType = prevTriggerEventType;
  }

  public boolean isFailure() { return false; } // TODO ask StateMachineDef

  /**
   * [TODO]
   * - [IMP] return should never complete with Exception
   *         at worst it is the ExecutionStage representing user defined/defauly Failure stage.
   *
   * Transition cases
   * ----------------
   * (all linked to new execution stage ie step++)
   * - For the current state
   *   - update the result state context
   * - For the next state
   *   - If a context override is provided then
   *     - Override will be set in the next execution stage
   *     - doesnt matter first/repeat visit
   *   - Else
   *     - If db doesnot have next state's context
   *       - Use context factory to create initial context
   *       - update the next's context
   *     - Else
   *       - update as carry on context
   *
   * Steps
   * -----
   * - Get valid Transition for the current state and evnet;
   * - if none then send to deadletter queue
   *
   * @param  stateMachineDef [description]
   * @param  executorService [description]
   * @return                 [description]
   */
  public <E> CompletableFuture<ExecutionUpdate<S, SMC>> run(E event, ExecutorService executorService) {
    State.Transition<S, E, SC, SMC> transition = stateMachineDef.getTransition(event, context);
    if (transition != null) {
      if (!transition.isAsync()) { // sync action
        State.To<S, ?> to;
        try {
          to = transition.getAction().apply(event, context.copy(executorService));
        } catch (Throwable ex) { // unchecked exceptions
          to = stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, context, ex);
        }
        logger.debug("Transition To {}", to.getState());
        return createNextExecutionUpdate(to, event, executorService);
      } else { // async action
        return transition.getAsyncAction().apply(event, context.copy(executorService))
          .exceptionally((exception) ->
            stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, context, exception))
          .thenComposeAsync((to) ->
            createNextExecutionUpdate(to, event, executorService), executorService);
      }
    } else {
      return failedFuture(new UnhandledEventException("No transition found in the current state " + currentState
                                                      + " for event " + event.getClass().getName()));
    }
  }

  /**
   * [createNextExecutionUpdate description]
   * @param  to    [description]
   * @param  event [description]
   * @return       [description]
   */
  @SuppressWarnings("unchecked")
  private <E> CompletableFuture<ExecutionUpdate<S, SMC>> createNextExecutionUpdate(State.To<S, ?> to, E event, ExecutorService es) {
    if (to.getContextOverride() != null && !stateMachineDef.validateContextType(to.getState(), to.getContextOverride())) {
      return failedFuture(new InvalidStateTransitionException("Context type " + to.getContextOverride().getClass().getName()
                                   + " doesnot match the target state " + to.getState() + "'s context"));
    }

    ExecutionUpdate<S, SMC> update;
    if (to instanceof State.Stop) {
      State.Stop<S> stop = (State.Stop<S>) to;
      update = new StopExecutionUpdate<>(id, step + 1, stop.getException(), currentState, context.getStateContext(), context.getStateMachineContext(), event.getClass());
    } else if (stateMachineDef.isSuccessState(to.getState())) {
      return runSinkState(to, true, event.getClass(), es);
    } else if (stateMachineDef.isFailureState(to.getState())) {
      return runSinkState(to, false, event.getClass(), es);
    } else {
      if (to.getContextOverride() != null) {
        update = new StateExecutionUpdate<>(id, step + 1, to.getState(), to.getContextOverride(), currentState, context.getStateContext(), context.getStateMachineContext(), event.getClass());
      } else {
        update = new StateExecutionUpdate<>(id, step + 1, to.getState(), stateMachineDef.getStateContextFactory(to.getState()), currentState, context.getStateContext(), context.getStateMachineContext(), event.getClass());
      }
    }

    return CompletableFuture.completedFuture(update);
  }

  private CompletableFuture<ExecutionUpdate<S, SMC>> runSinkState(State.To<S, ?> to, boolean isSuccess, Class<?> eventType, ExecutorService es) {
    SinkState<S, SMC, Object>  sinkState = stateMachineDef.getSinkState(to.getState());
    if (!sinkState.isActionAsync()) {
      Object result = sinkState.getAction().apply(context.getStateMachineContext());
      return CompletableFuture.completedFuture(
        new SinkStateExecutionUpdate<>(id, step + 1, to.getState(), result, currentState, context.getStateContext(), context.getStateMachineContext(), eventType, isSuccess));
    } else {
      CompletableFuture<Object> resultF = sinkState.getAsyncAction().apply(context.getStateMachineContext(), es);
      return resultF.thenApplyAsync((result) ->
        new SinkStateExecutionUpdate<>(id, step + 1, to.getState(), result, currentState, context.getStateContext(), context.getStateMachineContext(), eventType, isSuccess), es);
    }
  }

  /**
   * [failedFuture description]
   * [Move to util]
   * @param  exception [description]
   * @return           [description]
   */
  private <T> CompletableFuture<T> failedFuture(Throwable exception) {
    CompletableFuture<T> eF = new CompletableFuture<>();
    eF.completeExceptionally(exception);
    return eF;
  }

  //////////////// STATICS ////////////////////////

  /**
   * [startFor description]
   * @param  id              [description]
   * @param  stateMachineDef [description]
   * @return                 [description]
   */
  public static <S, SMC> ExecutionStage<S, ?, SMC> startFor(ExecutionId id, StateMachineDef<S, SMC> stateMachineDef) {
    S state = stateMachineDef.getStartState();
    StateMachineDef.Context<S, ?, SMC> context = stateMachineDef.getStartContext();
    return new ExecutionStage<>(id, 1, stateMachineDef, state, context, null, null);
  }
}