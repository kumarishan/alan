package alan.statemachine;

import alan.core.Tape;
import alan.core.ExecutionId;

import static alan.core.Tape.Status;
import static alan.core.Tape.Status.*;
import static alan.core.Tape.ContextLabel;

/**
 *
 */
abstract class StateMachineTape extends Tape {

  public StateMachineTape(ExecutionId id, int step, Status status, byte[] stateMachineContext) {
    super(id, step, status, stateMachineContext);
  }

  public abstract String getCurrentState();

  public abstract byte[] getCurrentStateContext();

  ///////////////////////////////// Factory Methods ////////////////////////////////////////////////

  public static StateMachineTape Start(ExecutionId id, int step, byte[] stateMachineContext, String state, byte[] stateContext) {
    return new Start(id, step, stateMachineContext, state, stateContext);
  }

  public static StateMachineTape Stage(ExecutionId id, int step, byte[] stateMachineContext,
                                        String currentState, byte[] currentStateContext, ContextLabel contextLabel,
                                        String prevState, byte[] prevStateContext,
                                        String triggerEvent) {
    return new Stage(id, step, stateMachineContext, currentState, currentStateContext, contextLabel, prevState, prevStateContext, triggerEvent);
  }

  public static StateMachineTape Stop(ExecutionId id, int step, byte[] stateMachineContext,
                                      String exception, String state, byte[] stateContext,
                                      String triggerEvent) {
    return new Stop(id, step, stateMachineContext, exception, state, stateContext, triggerEvent);
  }

  public static StateMachineTape Failure(ExecutionId id, int step, byte[] stateMachineContext,
                                         String state, byte[] result, String fromState, byte[] fromStateContext,
                                         String triggerEvent) {
    return new Failure(id, step, stateMachineContext, state, result, fromState, fromStateContext, triggerEvent);
  }

  public static StateMachineTape Success(ExecutionId id, int step, byte[] stateMachineContext,
                                         String state, byte[] result, String fromState, byte[] fromStateContext,
                                         String triggerEvent) {
    return new Success(id, step, stateMachineContext, state, result, fromState, fromStateContext, triggerEvent);
  }

  //////////////////////////////////// Tapes /////////////////////////////////////

  /**
   *
   */
  static class Start extends StateMachineTape {
    String state;
    byte[] stateContext;

    public Start(ExecutionId id, int step, byte[] stateMachineContext, String state, byte[] stateContext) {
      super(id, step, NEW, stateMachineContext);
      this.state = state;
      this.stateContext = stateContext;
    }

    public String getCurrentState() {
      return state;
    }

    public byte[] getCurrentStateContext() {
      return stateContext;
    }
  }

  /**
   *
   */
  static class Stage extends StateMachineTape {
    String currentState;
    byte[] currentStateContext;
    ContextLabel contextLabel;
    String prevState;
    byte[] prevStateContext;
    String triggerEvent;

    public Stage(ExecutionId id, int step, byte[] stateMachineContext,
                      String currentState, byte[] currentStateContext, ContextLabel contextLabel,
                      String prevState, byte[] prevStateContext,
                      String triggerEvent) {
      super(id, step, LIVE, stateMachineContext);
      this.currentState = currentState;
      this.currentStateContext = currentStateContext;
      this.contextLabel = contextLabel;
      this.prevState = prevState;
      this.prevStateContext = prevStateContext;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return currentState;
    }

    public byte[] getCurrentStateContext() {
      return currentStateContext;
    }
  }

  /**
   *
   */
  static class Stop extends StateMachineTape {
    String exception;
    String state;
    byte[] stateContext;
    String triggerEvent;

    public Stop(ExecutionId id, int step, byte[] stateMachineContext,
                String exception, String state, byte[] stateContext,
                String triggerEvent) {
      super(id, step, STOPPED, stateMachineContext);
      this.exception = exception;
      this.state = state;
      this.stateContext = stateContext;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return state;
    }

    public byte[] getCurrentStateContext() {
      return stateContext;
    }
  }

  /**
   *
   */
  static class Failure extends StateMachineTape {
    String state;
    byte[] result;
    String fromState;
    byte[] fromStateContext;
    String triggerEvent;

    public Failure(ExecutionId id, int step, byte[] stateMachineContext,
                   String state, byte[] result, String fromState, byte[] fromStateContext,
                   String triggerEvent) {
      super(id, step, FAILED, stateMachineContext);
      this.state = state;
      this.result = result;
      this.fromState = fromState;
      this.fromStateContext = fromStateContext;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return state;
    }

    public byte[] getCurrentStateContext() {
      return null;
    }
  }

  /**
   *
   */
  static class Success extends StateMachineTape {
    String state;
    byte[] result;
    String fromState;
    byte[] fromStateContext;
    String triggerEvent;

    public Success(ExecutionId id, int step, byte[] stateMachineContext,
                   String state, byte[] result, String fromState, byte[] fromStateContext,
                   String triggerEvent) {
      super(id, step, SUCCEEDED, stateMachineContext);
      this.state = state;
      this.result = result;
      this.fromState = fromState;
      this.fromStateContext = fromStateContext;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return state;
    }

    public byte[] getCurrentStateContext() {
      return null;
    }
  }
}