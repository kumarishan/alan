package alan.core;

public abstract class TapeCommand<R> {
  ExecutionId id;
  protected TapeCommand(ExecutionId id) {
    this.id = id;
  }

  public static ReleaseLock ReleaseLock(ExecutionId id) {
    return new ReleaseLock(id);
  }

  public static DiffPush DiffPush(ExecutionId id) {
    return new DiffPush(id);
  }

  public static Peek Peek(ExecutionId id, int step) {
    return new Peek(id, step);
  }

  public static AcquireLock AcquireLock(ExecutionId id) {
    return new AcquireLock(id);
  }

  public static GetStatus GetStatus(ExecutionId id) {
    return new GetStatus(id);
  }


  static class Fork extends TapeCommand<Boolean> {
    public Fork(ExecutionId id) {
      super(id);
    }
  }

  static class DiffPush extends TapeCommand<Boolean> {
    public DiffPush(ExecutionId id) {
      super(id);
    }
  }

  /**
   *
   */
  public static class Peek<T> extends TapeCommand<T> {
    private final int step;
    public Peek(ExecutionId id, int step) {
      super(id);
      this.step = step;
    }
  }

  /**
   *
   */
  public static class Pop extends TapeCommand<Tape> {
    public Pop(ExecutionId id) {
      super(id);
    }
  }


  /**
   *
   */
  public static class GetStatus extends TapeCommand<Tape.Status> {
    public GetStatus(ExecutionId id) {
      super(id);
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

  public static class ReleaseLock extends TapeCommand<Boolean> {
    public ReleaseLock(ExecutionId id) {
      super(id);
    }

  }

}

