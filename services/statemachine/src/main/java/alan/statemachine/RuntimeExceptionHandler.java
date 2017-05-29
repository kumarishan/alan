package alan.statemachine;

/**
 * 
 */
@FunctionalInterface
public interface RuntimeExceptionHandler<S, SMC> {
  public Transition.To<S, ?> handle(S state, Object event, StateMachineActionContext<S, ?, SMC> context, Throwable exception);
}