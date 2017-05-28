package alan.statemachine;

import java.util.Set;
import java.util.HashSet;

import alan.core.Schema;

import static alan.statemachine.StateMachineTape.*;


public class StateMachineSchema implements Schema<StateMachineTape> {

  public Schema.Tape toSchemaTape(StateMachineTape tape) {
    if (tape instanceof Start) {
      return Start.toSchemaTape((Start)tape);
    } else if (tape instanceof Stage) {
      return Stage.toSchemaTape((Stage)tape);
    } else if (tape instanceof Stop) {
      return Stop.toSchemaTape((Stop)tape);
    } else if (tape instanceof Failure) {
      return Failure.toSchemaTape((Failure)tape);
    } else if (tape instanceof Success) {
      return Success.toSchemaTape((Success)tape);
    } else return null; // throw error
  }

  public StateMachineTape tapeFromSchemaTape(Schema.Tape tape) {
    switch(String.class.cast(tape.get("tapeType"))) {
      case "Start":
        return Start.fromSchemaTape(tape);
      case "Stage":
        return Stage.fromSchemaTape(tape);
      case "Stop":
        return Stop.fromSchemaTape(tape);
      case "Failure":
        return Failure.fromSchemaTape(tape);
      case "Success":
        return Success.fromSchemaTape(tape);
      default:
        return null;
    }
  }

  public Set<Schema.StateContext> getSchemaStateContext(StateMachineTape tape) {
    Set<Schema.StateContext> contexts = new HashSet<>();
    if (tape instanceof Start) {
      Start start = (Start)tape;
      contexts.add(Schema.StateContext(start.step, start.state, start.stateContext));
    } else if (tape instanceof Stage) {
      Stage stage = (Stage)tape;
      contexts.add(Schema.StateContext(stage.step, stage.currentState, stage.currentStateContext));
      contexts.add(Schema.StateContext(stage.step, stage.prevState, stage.prevStateContext));
    } else if (tape instanceof Stop) {
      Stop stop = (Stop)tape;
      contexts.add(Schema.StateContext(stop.step, stop.state, stop.stateContext));
    } else if (tape instanceof Failure) {
      Failure failure = (Failure)tape;
      contexts.add(Schema.StateContext(failure.step, failure.fromState, failure.fromStateContext));
    } else if (tape instanceof Success) {
      Success failure = (Success)tape;
      contexts.add(Schema.StateContext(failure.step, failure.fromState, failure.fromStateContext));
    }
    return contexts;
  }
}