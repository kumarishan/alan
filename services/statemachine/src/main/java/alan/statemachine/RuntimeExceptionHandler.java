package alan.statemachine;

/**
 * 
 */
@FunctionalInterface
public interface RuntimeExceptionHandler<S, SMC> {
  public Transition.To handle(S state, Object event, StateActionContext<S, ?, SMC> context, Throwable exception);
}