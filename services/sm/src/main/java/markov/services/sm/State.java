package markov.services.sm;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Array;

// holds settings for a state
// - statename
// - current stateContext
// - [todo] transitions associated with the state
public class State<S, SC> {

  private final S name;
  private final Class<SC> contextType;
  private final ContextFactory<SC> contextFactory;
  private final Map<Class<?>, List<Transition<S, ?, SC, ?>>> transitionMap;
  private final Set<Class<?>> eventTypes;
  {
    eventTypes = new HashSet<>();
  }

  public State(S name, Class<SC> contextType, ContextFactory<SC> contextFactory) {
    this(name, null, contextType, contextFactory, new HashMap<>());
  }

  private State(S name, SC context, Class<SC> contextType, ContextFactory<SC> contextFactory, Map<Class<?>, List<Transition<S, ?, SC, ?>>> transitionMap) {
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
    return contextFactory.apply();
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
    Predicate<E, SC, SMC> predicate;

    public Buildr(State<S, SC> state, Class<E> eventType, Predicate<E, SC, SMC> predicate) {
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
    public <E1> Buildr<S, SC, SMC, E1> onEvent(Class<E1> eventType, Predicate<E1, SC, SMC> predicate) {
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
    private final Predicate<E, SC, SMC> predicate;
    private final Action<S, E, SC, SMC> action;
    private final AsyncAction<S, E, SC, SMC> asyncAction;

    public Transition(Predicate<E, SC, SMC> predicate, Action<S, E, SC, SMC> action) {
      this.predicate = predicate;
      this.action = action;
      this.asyncAction = null;
    }


    public Transition(Predicate<E, SC, SMC> predicate, AsyncAction<S, E, SC, SMC> asyncAction) {
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
    public boolean check(E event, StateMachineDef.Context<SC, SMC> context) {
      return (this.predicate == null ? true : this.predicate.apply(event, context));
    }
  }

  /**
   *
   */
  public static interface Predicate<E, SC, SMC> {
    public boolean apply(E event, StateMachineDef.Context<SC, SMC> context);
  }

  /**
   *
   */
  public static interface Action<S, E, SC, SMC> {
    public To<S, ?> apply(E event, StateMachineDef.Context<SC, SMC> context) throws Throwable;
  }

  /**
   *
   */
  public static interface AsyncAction<S, E, SC, SMC> {
    public CompletableFuture<To<S, ?>> apply(E event, StateMachineDef.Context<SC, SMC> context) throws Throwable;
  }

  /**
   *
   */
  public static class To<S, SC> {
    private final S state;
    private final SC context;

    public To(S state, SC context) {
      this.state = state;
      this.context = context;
    }

    public To(S state) {
      this(state, null);
    }

    public S getState() {
      return state;
    }

    public SC getContext() {
      return context;
    }

    public <SC1> To<S, SC1> override(SC1 context) {
      return new To<>(state, context);
    }
  }

}