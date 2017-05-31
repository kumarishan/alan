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
public abstract class StateMachineTape extends Tape {

  public StateMachineTape(ExecutionId id, int step, Status status, byte[] stateMachineContext, long timestamp) {
    super(id, step, status, stateMachineContext, timestamp);
  }

  public abstract String getCurrentState();

  public abstract byte[] getCurrentStateContext();

  ///////////////////////////////// Factory Methods ////////////////////////////////////////////////

  public static Start Start(ExecutionId id, int step, byte[] stateMachineContext, String state, byte[] stateContext, long timestamp) {
    return new Start(id, step, stateMachineContext, state, stateContext, timestamp);
  }

  public static Stage Stage(ExecutionId id, int step, byte[] stateMachineContext,
                                        String currentState, byte[] currentStateContext, ContextLabel contextLabel,
                                        String prevState, byte[] prevStateContext,
                                        String triggerEventType, byte[] triggerEvent, long timestamp) {
    return new Stage(id, step, stateMachineContext, currentState, currentStateContext, contextLabel, prevState, prevStateContext, triggerEventType, triggerEvent, timestamp);
  }

  public static Stop Stop(ExecutionId id, int step, byte[] stateMachineContext,
                                      String exception, String state, byte[] stateContext,
                                      String triggerEventType, byte[] triggerEvent, long timestamp) {
    return new Stop(id, step, stateMachineContext, exception, state, stateContext, triggerEventType, triggerEvent, timestamp);
  }

  public static Failure Failure(ExecutionId id, int step, byte[] stateMachineContext,
                                         String state, byte[] result, String fromState, byte[] fromStateContext,
                                         String triggerEventType, byte[] triggerEvent, long timestamp) {
    return new Failure(id, step, stateMachineContext, state, result, fromState, fromStateContext, triggerEventType, triggerEvent, timestamp);
  }

  public static Success Success(ExecutionId id, int step, byte[] stateMachineContext,
                                         String state, byte[] result, String fromState, byte[] fromStateContext,
                                         String triggerEventType, byte[] triggerEvent, long timestamp) {
    return new Success(id, step, stateMachineContext, state, result, fromState, fromStateContext, triggerEventType, triggerEvent, timestamp);
  }

  //////////////////////////////////// Tapes /////////////////////////////////////

  /**
   *
   */
  static class Start extends StateMachineTape {
    public String state;
    public byte[] stateContext;

    public Start(ExecutionId id, int step, byte[] stateMachineContext, String state, byte[] stateContext, long timestamp) {
      super(id, step, NEW, stateMachineContext, timestamp);
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
      Schema.Tape row = new Schema.Tape(start.id, start.step, start.status, start.stateMachineContext, start.timestamp);
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
        byte[].class.cast(row.get("stateContext")),
        row.timestamp
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
    String triggerEventType;
    byte[] triggerEvent;

    public Stage(ExecutionId id, int step, byte[] stateMachineContext,
                      String currentState, byte[] currentStateContext, ContextLabel contextLabel,
                      String prevState, byte[] prevStateContext,
                      String triggerEventType, byte[] triggerEvent, long timestamp) {
      super(id, step, LIVE, stateMachineContext, timestamp);
      this.currentState = currentState;
      this.currentStateContext = currentStateContext;
      this.contextLabel = contextLabel;
      this.prevState = prevState;
      this.prevStateContext = prevStateContext;
      this.triggerEventType = triggerEventType;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return currentState;
    }

    public byte[] getCurrentStateContext() {
      return currentStateContext;
    }

    public static Schema.Tape toSchemaTape(Stage stage) {
      Schema.Tape row = new Schema.Tape(stage.id, stage.step, stage.status, stage.stateMachineContext, stage.timestamp);
      row.put("tapeType", String.class, "Stage");
      row.put("currentState", String.class, stage.currentState);
      row.put("currentStateContext", byte[].class, stage.currentStateContext);
      row.put("contextLabel", String.class, stage.contextLabel.toString());
      row.put("prevState", String.class, stage.prevState);
      row.put("prevStateContext", byte[].class, stage.prevStateContext);
      row.put("triggerEventType", String.class, stage.triggerEventType);
      row.put("triggerEvent", byte[].class, stage.triggerEvent);
      return row;
    }

    public static Stage fromSchemaTape(Schema.Tape row) {
      return Stage(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("currentState").value),
        byte[].class.cast(row.get("currentStateContext").value),
        ContextLabel.valueOf(String.class.cast(row.get("contextLabel").value)),
        String.class.cast(row.get("prevState").value),
        byte[].class.cast(row.get("prevStateContext").value),
        String.class.cast(row.get("triggerEventType").value),
        byte[].class.cast(row.get("triggerEvent").value),
        row.timestamp
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
    String triggerEventType;
    byte[] triggerEvent;

    public Stop(ExecutionId id, int step, byte[] stateMachineContext,
                String exception, String state, byte[] stateContext,
                String triggerEventType, byte[] triggerEvent, long timestamp) {
      super(id, step, STOPPED, stateMachineContext, timestamp);
      this.exception = exception;
      this.state = state;
      this.stateContext = stateContext;
      this.triggerEventType = triggerEventType;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return state;
    }

    public byte[] getCurrentStateContext() {
      return stateContext;
    }

    public static Schema.Tape toSchemaTape(Stop stop) {
      Schema.Tape row = new Schema.Tape(stop.id, stop.step, stop.status, stop.stateMachineContext, stop.timestamp);
      row.put("tapeType", String.class, "Stop");
      row.put("exception", String.class, stop.exception);
      row.put("state", String.class, stop.state);
      row.put("stateContext", byte[].class, stop.stateContext);
      row.put("triggerEventType", String.class, stop.triggerEventType);
      row.put("triggerEvent", byte[].class, stop.triggerEvent);
      return row;
    }

    public static Stop fromSchemaTape(Schema.Tape row) {
      return Stop(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("exception").value),
        String.class.cast(row.get("state").value),
        byte[].class.cast(row.get("stateContext").value),
        String.class.cast(row.get("triggerEventType").value),
        byte[].class.cast(row.get("triggerEvent").value),
        row.timestamp
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
    String triggerEventType;
    byte[] triggerEvent;

    public Failure(ExecutionId id, int step, byte[] stateMachineContext,
                   String state, byte[] result, String fromState, byte[] fromStateContext,
                   String triggerEventType, byte[] triggerEvent, long timestamp) {
      super(id, step, FAILED, stateMachineContext, timestamp);
      this.state = state;
      this.result = result;
      this.fromState = fromState;
      this.fromStateContext = fromStateContext;
      this.triggerEventType = triggerEventType;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return state;
    }

    public byte[] getCurrentStateContext() {
      return null;
    }

    public static Schema.Tape toSchemaTape(Failure failure) {
      Schema.Tape row = new Schema.Tape(failure.id, failure.step, failure.status, failure.stateMachineContext, failure.timestamp);
      row.put("tapeType", String.class, "Failure");
      row.put("state", String.class, failure.state);
      row.put("result", byte[].class, failure.result);
      row.put("fromState", String.class, failure.fromState);
      row.put("fromStateContext", byte[].class, failure.fromStateContext);
      row.put("triggerEventType", String.class, failure.triggerEventType);
      row.put("triggerEvent", byte[].class, failure.triggerEvent);
      return row;
    }

    public static Failure fromSchemaTape(Schema.Tape row) {
      return Failure(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("state").value),
        byte[].class.cast(row.get("result").value),
        String.class.cast(row.get("fromState").value),
        byte[].class.cast(row.get("fromStateContext").value),
        String.class.cast(row.get("triggerEventType").value),
        byte[].class.cast(row.get("triggerEvent").value),
        row.timestamp
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
    String triggerEventType;
    byte[] triggerEvent;

    public Success(ExecutionId id, int step, byte[] stateMachineContext,
                   String state, byte[] result, String fromState, byte[] fromStateContext,
                   String triggerEventType, byte[] triggerEvent, long timestamp) {
      super(id, step, SUCCEEDED, stateMachineContext, timestamp);
      this.state = state;
      this.result = result;
      this.fromState = fromState;
      this.fromStateContext = fromStateContext;
      this.triggerEventType = triggerEventType;
      this.triggerEvent = triggerEvent;
    }

    public String getCurrentState() {
      return state;
    }

    public byte[] getCurrentStateContext() {
      return null;
    }

    public static Schema.Tape toSchemaTape(Success success) {
      Schema.Tape row = new Schema.Tape(success.id, success.step, success.status, success.stateMachineContext, success.timestamp);
      row.put("tapeType", String.class, "Success");
      row.put("state", String.class, success.state);
      row.put("result", byte[].class, success.result);
      row.put("fromState", String.class, success.fromState);
      row.put("fromStateContext", byte[].class, success.fromStateContext);
      row.put("triggerEventType", String.class, success.triggerEventType);
      row.put("triggerEvent", byte[].class, success.triggerEvent);
      return row;
    }

    public static Failure fromSchemaTape(Schema.Tape row) {
      return Failure(
        row.id,
        row.step,
        row.stateMachineContext,
        String.class.cast(row.get("state").value),
        byte[].class.cast(row.get("result").value),
        String.class.cast(row.get("fromState").value),
        byte[].class.cast(row.get("fromStateContext").value),
        String.class.cast(row.get("triggerEventType").value),
        byte[].class.cast(row.get("triggerEvent").value),
        row.timestamp
      );
    }
  }
}