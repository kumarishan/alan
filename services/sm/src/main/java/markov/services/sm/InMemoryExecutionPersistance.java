package markov.services.sm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.SortedMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import static markov.services.sm.ExecutionProgress.Status;

/**
 *
 */
public final class InMemoryExecutionPersistance<S, SMC> implements ExecutionPersistance<S, SMC> {
  private final StateMachineDef<S, SMC> stateMachineDef;

  private static class Progress {
    final int step;
    final String status;

    public Progress(int step, String status) {
      this.step = step;
      this.status = status;
    }
  }

  private static class Stage {
    final ExecutionId id;
    final int step;
    final String currentState;
    final String previousState;
    final String triggerEventType;

    public Stage(ExecutionId id, int step, String currentState, String previousState, String triggerEventType) {
      this.id = id;
      this.step = step;
      this.currentState = currentState;
      this.previousState = previousState;
      this.triggerEventType = triggerEventType;
    }
  }

  private static class StateContext {
    final ExecutionId id;
    final int step;
    final String state;
    final byte[] binary;
    final String label;

    public StateContext(ExecutionId id, int step, String state, byte[] binary, String label) {
      this.id = id;
      this.step = step;
      this.state = state;
      this.binary = binary;
      this.label = label;
    }
  }

  private static class SinkStateResult {
    final ExecutionId id;
    final int step;
    final String state;
    final byte[] result;
    final boolean isSuccess;
    final String exception;

    public SinkStateResult(ExecutionId id, int step, String state, byte[] result, String exception, boolean isSuccess) {
      this.id = id;
      this.step = step;
      this.state = state;
      this.result = result;
      this.exception = exception;
      this.isSuccess = isSuccess;
    }
  }

  private final ExecutorService executor;
  private final ConcurrentMap<ExecutionId, Progress> progresses;
  private final ConcurrentMap<ExecutionId, SinkStateResult> sinks;
  private final ConcurrentMap<ExecutionId, SortedMap<Integer, byte[]>> stateMachineContexts;
  private final ConcurrentMap<ExecutionId, SortedMap<Integer, Map<String, StateContext>>> stateContexts;
  private final ConcurrentMap<ExecutionId, SortedMap<Integer, Stage>> stages;
  {
    progresses = new ConcurrentHashMap<>();
    sinks = new ConcurrentHashMap<>();
    stateMachineContexts = new ConcurrentHashMap<>();
    stateContexts = new ConcurrentHashMap<>();
    stages = new ConcurrentHashMap<>();
  }

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
    ExecutionProgress res;
    if (progresses.containsKey(id)) {
      Progress progress = progresses.get(id);
      res = new ExecutionProgress(id, progress.step, Status.valueOf(progress.status));
    } else {
      res = new ExecutionProgress(id, 1, Status.NEW);
    }

    return CompletableFuture.completedFuture(res);
  }

  /**
   * TODO
   * @param  id [description]
   * @return    [description]
   */
  public CompletableFuture<ExecutionStage<S, ?, SMC>> getExecutionStage(ExecutionId id) {
    return getExecutionStage(id);
  }

  // wildcard
  private <SC> CompletableFuture<ExecutionStage<S, SC, SMC>> _getExecutionStage(ExecutionId id) {
    Progress progress = progresses.get(id);
    int step = progress.step;
    Stage stage = stages.get(id).get(step);
    StateContext currentContext = stateContexts.get(id).get(step).get(stage.currentState);

    S currentState = stateMachineDef.stateNameFor(stage.currentState);
    S previousState = stateMachineDef.stateNameFor(stage.previousState);

    byte[] smcBinary = stateMachineContexts.get(id).get(step);
    SC stateContext = stateMachineDef.deserializeStateContext(currentState, currentContext.binary);
    SMC stateMachineContext = stateMachineDef.deserializeStateMachineContext(smcBinary);
    StateMachineDef.Context<S, SC, SMC> context = new StateMachineDef.Context<>(currentState, stateContext, stateMachineContext, stateMachineDef);

    Class<?> prevTriggerEventType;
    try {
      prevTriggerEventType = Class.forName(stage.triggerEventType);
    } catch (ClassNotFoundException ex) {
      return CompletableFuture.completedFuture(null);
    }

    ExecutionStage<S, SC, SMC> res = new ExecutionStage<>(id, step, stateMachineDef, currentState, context, previousState, prevTriggerEventType);
    return CompletableFuture.completedFuture(res);
  }

  /**
   * [saveExecutionStage description]
   * @param  stage [description]
   * @return       [description]
   */
  public CompletableFuture<Boolean> updateExecution(ExecutionUpdate<S, SMC> update) {
    if (update instanceof NewExecutionUpdate)
      handleNewExecutionUpdate((NewExecutionUpdate<S, ?, SMC>) update);
    else if (update instanceof StateExecutionUpdate)
      handleStateExecutionUpdate((StateExecutionUpdate<S, ?, ?, SMC>) update);
    else if (update instanceof SinkStateExecutionUpdate)
      handleSinkStateExecutionUpdate((SinkStateExecutionUpdate<S, ?, ?, SMC>) update);
    else if (update instanceof StopExecutionUpdate)
      handleStopExecutionUpdate((StopExecutionUpdate<S, ?, SMC>) update);

    return CompletableFuture.completedFuture(true);
  }

  /**
   * [handleNewExecutionUpdate description]
   * @param  update [description]
   * @return        [description]
   */
  private <SC> void handleNewExecutionUpdate(NewExecutionUpdate<S, SC, SMC> update) {
    byte[] stateMachineContext = stateMachineDef.serializeStateMachineContext(update.stateMachineContext);

    String state = stateMachineDef.nameStrFor(update.startState);
    @SuppressWarnings("unchecked")
    byte[] binary = stateMachineDef.serializeStateContext((Class<SC>)update.startStateContext.getClass(), (SC)update.startStateContext);
    StateContext context = new StateContext(update.id, update.step, state, binary, "NEW");

    Stage stage = new Stage(update.id, update.step, state, null, null);

    Progress progress = new Progress(update.step, update.status.toString());

    updateProgress(update.id, progress);
    appendStateMachineContext(update.id, update.step, stateMachineContext);
    appendStateContext(update.id, update.step, state, context);
    appendStage(update.id, update.step, stage);
  }

  /**
   * [handleStateExecutionUpdate description]
   * @param  update [description]
   * @return        [description]
   */
  @SuppressWarnings("unchecked")
  private <SC, SR> void handleStateExecutionUpdate(StateExecutionUpdate<S, SC, SR, SMC> update) {
    byte[] stateMachineContext = stateMachineDef.serializeStateMachineContext(update.stateMachineContext);

    String nextState = stateMachineDef.nameStrFor(update.nextState);
    byte[] binary;
    String label;
    if (update.hasOverride()) {
      binary = stateMachineDef.serializeStateContext(update.nextState, update.overrideContext);
      label = "OVERRIDE";
    } else {
      StateContext context = getStateContext(update.id, update.step - 1, nextState);
      if (context == null) {
        SC sc = update.contextFactory.get();
        binary = stateMachineDef.serializeStateContext((Class<SC>)sc.getClass(), sc);
        label = "NEW";
      } else {
        binary = context.binary;
        label = "CARRY";
      }
    }
    StateContext nextContext = new StateContext(update.id, update.step, nextState, binary, label);

    String prevState = stateMachineDef.nameStrFor(update.prevState);
    binary = stateMachineDef.serializeStateContext((Class<SR>)update.resultContext.getClass(), (SR)update.resultContext);
    StateContext prevContext = new StateContext(update.id, update.step, prevState, binary, "RESULT");

    Stage stage = new Stage(update.id, update.step, nextState, prevState, update.eventType.getName());

    Progress progress = new Progress(update.step, update.status.toString());

    updateProgress(update.id, progress);
    appendStateMachineContext(update.id, update.step, stateMachineContext);
    appendStateContext(update.id, update.step, nextState, nextContext);
    appendStateContext(update.id, update.step, prevState, prevContext);
    appendStage(update.id, update.step, stage);
  }

  /**
   * [handleSinkStateExecutionUpdate description]
   * @param  update [description]
   * @return        [description]
   */
  @SuppressWarnings("unchecked")
  private <SR, SCR> void handleSinkStateExecutionUpdate(SinkStateExecutionUpdate<S, SR, SCR, SMC> update) {
    byte[] stateMachineContext = stateMachineDef.serializeStateMachineContext(update.stateMachineContext);

    String sinkState = stateMachineDef.nameStrFor(update.sinkState);
    byte[] binary = stateMachineDef.serializeStateContext((Class<SR>)update.sinkResult.getClass(), update.sinkResult);
    SinkStateResult sinkStateResult = new SinkStateResult(update.id, update.step, sinkState, binary, null, update.isSuccess());

    String prevState = stateMachineDef.nameStrFor(update.prevState);
    binary = stateMachineDef.serializeStateContext((Class<SCR>)update.resultContext.getClass(), update.resultContext);
    StateContext prevContext = new StateContext(update.id, update.step, prevState, binary, "RESULT");

    Stage stage = new Stage(update.id, update.step, sinkState, prevState, update.eventType.getName());

    Progress progress = new Progress(update.step, update.status.toString());

    updateProgress(update.id, progress);
    appendStateMachineContext(update.id, update.step, stateMachineContext);
    updateSinks(update.id, sinkStateResult);
    appendStage(update.id, update.step, stage);
  }

  /**
   * [handleStopExecutionUpdate description]
   * @param  update [description]
   * @return        [description]
   */
  private <SCR> void handleStopExecutionUpdate(StopExecutionUpdate<S, SCR, SMC> update) {
    byte[] stateMachineContext = stateMachineDef.serializeStateMachineContext(update.stateMachineContext);

    String prevState = stateMachineDef.nameStrFor(update.prevState);
    @SuppressWarnings("unchecked")
    byte[] binary = stateMachineDef.serializeStateContext((Class<SCR>)update.resultContext.getClass(), update.resultContext);
    StateContext prevContext = new StateContext(update.id, update.step, prevState, binary, "RESULT");

    String exception = update.exception.getClass().getName() + "::" + update.exception.getMessage();
    SinkStateResult sinkStateResult = new SinkStateResult(update.id, update.step, prevState, null, exception, false);

    Stage stage = new Stage(update.id, update.step, prevState, prevState, update.eventType.getName());
    Progress progress = new Progress(update.step, update.status.toString());

    updateProgress(update.id, progress);
    appendStateMachineContext(update.id, update.step, stateMachineContext);
    updateSinks(update.id, sinkStateResult);
    appendStage(update.id, update.step, stage);
  }

  /**
   * [updateProgress description]
   * @param id       [description]
   * @param progress [description]
   */
  private void updateProgress(ExecutionId id, Progress progress) {
    progresses.put(id, progress);
  }

  /**
   * [appendStateMachineContext description]
   * @param id      [description]
   * @param step    [description]
   * @param context [description]
   */
  private void appendStateMachineContext(ExecutionId id, int step, byte[] context) {
    SortedMap<Integer, byte[]> smc = stateMachineContexts.get(id);
    if (smc == null) {
      smc = new TreeMap<>();
      stateMachineContexts.put(id, smc);
    }

    smc.put(step, context);
  }

  /**
   * [appendStateContext description]
   * @param id      [description]
   * @param step    [description]
   * @param state   [description]
   * @param context [description]
   */
  private void appendStateContext(ExecutionId id, int step, String state, StateContext context) {
    SortedMap<Integer, Map<String, StateContext>> sc = stateContexts.get(id);
    if (sc == null) {
      sc = new TreeMap<>();
      stateContexts.put(id, sc);
    }

    Map<String, StateContext> st = sc.get(step);
    if (st == null) {
      st = new HashMap<>();
      sc.put(step, st);
    }

    st.put(state, context);
  }

  /**
   * [appendStage description]
   * @param id    [description]
   * @param step  [description]
   * @param stage [description]
   */
  private void appendStage(ExecutionId id, int step, Stage stage) {
    SortedMap<Integer, Stage> st = stages.get(id);
    if (st == null) {
      st = new TreeMap<>();
      stages.put(id, st);
    }

    st.put(step, stage);
  }

  /**
   * [updateSinks description]
   * @param id     [description]
   * @param result [description]
   */
  private void updateSinks(ExecutionId id, SinkStateResult result) {
    sinks.put(id, result);
  }

  /**
   * [getStateContext description]
   * @param  id    [description]
   * @param  step  [description]
   * @param  state [description]
   * @return       [description]
   */
  private StateContext getStateContext(ExecutionId id, int step, String state) {
    SortedMap<Integer, Map<String, StateContext>> sc = stateContexts.get(id);
    if (sc != null) {
      Map<String, StateContext> st = sc.get(step);
      if (st != null) {
        return st.get(state);
      }
    }
    return null;
  }

}