package alan.core;

public abstract class Tape {

  public static class Status {
    public ExecutionId id;
    public int step;

    public boolean isLive() {
      return true;
    }

    public boolean isNew() {
      return true;
    }
  }
}