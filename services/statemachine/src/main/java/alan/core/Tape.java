package alan.core;

import static alan.core.Tape.Status.*;


/**
 * 
 */
public abstract class Tape {

  public final ExecutionId id;
  public final int step;
  public final Status status;
  public final byte[] stateMachineContext;
  public final long timestamp;

  public Tape(ExecutionId id, int step, Status status, byte[] stateMachineContext, long timestamp) {
    this.id = id;
    this.step = step;
    this.status = status;
    this.stateMachineContext = stateMachineContext;
    this.timestamp = timestamp;
  }

  public boolean isNew() {
    return status == NEW;
  }

  public boolean isCompleted() {
    return status == SUCCEEDED || status == FAILED || status == STOPPED;
  }

  public static enum Status {
    NEW, LIVE, FAILED, SUCCEEDED, STOPPED;
  }

  public static enum ContextLabel {
    NEW, CARRY, OVERRIDE;
  }
}