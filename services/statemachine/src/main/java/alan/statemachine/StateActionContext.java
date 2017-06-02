package alan.statemachine;

import java.util.concurrent.Executor;


/**
 * [RENAME]
 */
public final class StateActionContext<S, SC, SMC> {
  private final S state;
  private SC stateContext;
  private SMC stateMachineContext;
  private final Executor executor;
  private final StateMachineDef<S, SMC> stateMachineDef;

  StateActionContext(S state, SC stateContext, SMC stateMachineContext, Executor executor, StateMachineDef<S, SMC> stateMachineDef) {
    this.state = state;
    this.stateContext = stateContext;
    this.stateMachineContext = stateMachineContext;
    this.executor = executor;
    this.stateMachineDef = stateMachineDef;
  }

  StateActionContext(S state, SC stateContext, SMC stateMachineContext, StateMachineDef<S, SMC> stateMachineDef) {
    this(state, stateContext, stateMachineContext, null, stateMachineDef);
  }

  /**
   * [getState description]
   * @return [description]
   */
  public S getState() {
    return state;
  }

  /**
   * [getStateContext description]
   * @return [description]
   */
  public SC getStateContext() {
    return stateContext;
  }

  /**
   * [getStateMachineContext description]
   * @return [description]
   */
  public SMC getStateMachineContext() {
    return stateMachineContext;
  }

  /**
   * [resetStateContext description]
   * @return [description]
   */
  @SuppressWarnings("unchecked")
  public SC resetStateContext() {
    stateContext = (SC)stateMachineDef.createStateContext(state);
    return stateContext;
  }

  /**
   * [resetStateMachineContext description]
   * @return [description]
   */
  public SMC resetStateMachineContext() {
    stateMachineContext = stateMachineDef.createStateMachineContext();
    return stateMachineContext;
  }

  /**
   * [setStateContext description]
   * @param context [description]
   */
  public void setStateContext(SC context) {
    stateContext = context;
  }

  /**
   * [setStateMachineContext description]
   * @param context [description]
   */
  public void setStateMachineContext(SMC context) {
    stateMachineContext = context;
  }

  /**
   * [withExecutor description]
   * @param  service [description]
   * @return         [description]
   */
  public StateActionContext<S, SC, SMC> copy(Executor executor) {
    return new StateActionContext<>(state, stateContext, stateMachineContext, executor, stateMachineDef);
  }
}