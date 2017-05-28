package alan.statemachine;

/**
 * 
 */
@FunctionalInterface
public interface RuntimeExceptionHandler<S, SMC> {
  public State.To<S, ?> handle(S state, Object event, StateMachineDef.ActionContext<S, ?, SMC> context, Throwable exception);
}