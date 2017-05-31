package alan.core;


/**
 * 
 */
public abstract class TapeCommand<R> {
  public final ExecutionId id;
  protected TapeCommand(ExecutionId id) {
    this.id = id;
  }

  public static ReleaseLock ReleaseLock(ExecutionId id) {
    return new ReleaseLock(id);
  }

  public static Push Push(ExecutionId id, Tape tape) {
    return new Push(id, tape);
  }

  public static <T extends Tape> Peek<T> Peek(ExecutionId id, Class<T> type) {
    return new Peek<>(id, type);
  }

  public static AcquireLock AcquireLock(ExecutionId id) {
    return new AcquireLock(id);
  }

  public static GetStateContext GetStateContext(ExecutionId id, String state) {
    return new GetStateContext(id, state);
  }

  /**
   * 
   */
  public static class Push extends TapeCommand<Boolean> {
    public final Tape tape;
    public Push(ExecutionId id, Tape tape) {
      super(id);
      this.tape = tape;
    }
  }

  /**
   *
   */
  public static class Peek<T extends Tape> extends TapeCommand<T> {
    public final Class<T> type;
    public Peek(ExecutionId id, Class<T> type) {
      super(id);
      this.type = type;
    }
  }

  /**
   *
   */
  public static class GetStateContext extends TapeCommand<byte[]> {
    public final String state;
    public GetStateContext(ExecutionId id, String state) {
      super(id);
      this.state = state;
    }
  }

  /**
   *
   */
  public static class AcquireLock extends TapeCommand<Boolean> {
    public AcquireLock(ExecutionId id) {
      super(id);
    }
  }

  /**
   *
   */
  public static class ReleaseLock extends TapeCommand<Boolean> {
    public ReleaseLock(ExecutionId id) {
      super(id);
    }
  }

}

