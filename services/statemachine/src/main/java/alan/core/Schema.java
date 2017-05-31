package alan.core;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import static alan.core.Tape.Status;


public interface Schema<T extends alan.core.Tape> {
  public Schema.Tape toSchemaTape(T tape);
  public T tapeFromSchemaTape(Schema.Tape tape);
  public Set<Schema.StateContext> getSchemaStateContext(T tape);


  public static StateContext StateContext(int step, String state, byte[] context) {
    return new StateContext(step, state, context);
  }

  public static Tape Tape(ExecutionId id, int step, Status status, byte[] stateMachineContext, long timestamp) {
    return new Tape(id, step, status, stateMachineContext, timestamp);
  }

  /**
   *
   */
  public static class StateContext {
    public final int step;
    public final String state;
    public final byte[] context;

    public StateContext(int step, String state, byte[] context) {
      this.step = step;
      this.state = state;
      this.context = context;
    }
  }


  /**
   *
   */
  public static class Tape {
    public final ExecutionId id;
    public final int step;
    public final Status status;
    public final byte[] stateMachineContext;
    public final long timestamp;

    private final Map<String, Data<?>> row;

    public Tape(ExecutionId id, int step, Status status, byte[] stateMachineContext, long timestamp) {
      this.id = id;
      this.step = step;
      this.status = status;
      this.stateMachineContext = stateMachineContext;
      this.timestamp = timestamp;
      this.row = new HashMap<>();
    }

    public Data get(String key) {
      return this.row.get(key);
    }

    public <T> void put(String key, Class<T> valueType, T value) {
      this.row.put(key, new Data<>(valueType, value));
    }

    public Set<Entry<?>> entrySet() {
      return null;
    }

    public static class Entry<T> {
      final String key;
      final Data<T> data;

      public Entry(String key, Data<T> data) {
        this.key = key;
        this.data = data;
      }
    }

    /**
     *
     */
    public static class Data<T> {
      public final Class<?> type;
      public final T value;

      public Data(Class<T> type, T value) {
        this.type = type;
        this.value = value;
      }
    }

  }
}