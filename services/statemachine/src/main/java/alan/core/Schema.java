package alan.core;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import static alan.core.Tape.Status;


public interface Schema {
  public Schema.Tape toSchemaTape(alan.core.Tape tape);
  public alan.core.Tape tapeFromSchemaTape(Schema.Tape tape);
  public Set<Schema.StateContext> getSchemaStateContext(alan.core.Tape tape);


  public static StateContext StateContext(int step, String state, byte[] context) {
    return new StateContext(step, state, context);
  }

  public static Tape Tape(ExecutionId id, int step, Status status, byte[] stateMachineContext) {
    return new Tape(id, step, status, stateMachineContext);
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

    private final Map<String, Data<?>> row;

    public Tape(ExecutionId id, int step, Status status, byte[] stateMachineContext) {
      this.id = id;
      this.step = step;
      this.status = status;
      this.stateMachineContext = stateMachineContext;
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
      final Class<?> type;
      final T value;

      public Data(Class<T> type, T value) {
        this.type = type;
        this.value = value;
      }
    }

  }
}