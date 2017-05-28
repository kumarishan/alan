package alan.statemachine;


/**
 *
 */
@FunctionalInterface
public interface ContextDeserializer<SC> {
  public SC apply(byte[] binary);
}