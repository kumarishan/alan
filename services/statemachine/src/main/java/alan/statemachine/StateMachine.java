package alan.statemachine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import alan.core.ExecutionId;
import alan.core.Machine;
import alan.core.TapeLog;

import static alan.core.Machine.Response;
import static alan.statemachine.State.Transition;
import static alan.core.TapeCommand.*;
import static alan.statemachine.StateMachinePeek.StateMachinePeek;


/**
 *
 */
class StateMachinePeek extends Peek<StateMachineTape> {
  public StateMachinePeek(ExecutionId id, int step) {
    super(id, step);
  }

  public static StateMachinePeek StateMachinePeek(ExecutionId id, int step) {
    return new StateMachinePeek(id, step);
  }
}

/**
 * State Machine
 *
 */
class StateMachine<S, SMC> implements Machine {

  private final ExecutionId id;
  private final StateMachineDef<S, SMC> stateMachineDef;
  private final ExecutorService executor;
  private final TapeLog tapeLog = new TapeLog(); // TODO

  /**
   * [StateMachine description]
   * @param  id              [description]
   * @param  stateMachineDef [description]
   * @param  executor        [description]
   * @return                 [description]
   */
  public StateMachine(ExecutionId id, StateMachineDef<S, SMC> stateMachineDef, ExecutorService executor) {
    this.id = id;
    this.stateMachineDef = stateMachineDef;
    this.executor = executor;
  }

  /**
   * [getExecutionId description]
   * @return [description]
   */
  public ExecutionId getExecutionId() {
    return id;
  }

  /**
   * [getName description]
   * @return [description]
   */
  public String getName() {
    return stateMachineDef.getId();
  }

  /**
   * [run description]
   * @param  event [description]
   * @return       [description]
   */
  public <E> CompletableFuture<Response> run(E event) {
    return tapeLog.execute(AcquireLock(id))
      .thenComposeAsync((acquired) -> {
        if (!acquired) return completedF(Response.FAILED_LOCK_ALREADY_HELD);
        return tapeLog.execute(GetStatus(id))
                  .thenComposeAsync((status) -> {
                    if (status.isNew()) {
                      // create a new state...
                      // accumulate updates in a list
                      // and execute colletively
                      return tapeLog.execute(DiffPush(id)) // context from state machine def
                            .thenComposeAsync((success) -> {
                              if (!success) return CompletableFuture.completedFuture(Response.FAILED_TO_START);
                              else return getRunUpdate(event, 2);
                            });
                    } else if (status.isLive()) {
                      return getRunUpdate(event, status.step + 1);
                    } else {
                      return completedF(Response.FAILED_ALREADY_COMPLETE);
                    }
          }, executor);
      }, executor)
      .thenComposeAsync((response) -> {
        if (response != Response.FAILED_LOCK_ALREADY_HELD) {
          return tapeLog.execute(ReleaseLock(id))
                  .thenApplyAsync((released) -> {
                    if (released) return response;
                    else return Response.FAILED_INCONSISTENT_LOCK;
                  }, executor);
        } else {
          return completedF(response);
        }
      }, executor);
  }

  /**
   * [getRunUpdate description]
   * @param  event [description]
   * @param  step  [description]
   * @return       [description]
   */
  private <E> CompletableFuture<Response> getRunUpdate(E event, int step) {
    return tapeLog.execute(StateMachinePeek(id, step)) // StateMachinePeek implements TapeCommand<StateMachineTape>
      .thenComposeAsync((tape) -> {
        if (tape != null) return runUpdate(tape, event, step);
        else return completedF(Response.RETRY_TASK);
      });
  }

  /**
   * [runUpdate description]
   * @param  tape  [description]
   * @param  event [description]
   * @param  step  [description]
   * @return       [description]
   */
  private <E> CompletableFuture<Response> runUpdate(StateMachineTape tape, E event, int step) {
    S currentState = stateMachineDef.stateNameFor(tape.currentState);
    Object stateContext = stateMachineDef.deserializeStateContext(currentState, tape.stateContext);
    SMC stateMachineContext = stateMachineDef.deserializeStateMachineContext(tape.stateMachineContext);

    StateMachineDef.Context<S, Object, SMC> context = new StateMachineDef.Context<>(currentState, stateContext, stateMachineContext, stateMachineDef);

    Transition<S, E, Object, SMC> transition = stateMachineDef.getTransition(event, context);
    if (transition != null) {
      if (!transition.isAsync()) {
        State.To<S, ?> to;
        try {
          to = transition.getAction().apply(event, context.copy(executor));
        } catch (Throwable ex) {
          to = stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, context, ex);
        }
        return update(step, currentState, to, event, context);
      } else {
        return transition.getAsyncAction().apply(event, context.copy(executor))
          .exceptionally((exception) ->
            stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, context, exception))
          .thenComposeAsync((to) -> update(step, currentState, to, event, context), executor);
      }
    } else {
      return completedF(Response.FAILED_UNHANDLED_EVENT);
    }
  }

  /**
   * [update description]
   * @param  step      [description]
   * @param  prevState [description]
   * @param  to        [description]
   * @param  event     [description]
   * @param  context   [description]
   * @return           [description]
   */
  private <E> CompletableFuture<Response> update(int step, S prevState, State.To<S, ?> to, E event, StateMachineDef.Context<S, ?, SMC> context) {
    if (to.contextOverride != null && stateMachineDef.validateContextType(to.state, to.contextOverride)) {
      return completedF(Response.FAILED_INVALID_TRANSITION);
    }

    String prevStateStr = stateMachineDef.nameStrFor(prevState);
    byte[] stateMachineContextBinary = stateMachineDef.serializeStateMachineContext(context.getStateMachineContext());
    byte[] prevStateContextBinary = stateMachineDef.serializeStateContext(context.getStateContext().getClass(), context.getStateContext());
    String prevTriggerEventType = event.getClass().getName();

    StateMachineTape tape;
    if (to instanceof State.Stop) {
      @SuppressWarnings("unchecked")
      State.Stop<S> stop = (State.Stop<S>) to;
      tape = new StateMachineTape(id, step, stateMachineContextBinary, stop.exception, prevStateStr, prevStateContextBinary, prevTriggerEventType);
    } else if (stateMachineDef.isSuccessState(to.state)) {
      return runSinkState(to.state, context.getStateMachineContext(), true, event.getClass())
        .thenComposeAsync((result) -> {
          byte[] resultBinary = stateMachineDef.serializeSinkStateResult(result.getClass(), result);
          StateMachineTape t = new StateMachineTape(id, step, stateMachineContextBinary, stateMachineDef.nameStrFor(to.state), resultBinary, prevStateStr, prevStateContextBinary, prevTriggerEventType);
          return tapeLog.execute(DiffPush(id))
                    .thenApplyAsync((success) -> {
                      if (success) return Response.SUCCESS;
                      else return Response.FAILED_TO_PERSIST_NEXT_STAGE;
                    }, executor); // [TODO]
        }, executor);
    } else {
      if (to.contextOverride != null) {
        Object contextOverride = to.contextOverride;
        byte[] contextOverrideBinary = stateMachineDef.serializeStateContext(contextOverride.getClass(), contextOverride);
        tape = new StateMachineTape(id, step, stateMachineContextBinary, stateMachineDef.nameStrFor(to.state), contextOverrideBinary, prevStateStr, prevStateContextBinary, prevTriggerEventType);
      } else {
        tape = new StateMachineTape(id, step, stateMachineContextBinary, stateMachineDef.nameStrFor(to.state), null, prevStateStr, prevStateContextBinary, prevTriggerEventType);
      }
    }

    return completedF(Response.SUCCESS);
  }

  /**
   * [runSinkState description]
   * @param  state     [description]
   * @param  isSuccess [description]
   * @param  eventType [description]
   * @return           [description]
   */
  private CompletableFuture<Object> runSinkState(S state, SMC stateMachineContext, boolean isSuccess, Class<?> eventType) {
    SinkState<S, SMC, Object> sinkState = stateMachineDef.getSinkState(state);
    if (!sinkState.isActionAsync()) {
      Object result = sinkState.getAction().apply(stateMachineContext);
      return CompletableFuture.completedFuture(result);
    } else {
      return sinkState.getAsyncAction().apply(stateMachineContext, executor);
    }
  }

  /**
   * [hashCode description]
   * @return [description]
   */
  public int hashCode() {
    int hash = id.hashCode();
    hash = hash * 31 + getName().hashCode();
    return hash;
  }

  /**
   * [equals description]
   * @param  other [description]
   * @return       [description]
   */
  public boolean equals(Object other) {
    if (!(other instanceof StateMachine)) return false;
    else {
      @SuppressWarnings("unchecked")
      StateMachine<S, SMC> o = (StateMachine<S, SMC>) other;
      return this.id.equals(o.id) && getName().equals(o.getName());
    }
  }

  // to utils
  private <T> CompletableFuture<T> completedF(T res) {
    return CompletableFuture.completedFuture(res);
  }

}