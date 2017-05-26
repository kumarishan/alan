package alan.statemachine;

import alan.core.Tape;
import alan.core.ExecutionId;

/**
 * 
 */
class StateMachineTape extends Tape {
  String currentState;
  byte[] stateContext;
  byte[] stateMachineContext;

  public StateMachineTape(ExecutionId id, int step, byte[] stateMachineContext, Throwable exception, String prevState, byte[] prevStateContext, String prevTriggerEventType) {}
  public StateMachineTape(ExecutionId id, int step, byte[] stateMachineContext, String toState, byte[] toContext, String prevState, byte[] prevStateContext, String prevTriggerEventType) {}
}