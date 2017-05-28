package alan.statemachine;


/**
 *
 */
@FunctionalInterface
public interface ContextSerializer<SC> {
  public byte[] apply(SC context);
}
