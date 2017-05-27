package alan.core;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import static alan.core.Tape.Status;


/**
 *
 */
public class SchemaRow {
  public final ExecutionId id;
  public final int step;
  public final Status status;
  public final byte[] stateMachineContext;

  private final Map<String, Data<?>> row;

  public SchemaRow(ExecutionId id, int step, Status status, byte[] stateMachineContext) {
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