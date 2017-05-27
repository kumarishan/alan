package alan.statemachine;

import alan.core.Tape;
import alan.core.ExecutionId;
import alan.core.SchemaRow;

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

  public static StateMachineTape fromSchema(SchemaRow row) {
    switch(String.class.cast(row.get("tapeType"))) {
      case "Start":
        return Start.fromSchemaRow(row);
      case "Stage":
        return Stage.fromSchemaRow(row);
      case "Stop":
        return Stop.fromSchemaRow(row);
      case "Failure":
        return Failure.fromSchemaRow(row);
      case "Success":
        return Success.fromSchemaRow(row);
      default:
        return null;
    }
  }

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

    public SchemaRow toSchemaRow() {
      SchemaRow row = new SchemaRow(id, step, status, stateMachineContext);
      row.put("tapeType", String.class, "Start");
      row.put("state", String.class, state);
      row.put("stateContext", byte[].class, stateContext);
      return row;
    }

    public static Start fromSchemaRow(SchemaRow row) {
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

    public SchemaRow toSchemaRow() {
      SchemaRow row = new SchemaRow(id, step, status, stateMachineContext);
      row.put("tapeType", String.class, "Stage");
      row.put("currentState", String.class, currentState);
      row.put("currentStateContext", byte[].class, currentStateContext);
      row.put("contextLabel", String.class, contextLabel.toString());
      row.put("prevState", String.class, prevState);
      row.put("prevStateContext", byte[].class, prevStateContext);
      row.put("triggerEvent", String.class, triggerEvent);
      return row;
    }

    public static Stage fromSchemaRow(SchemaRow row) {
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

    public SchemaRow toSchemaRow() {
      SchemaRow row = new SchemaRow(id, step, status, stateMachineContext);
      row.put("tapeType", String.class, "Stop");
      row.put("exception", String.class, exception);
      row.put("state", String.class, state);
      row.put("stateContext", byte[].class, stateContext);
      row.put("triggerEvent", String.class, triggerEvent);
      return row;
    }

    public static Stop fromSchemaRow(SchemaRow row) {
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

    public SchemaRow toSchemaRow() {
      SchemaRow row = new SchemaRow(id, step, status, stateMachineContext);
      row.put("tapeType", String.class, "Failure");
      row.put("state", String.class, state);
      row.put("result", byte[].class, result);
      row.put("fromState", String.class, fromState);
      row.put("fromStateContext", byte[].class, fromStateContext);
      row.put("triggerEvent", String.class, triggerEvent);
      return row;
    }

    public static Failure fromSchemaRow(SchemaRow row) {
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

    public SchemaRow toSchemaRow() {
      SchemaRow row = new SchemaRow(id, step, status, stateMachineContext);
      row.put("tapeType", String.class, "Success");
      row.put("state", String.class, state);
      row.put("result", byte[].class, result);
      row.put("fromState", String.class, fromState);
      row.put("fromStateContext", byte[].class, fromStateContext);
      row.put("triggerEvent", String.class, triggerEvent);
      return row;
    }

    public static Failure fromSchemaRow(SchemaRow row) {
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