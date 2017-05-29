package alan.statemachine;

import java.util.concurrent.CompletableFuture;


/**
 *
 */
class Transition<S, E, SC, SMC> {
  private final Predicate<S, E, SC, SMC> predicate;
  private final Action<S, E, SC, SMC> action;
  private final AsyncAction<S, E, SC, SMC> asyncAction;

  public Transition(Predicate<S, E, SC, SMC> predicate, Action<S, E, SC, SMC> action) {
    this.predicate = predicate;
    this.action = action;
    this.asyncAction = null;
  }

  public Transition(Predicate<S, E, SC, SMC> predicate, AsyncAction<S, E, SC, SMC> asyncAction) {
    this.predicate = predicate;
    this.asyncAction = asyncAction;
    this.action = null;
  }

  /**
   * [isAsync description]
   * @return [description]
   */
  boolean isAsync() {
    return asyncAction != null;
  }

  /**
   * [getAction description]
   * @return [description]
   */
  Action<S, E, SC, SMC> getAction() {
    return action;
  }

  /**
   * [getAsyncAction description]
   * @return [description]
   */
  AsyncAction<S, E, SC, SMC> getAsyncAction() {
    return asyncAction;
  }

  /**
   * [check description]
   * @param  event   [description]
   * @param  context [description]
   * @return         [description]
   */
  boolean check(E event, StateActionContext<S, SC, SMC> context) {
    return (predicate == null ? true : predicate.apply(event, context));
  }

  /**
   *
   */
  public static class Buildr<S, SC, SMC, E> {
    private final StateDef<S, SC, SMC> stateDef;
    private final Class<E> eventType;
    private final Predicate<S, E, SC, SMC> predicate;

    public Buildr(StateDef<S, SC, SMC> stateDef, Class<E> eventType, Predicate<S, E, SC, SMC> predicate) {
      this.stateDef = stateDef;
      this.eventType = eventType;
      this.predicate = predicate;
    }

    public Buildr(StateDef<S, SC, SMC> stateDef, Class<E> eventType) {
      this(stateDef, eventType, null);
    }

    public Buildr(StateDef<S, SC, SMC> stateDef) {
      this(stateDef, null);
    }

    /**
     * [perform description]
     * @param  action [description]
     * @return        [description]
     */
    public Buildr<S, SC, SMC, Object> perform(Action<S, E, SC, SMC> action) {
      Transition<S, E, SC, SMC> transition = new Transition<>(predicate, action);
      stateDef.appendTransition(eventType, transition);
      return new Buildr<>(stateDef);
    }

    /**
     * [perform description]
     * @param  asyncAction [description]
     * @return             [description]
     */
    public Buildr<S, SC, SMC, Object> performAsync(AsyncAction<S, E, SC, SMC> asyncAction) {
      Transition<S, E, SC, SMC> transition = new Transition<>(predicate, asyncAction);
      stateDef.appendTransition(eventType, transition);
      return new Buildr<>(stateDef);
    }
  }

  /**
   *
   */
  public static interface To {}

  /**
   *
   */
  public static class GoTo<S, SC> implements To {
    final S state;
    final SC contextOverride;

    public GoTo(S state, SC contextOverride) {
      this.state = state;
      this.contextOverride = contextOverride;
    }

    public GoTo(S state) {
      this(state, null);
    }

    public <SC1> GoTo<S, SC1> override(SC1 context) {
      return new GoTo<>(state, context);
    }
  }

  /**
   *
   */
  public static class FailTo<S> implements To {
    final S state;
    final Throwable exception;

    public FailTo(S state, Throwable exception) {
      this.state = state;
      this.exception = exception;
    }

    public FailTo(S state) {
      this(state, null);
    }
  }

  /**
   *
   */
  public static class Stop implements To {
    final Throwable exception;

    public Stop(Throwable exception) {
      this.exception = exception;
    }

    public Stop() {
      this(null);
    }
  }

  /**
   *
   */
  @FunctionalInterface
  public static interface Predicate<S, E, SC, SMC> {
    public boolean apply(E event, StateActionContext<S, SC, SMC> context);
  }

  /**
   *
   */
  @FunctionalInterface
  public static interface Action<S, E, SC, SMC> {
    public To apply(E event, StateActionContext<S, SC, SMC> context) throws Throwable;
  }

  /**
   *
   */
  @FunctionalInterface
  public static interface AsyncAction<S, E, SC, SMC> {
    public CompletableFuture<To> apply(E event, StateActionContext<S, SC, SMC> context);
  }
}