package alan.util;

public class FI {

  @FunctionalInterface
  public static interface TriFunction<T, U, V, R> {
    public R apply(T t, U u, V v);
  }
}