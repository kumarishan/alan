package markov.services.sm;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.ExecutorService;


/**
 *
 */
class SinkState<S, SMC, R> {

  private final S name;
  private final Class<R> resultType;
  private final BiFunction<SMC, ExecutorService, CompletableFuture<R>> asyncAction;
  private final Function<SMC, R> action;
  private final boolean isSuccess;

  public SinkState(S name, Class<R> resultType, Function<SMC, R> action, boolean isSuccess) {
    this.name = name;
    this.resultType = resultType;
    this.asyncAction = null;
    this.action = action;
    this.isSuccess = isSuccess;
  }

  public SinkState(S name, Class<R> resultType, BiFunction<SMC, ExecutorService, CompletableFuture<R>> asyncAction, boolean isSuccess) {
    this.name = name;
    this.resultType = resultType;
    this.asyncAction = asyncAction;
    this.action = null;
    this.isSuccess = isSuccess;
  }

  /**
   *
   */
  public Class<?> getResultType() {
    return resultType;
  }

  /**
   * [isSuccess description]
   * @return [description]
   */
  public final boolean isSuccess() {
    return isSuccess;
  }

  /**
   * [isActionAsync description]
   * @return [description]
   */
  public final boolean isActionAsync() {
    return this.asyncAction != null;
  }

}

/**
 *
 */
class State<S, SC> {

  private final S name;
  private final Class<SC> contextType;
  private final Supplier<SC> contextFactory;
  private final Map<Class<?>, List<Transition<S, ?, SC, ?>>> transitionMap;
  private final Set<Class<?>> eventTypes;
  {
    eventTypes = new HashSet<>();
  }

  public State(S name, Class<SC> contextType, Supplier<SC> contextFactory) {
    this(name, null, contextType, contextFactory, new HashMap<>());
  }

  private State(S name, SC context, Class<SC> contextType, Supplier<SC> contextFactory, Map<Class<?>, List<Transition<S, ?, SC, ?>>> transitionMap) {
    this.name = name;
    this.contextType = contextType;
    this.contextFactory = contextFactory;
    this.transitionMap = transitionMap;
  }

  public State() {
    this(null, null, null);
  }

  /**
   * [getTransitions description]
   * @param  eventType [description]
   * @return           [description]
   */
  @SuppressWarnings("unchecked")
  public <E, SMC> List<Transition<S, E, SC, SMC>> getTransitions(Class<E> eventType) {
    if (!handlesEvent(eventType)) return null;
    else {
      List<Transition<S, E, SC, SMC>> result = new LinkedList<>();
      for (Transition<S, ?, SC, ?> transition : transitionMap.get(eventType)) {
        result.add((Transition<S, E, SC, SMC>) transition);
      }
      return result;
    }
  }

  /**
   * [getContextFactory description]
   * @return [description]
   */
  public Supplier<SC> getContextFactory() {
    return contextFactory;
  }

  /**
   * [validateContextType description]
   * @param  context [description]
   * @return         [description]
   */
  public boolean validateContextType(Object context) {
    return contextType.isInstance(context);
  }

  /**
   * [createContext description]
   * @return [description]
   */
  public SC createContext() {
    return contextFactory.get();
  }

  /**
   * [getContextType description]
   * @return [description]
   */
  public Class<SC> getContextType() {
    return contextType;
  }

  /**
   *
   */
  public Set<Class<?>> getEventTypes() {
    return new HashSet<>(this.eventTypes);
  }

  /**s
   *
   */
  public Set<Class<?>> getHandledEventTypes() {
    return transitionMap.keySet();
  }

  /**
   * [getName description]
   * @return [description]
   */
  public S getName() {
    return name;
  }

  /**
   * [getBuildr description]
   * @return [description]
   */
  public <SMC> Buildr<S, SC, SMC, Object> getBuildr() {
    return new Buildr<>(this);
  }

  /**
   * [handlesEvent description]
   * @param  eventType [description]
   * @return           [description]
   */
  public <E> boolean handlesEvent(Class<E> eventType) {
    return transitionMap.containsKey(eventType);
  }

  /**
   * [appendTransition description]
   * @param  eventType  [description]
   * @param  transition [description]
   * @return            [description]
   */
  private <E> void appendTransition(Class<E> eventType, Transition<S, E, SC, ?> transition) {
    List<Transition<S, ?, SC, ?>> transitions = transitionMap.get(eventType);
    if (transitions == null) {
      transitions = new LinkedList<>();
      transitionMap.put(eventType, transitions);
    }
    eventTypes.add(eventType);
    transitions.add(transition);
  }

  /**
   *
   */
  public static class Buildr<S, SC, SMC, E> {
    State<S, SC> state;
    Class<E> eventType;
    Predicate<S, E, SC, SMC> predicate;

    public Buildr(State<S, SC> state, Class<E> eventType, Predicate<S, E, SC, SMC> predicate) {
      this.state = state;
      this.eventType = eventType;
      this.predicate = predicate;
    }

    public Buildr(State<S, SC> state, Class<E> eventType) {
      this(state, eventType, null);
    }

    public Buildr(State<S, SC> state) {
      this(state, null);
    }

    /**
     * [onEvent description]
     * @param  eventType [description]
     * @return           [description]
     */
    public <E1> Buildr<S, SC, SMC, E1> onEvent(Class<E1> eventType) {
      return new Buildr<>(this.state, eventType);
    }

    /**
     * [onEvent description]
     * @param  eventType [description]
     * @param  predicate [description]
     * @return           [description]
     */
    public <E1> Buildr<S, SC, SMC, E1> onEvent(Class<E1> eventType, Predicate<S, E1, SC, SMC> predicate) {
      return new Buildr<>(this.state, eventType, predicate);
    }

    /**
     * [perform description]
     * @param  action [description]
     * @return        [description]
     */
    public Buildr<S, SC, SMC, Object> perform(Action<S, E, SC, SMC> action) {
      Transition<S, E, SC, SMC> transition = new Transition<>(this.predicate, action);
      this.state.appendTransition(this.eventType, transition);
      return new Buildr<>(state);
    }

    /**
     * [perform description]
     * @param  asyncAction [description]
     * @return             [description]
     */
    public Buildr<S, SC, SMC, Object> performAsync(AsyncAction<S, E, SC, SMC> asyncAction) {
      Transition<S, E, SC, SMC> transition = new Transition<>(this.predicate, asyncAction);
      this.state.appendTransition(this.eventType, transition);
      return new Buildr<>(state);
    }
  }

  /**
   *
   */
  public static class Transition<S, E, SC, SMC> {
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
    public boolean isAsync() {
      return asyncAction != null;
    }

    /**
     * [getAction description]
     * @return [description]
     */
    public Action<S, E, SC, SMC> getAction() {
      return action;
    }

    /**
     * [getAsyncAction description]
     * @return [description]
     */
    public AsyncAction<S, E, SC, SMC> getAsyncAction() {
      return asyncAction;
    }

    /**
     * [check description]
     * @param  event   [description]
     * @param  context [description]
     * @return         [description]
     */
    public boolean check(E event, StateMachineDef.Context<S, SC, SMC> context) {
      return (this.predicate == null ? true : this.predicate.apply(event, context));
    }
  }

  /**
   *
   */
  @FunctionalInterface
  public static interface Predicate<S, E, SC, SMC> {
    public boolean apply(E event, StateMachineDef.Context<S, SC, SMC> context);
  }

  /**
   *
   */
  @FunctionalInterface
  public static interface Action<S, E, SC, SMC> {
    public To<S, ?> apply(E event, StateMachineDef.Context<S, SC, SMC> context) throws Throwable;
  }

  /**
   *
   */
  @FunctionalInterface
  public static interface AsyncAction<S, E, SC, SMC> {
    public CompletableFuture<To<S, ?>> apply(E event, StateMachineDef.Context<S, SC, SMC> context);
  }

  /**
   *
   */
  public static class To<S, SC> {
    private final S state;
    private final SC contextOverride;

    public To(S state, SC contextOverride) {
      this.state = state;
      this.contextOverride = contextOverride;
    }

    public To(S state) {
      this(state, null);
    }

    public S getState() {
      return state;
    }

    public SC getContextOverride() {
      return contextOverride;
    }

    public <SC1> To<S, SC1> override(SC1 context) {
      return new To<>(state, context);
    }
  }

  /**
   *
   */
  public static class Stop<S> extends To<S, Object> {
    private final Throwable exception;

    public Stop() {
      super(null, null);
      this.exception = null;
    }

    public Stop(Throwable exception) {
      super(null, null);
      this.exception = exception;
    }

    public Throwable getException() {
      return this.exception;
    }
  }

}