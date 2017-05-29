package alan.statemachine;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import alan.core.ExecutionId;
import alan.core.Machine;
import alan.core.TapeLog;
import alan.core.TapeCommand;
import alan.core.InMemoryTapeLog;
import alan.statemachine.Transition;

import static alan.core.Machine.Response;
import static alan.core.TapeCommand.*;
import static alan.core.Tape.ContextLabel;
import static alan.util.FutureUtil.completedF;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * State Machine
 *
 */
class StateMachine<S, SMC> implements Machine {
  private static final Logger LOG = LoggerFactory.getLogger(StateMachine.class);

  private final ExecutionId id;
  private final StateMachineDef<S, SMC> stateMachineDef;
  private final ExecutorService executor;
  private final TapeLog<StateMachineTape> tapeLog;

  /**
   * [StateMachine description]
   * @param  id              [description]
   * @param  stateMachineDef [description]
   * @param  executor        [description]
   * @return                 [description]
   */
  public StateMachine(ExecutionId id, StateMachineDef<S, SMC> stateMachineDef, TapeLog<StateMachineTape> tapeLog, ExecutorService executor) {
    this.id = id;
    this.stateMachineDef = stateMachineDef;
    this.executor = executor;
    this.tapeLog = tapeLog;
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
    return stateMachineDef.getName();
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
        return getRunUpdate(event);
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
  private <E> CompletableFuture<Response> getRunUpdate(E event) {
    return tapeLog.execute(Peek(id, StateMachineTape.class))
      .thenComposeAsync((tape) -> {
        if (tape != null && tape.isCompleted()) return completedF(Response.FAILED_ALREADY_COMPLETE);

        List<TapeCommand<?>> commands = new ArrayList<>();
        if (tape == null) {
          S state = stateMachineDef.getStartState();
          Object stateContext = stateMachineDef.getStartStateContext();
          SMC stateMachineContext = stateMachineDef.createStateMachineContext();
          tape = StateMachineTape.Start(id, 0,
                                        stateMachineDef.serializeStateMachineContext(stateMachineContext),
                                        stateMachineDef.nameFor(state),
                                        stateMachineDef.serializeStateContext(stateContext));
          commands.add(Push(id, tape));
          return runUpdate(1, state, stateContext, stateMachineContext, event, commands);
        } else {
          return runUpdate(tape, event, commands);
        }
      }, executor)
      .exceptionally((exception) -> {
        exception.printStackTrace();
        return Response.RETRY_TASK;
      });
  }

  /**
   * [runUpdate description]
   * @param  tape  [description]
   * @param  event [description]
   * @param  step  [description]
   * @return       [description]
   */
  private CompletableFuture<Response> runUpdate(StateMachineTape tape, Object event, List<TapeCommand<?>> commands) {
    S currentState = stateMachineDef.stateFor(tape.getCurrentState());
    return runUpdate(tape.step + 1,
                     currentState,
                     stateMachineDef.deserializeStateContext(currentState, tape.getCurrentStateContext()),
                     stateMachineDef.deserializeStateMachineContext(tape.stateMachineContext),
                     event, commands);
  }

  /**
   * [runUpdate description]
   * @param  currentState        [description]
   * @param  stateContext        [description]
   * @param  stateMachineContext [description]
   * @param  event               [description]
   * @return                     [description]
   */
  private CompletableFuture<Response> runUpdate(int step, S currentState, Object stateContext, SMC stateMachineContext, Object event, List<TapeCommand<?>> commands) {
    StateMachineActionContext<S, Object, SMC> transitionContext = new StateMachineActionContext<>(currentState, stateContext, stateMachineContext, stateMachineDef);
    Transition<S, Object, Object, SMC> transition = stateMachineDef.getTransition(event, transitionContext); //////
    if (transition != null) {
      if (!transition.isAsync()) {
        Transition.To<S, ?> to;
        try {
          to = transition.getAction().apply(event, transitionContext.copy(executor));
          if (!(to instanceof Transition.Stop) && to.contextOverride != null
              && stateMachineDef.validateContextType(to.state, to.contextOverride)) {
            return completedF(Response.FAILED_INVALID_TRANSITION); // capture this invalid transition... ???
          }
        } catch (Throwable ex) {
          to = stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, transitionContext, ex); // capture this exception handling... ???
        }
        return update(step, transitionContext.getStateMachineContext(), to, currentState, transitionContext.getStateContext(), event, commands);
      } else {
        return transition.getAsyncAction().apply(event, transitionContext.copy(executor))
          .exceptionally((exception) ->
            stateMachineDef.getRuntimeExceptionHandler().handle(currentState, event, transitionContext, exception))
          .thenComposeAsync((to) -> update(step, transitionContext.getStateMachineContext(), to, currentState, transitionContext.getStateContext(), event, commands), executor);
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
  private CompletableFuture<Response> update(int step, SMC stateMachineContext, Transition.To<S, ?> to, S prevState, Object prevStateContext, Object event, List<TapeCommand<?>> commands) {
    byte[] stateMachineContextBinary = stateMachineDef.serializeStateMachineContext(stateMachineContext);
    String prevStateStr = stateMachineDef.nameFor(prevState);
    byte[] prevStateContextBinary = stateMachineDef.serializeStateContext(prevStateContext);
    String prevTriggerEventType = event.getClass().getName();

    if (to instanceof Transition.Stop) {
      @SuppressWarnings("unchecked")
      Transition.Stop<S> stop = (Transition.Stop<S>) to;
      StateMachineTape tape = StateMachineTape.Stop(id, step, stateMachineContextBinary,
                                                    stop.exception.toString(),
                                                    prevStateStr, prevStateContextBinary, prevTriggerEventType);
      commands.add(Push(id, tape));
      return tapeLog.execute(commands)
                    .thenApplyAsync((success) -> {
                      if (success) return Response.SUCCESS;
                      else return Response.FAILED_TO_PERSIST;
                    }, executor);
    } else if (stateMachineDef.isSuccessState(to.state)) {
      return runSuccessState(to.state, stateMachineContext, event.getClass())
        .thenComposeAsync((result) -> {
          StateMachineTape tape = StateMachineTape.Success(id, step, stateMachineContextBinary,
                                                           stateMachineDef.nameFor(to.state), stateMachineDef.serializeSinkStateResult(result),
                                                           prevStateStr, prevStateContextBinary, prevTriggerEventType);
          commands.add(Push(id, tape));
          return tapeLog.execute(commands)
                        .thenApplyAsync((success) -> {
                          if (success) return Response.SUCCESS;
                          else return Response.FAILED_TO_PERSIST;
                        }, executor);
        }, executor);
    } else if (stateMachineDef.isFailureState(to.state)) {
      return runFailureState(to.state, stateMachineContext, event.getClass())
        .thenComposeAsync((result) -> {
          StateMachineTape tape = StateMachineTape.Failure(id, step, stateMachineContextBinary,
                                                           stateMachineDef.nameFor(to.state), stateMachineDef.serializeSinkStateResult(result),
                                                           prevStateStr, prevStateContextBinary, prevTriggerEventType);
          commands.add(Push(id, tape));
          return tapeLog.execute(commands)
                        .thenApplyAsync((success) -> {
                          if (success) return Response.SUCCESS;
                          else return Response.FAILED_TO_PERSIST;
                        }, executor);
        }, executor);
    } else {
      String toStateStr = stateMachineDef.nameFor(to.state);
      if (to.contextOverride != null) {
        byte[] contextOverride = stateMachineDef.serializeStateContext(to.contextOverride);
        StateMachineTape tape = StateMachineTape.Stage(id, step, stateMachineContextBinary,
                                                      toStateStr, contextOverride, ContextLabel.OVERRIDE,
                                                      prevStateStr, prevStateContextBinary, prevTriggerEventType);
        commands.add(Push(id, tape));
        return tapeLog.execute(commands)
                      .thenApplyAsync((success) -> {
                        if (success) return Response.SUCCESS;
                        else return Response.FAILED_TO_PERSIST_NEXT_STAGE;
                      }, executor);
      } else {
        return tapeLog.execute(GetStateContext(id, toStateStr))
                      .thenComposeAsync((stateContext) -> {
                        ContextLabel label = ContextLabel.CARRY;
                        if (stateContext == null) {
                          stateContext = stateMachineDef.serializeStateContext(
                            stateMachineDef.createStateContext(to.state));
                          label = ContextLabel.NEW;
                        }
                        StateMachineTape tape = StateMachineTape.Stage(id, step, stateMachineContextBinary,
                                                                       toStateStr, stateContext, label,
                                                                       prevStateStr, prevStateContextBinary, prevTriggerEventType);
                        commands.add(Push(id, tape));
                        return tapeLog.execute(commands)
                                      .thenApplyAsync((success) -> {
                                        if (success) return Response.SUCCESS;
                                        else return Response.FAILED_TO_PERSIST_NEXT_STAGE;
                                      }, executor);
                      }, executor);
      }
    }
  }

  /**
   * [runSuccessState description]
   * @param  state               [description]
   * @param  stateMachineContext [description]
   * @param  eventType           [description]
   * @return                     [description]
   */
  private CompletableFuture<Object> runSuccessState(S state, SMC stateMachineContext, Class<?> eventType) {
    SinkStateDef<S, SMC, Object> sinkState = stateMachineDef.getSinkState(state);
    if (!sinkState.isActionAsync()) {
      Object result = sinkState.getAction().apply(stateMachineContext); // i think provide event type to reach this success state....
      return completedF(result);
    } else {
      return sinkState.getAsyncAction().apply(stateMachineContext, executor);
    }
  }

  /**
   * [runSinkState description]
   * @param  state     [description]
   * @param  isSuccess [description]
   * @param  eventType [description]
   * @return           [description]
   */
  private CompletableFuture<Object> runFailureState(S state, SMC stateMachineContext, Class<?> eventType) {
    SinkStateDef<S, SMC, Object> sinkState = stateMachineDef.getSinkState(state);
    if (!sinkState.isActionAsync()) {
      Object result = sinkState.getAction().apply(stateMachineContext); // i think provide event type to reach this success state....
      return completedF(result);
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
}