package markov.services.sm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


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
 * [TODO]
 * Describes the current stage of the StateMachine for given execution id
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
  private final StateMachineDef.Context<SC, SMC> context;
  private final S previousState;
  private final Class<?> prevTriggerEventType;

  public ExecutionStage(ExecutionId id, int step, StateMachineDef<S, SMC> stateMachineDef,
                        S currentState, StateMachineDef.Context<SC, SMC> context,
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
   * Steps
   * -----
   * - Get valid Transition for the current state and evnet;
   * - if none then send to deadletter queue
   *
   * @param  stateMachineDef [description]
   * @param  executorService [description]
   * @return                 [description]
   */
  public <E> CompletableFuture<ExecutionStage<S, ?, SMC>> run(E event, ExecutorService executorService) {
    State.Transition<S, E, SC, SMC> transition = stateMachineDef.getTransition(currentState, event, context);
    if (transition != null) {
      if (!transition.isAsync()) { // sync action
        State.To<S, ?> to;
        try {
          to = transition.getAction().apply(event, context.copy(executorService));
        } catch (Throwable ex) {
          to = stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, context, ex);
        }
        // check to intanceof State.Terminate<S>
        return createNextExecutionStage(to, event);
      } else { // async action
        return transition.getAsyncAction().apply(event, context.copy(executorService))
          .exceptionally((exception) ->
            stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, context, exception))
          .thenComposeAsync((to) -> createNextExecutionStage(to, event), executorService);
      }
    } else {
      return failedFuture(new UnhandledEventException("No transition found in the current state " + currentState
                                                      + " for event " + event.getClass().getName()));
    }
  }

  /**
   * [createNextExecutionStage description]
   * @param  to    [description]
   * @param  event [description]
   * @return       [description]
   */
  @SuppressWarnings("unchecked")
  private <E> CompletableFuture<ExecutionStage<S, ?, SMC>> createNextExecutionStage(State.To<S, ?> to, E event) {
    if (to.getContextOverride() != null && !stateMachineDef.validateContextType(to.getState(), to.getContextOverride())) {
      return failedFuture(new InvalidStateTransitionException("Context type " + to.getContextOverride().getClass().getName()
                                   + " doesnot match the target state " + to.getState() + "'s context"));
    }

    StateMachineDef.Context<SC, SMC> targetContext = null;

    if (to.getContextOverride() != null) {
      targetContext = new StateMachineDef.Context<SC, SMC>((SC) to.getContextOverride(), context.stateMachineContext);
    } else {
      targetContext = new StateMachineDef.Context<SC, SMC>(null, context.stateMachineContext);
    }
    return CompletableFuture.completedFuture(
      new ExecutionStage<>(id, step + 1, stateMachineDef, to.getState(), targetContext, currentState, event.getClass()));
  }

  /**
   * [failed description]
   * ????????
   * @return [description]
   */
  public ExecutionStage failed() {
    return this;
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
    StateMachineDef.Context<?, SMC> context = stateMachineDef.getStartContext();
    return new ExecutionStage<>(id, 1, stateMachineDef, state, context, null, null);
  }
}