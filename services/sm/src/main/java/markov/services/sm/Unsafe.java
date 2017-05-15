package markov.services.sm;

import java.lang.reflect.Field;

public final class Unsafe {
  public final static sun.misc.Unsafe instance;

  static {
    try {
      sun.misc.Unsafe newInstance = null;
      for (Field field : sun.misc.Unsafe.class.getDeclaredFields()) {
        if (field.getType() == sun.misc.Unsafe.class) {
          field.setAccessible(true);
          newInstance = (sun.misc.Unsafe) field.get(null);
          break;
        }
      }
      if (newInstance == null) throw new IllegalStateException("No instance of sun.misc.Unsafe");
      else instance = newInstance;
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}