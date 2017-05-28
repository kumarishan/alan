package alan.statemachine;

import alan.core.Tape;
import alan.core.ExecutionId;
import alan.core.Schema;

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

  public static Start Start(ExecutionId id, int step, byte[] stateMachineContext, String state, byte[] stateContext) {
    return new Start(id, step, stateMachineContext, state, stateContext);
  }

  public static Stage Stage(ExecutionId id, int step, byte[] stateMachineContext,
                                        String currentState, byte[] currentStateContext, ContextLabel contextLabel,
                                        String prevState, byte[] prevStateContext,
                                        String triggerEvent) {
    return new Stage(id, step, stateMachineContext, currentState, currentStateContext, contextLabel, prevState, prevStateContext, triggerEvent);
  }

  public static Stop Stop(ExecutionId id, int step, byte[] stateMachineContext,
                                      String exception, String state, byte[] stateContext,
                                      String triggerEvent) {
    return new Stop(id, step, stateMachineContext, exception, state, stateContext, triggerEvent);
  }

  public static Failure Failure(ExecutionId id, int step, byte[] stateMachineContext,
                                         String state, byte[] result, String fromState, byte[] fromStateContext,
                                         String triggerEvent) {
    return new Failure(id, step, stateMachineContext, state, result, fromState, fromStateContext, triggerEvent);
  }

  public static Success Success(ExecutionId id, int step, byte[] stateMachineContext,
                                         String state, byte[] result, String fromState, byte[] fromStateContext,
                                         String triggerEvent) {
    return new Success(id, step, stateMachineContext, state, result, fromState, fromStateContext, triggerEvent);
  }

  //////////////////////////////////// Tapes /////////////////////////////////////

  /**
   *
   */
  static class Start extends StateMachineTape {
    public String state;
    public byte[] stateContext;

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

    public static Schema.Tape toSchemaTape(Start start) {
      Schema.Tape row = new Schema.Tape(start.id, start.step, start.status, start.stateMachineContext);
      row.put("tapeType", String.class, "Start");
      row.put("state", String.class, start.state);
      row.put("stateContext", byte[].class, start.stateContext);
      return row;
    }

    public static Start fromSchemaTape(Schema.Tape row) {
      return Start(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("state")),
        byte[].class.cast(row.get("stateContext"))
      );
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

    public static Schema.Tape toSchemaTape(Stage stage) {
      Schema.Tape row = new Schema.Tape(stage.id, stage.step, stage.status, stage.stateMachineContext);
      row.put("tapeType", String.class, "Stage");
      row.put("currentState", String.class, stage.currentState);
      row.put("currentStateContext", byte[].class, stage.currentStateContext);
      row.put("contextLabel", String.class, stage.contextLabel.toString());
      row.put("prevState", String.class, stage.prevState);
      row.put("prevStateContext", byte[].class, stage.prevStateContext);
      row.put("triggerEvent", String.class, stage.triggerEvent);
      return row;
    }

    public static Stage fromSchemaTape(Schema.Tape row) {
      return Stage(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("currentState")),
        byte[].class.cast(row.get("stateContext")),
        ContextLabel.valueOf(String.class.cast(row.get("contextLabel"))),
        String.class.cast(row.get("prevState")),
        byte[].class.cast(row.get("prevStateContext")),
        String.class.cast(row.get("triggerEvent"))
      );
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

    public static Schema.Tape toSchemaTape(Stop stop) {
      Schema.Tape row = new Schema.Tape(stop.id, stop.step, stop.status, stop.stateMachineContext);
      row.put("tapeType", String.class, "Stop");
      row.put("exception", String.class, stop.exception);
      row.put("state", String.class, stop.state);
      row.put("stateContext", byte[].class, stop.stateContext);
      row.put("triggerEvent", String.class, stop.triggerEvent);
      return row;
    }

    public static Stop fromSchemaTape(Schema.Tape row) {
      return Stop(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("exception")),
        String.class.cast(row.get("state")),
        byte[].class.cast(row.get("stateContext")),
        String.class.cast(row.get("triggerEvent"))
      );
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

    public static Schema.Tape toSchemaTape(Failure failure) {
      Schema.Tape row = new Schema.Tape(failure.id, failure.step, failure.status, failure.stateMachineContext);
      row.put("tapeType", String.class, "Failure");
      row.put("state", String.class, failure.state);
      row.put("result", byte[].class, failure.result);
      row.put("fromState", String.class, failure.fromState);
      row.put("fromStateContext", byte[].class, failure.fromStateContext);
      row.put("triggerEvent", String.class, failure.triggerEvent);
      return row;
    }

    public static Failure fromSchemaTape(Schema.Tape row) {
      return Failure(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("state")),
        byte[].class.cast(row.get("result")),
        String.class.cast(row.get("fromState")),
        byte[].class.cast(row.get("fromStateContext")),
        String.class.cast(row.get("triggerEvent"))
      );
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

    public static Schema.Tape toSchemaTape(Success success) {
      Schema.Tape row = new Schema.Tape(success.id, success.step, success.status, success.stateMachineContext);
      row.put("tapeType", String.class, "Success");
      row.put("state", String.class, success.state);
      row.put("result", byte[].class, success.result);
      row.put("fromState", String.class, success.fromState);
      row.put("fromStateContext", byte[].class, success.fromStateContext);
      row.put("triggerEvent", String.class, success.triggerEvent);
      return row;
    }

    public static Failure fromSchemaTape(Schema.Tape row) {
      return Failure(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("state")),
        byte[].class.cast(row.get("result")),
        String.class.cast(row.get("fromState")),
        byte[].class.cast(row.get("fromStateContext")),
        String.class.cast(row.get("triggerEvent"))
      );
    }
  }
}