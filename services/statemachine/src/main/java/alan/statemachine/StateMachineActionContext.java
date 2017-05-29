package alan.statemachine;

import java.util.concurrent.ExecutorService;


/**
 * [RENAME]
 */
public final class StateMachineActionContext<S, SC, SMC> {
  private final S state;
  private SC stateContext;
  private SMC stateMachineContext;
  private final ExecutorService executorService;
  private final StateMachineDef<S, SMC> stateMachineDef;

  StateMachineActionContext(S state, SC stateContext, SMC stateMachineContext, ExecutorService executorService, StateMachineDef<S, SMC> stateMachineDef) {
    this.state = state;
    this.stateContext = stateContext;
    this.stateMachineContext = stateMachineContext;
    this.executorService = executorService;
    this.stateMachineDef = stateMachineDef;
  }

  StateMachineActionContext(S state, SC stateContext, SMC stateMachineContext, StateMachineDef<S, SMC> stateMachineDef) {
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
    stateContext = (SC)stateMachineDef.getStateContextFactory(state).get();
    return stateContext;
  }

  /**
   * [resetStateMachineContext description]
   * @return [description]
   */
  public SMC resetStateMachineContext() {
    stateMachineContext = stateMachineDef.getStateMachineContextFactory().get();
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
   * [withExecutorService description]
   * @param  service [description]
   * @return         [description]
   */
  public StateMachineActionContext<S, SC, SMC> copy(ExecutorService service) {
    return new StateMachineActionContext<>(state, stateContext, stateMachineContext, service, stateMachineDef);
  }
}