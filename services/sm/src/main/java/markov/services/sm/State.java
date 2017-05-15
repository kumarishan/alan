package markov.services.sm;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

// holds settings for a state
// - statename
// - current stateContext
// - [todo] transitions associated with the state
public final class State<S, SC> {

  private final S name;
  private final SC context;
  private final ContextFactory<SC> contextFactory;
  private final Map<Class<?>, List<Transition<?, SC, ?>>> transitionMap;
  private final ContextSerializer<SC> serializer;
  private final ContextDeserializer<SC> deserializer;
  private final Set<Class<?>> eventTypes;
  {
    eventTypes = new HashSet<>();
  }

  private State(S name, SC context, ContextFactory<SC> contextFactory, Map<Class<?>, List<Transition<?, SC, ?>>> transitionMap,
                ContextSerializer<SC> serializer, ContextDeserializer<SC> deserializer) {
    this.name = name;
    this.context = context;
    this.contextFactory = contextFactory;
    this.transitionMap = transitionMap;
    this.serializer = serializer;
    this.deserializer = deserializer;
  }

  public State(S name, ContextFactory<SC> contextFactory,
               ContextSerializer<SC> serializer, ContextDeserializer<SC> deserializer) {
    this(name, null, contextFactory, new HashMap<>(), serializer, deserializer);
  }

  /**
   * [getTransitions description]
   * @param  eventType [description]
   * @return           [description]
   */
  @SuppressWarnings("unchecked")
  public <E> List<Transition<E, SC, ?>> getTransitions(Class<E> eventType) {
    if (!handlesEvent(eventType)) return null;
    else {
      List<Transition<E, SC, ?>> result = new LinkedList<>();
      for (Transition<?, SC, ?> transition : transitionMap.get(eventType)) {
        result.add((Transition<E, SC, ?>) transition);
      }
      return result;
    }
  }

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
   * [getContext description]
   * @return [description]
   */
  public SC getContext() {
    return context;
  }

  /**
   * [getBuildr description]
   * @return [description]
   */
  public <SMC> Buildr<S, SC, SMC, Object> getBuildr() {
    return new Buildr<>(this);
  }

  /**
   * [withContext description]
   * @param  newContext [description]
   * @return            [description]
   */
  public State<S, SC> withContext(SC newContext) {
    return new State<>(this.name, newContext, this.contextFactory, this.transitionMap, this.serializer, this.deserializer);
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
   * [deserializeContext description]
   * @param  contextBinary [description]
   * @return               [description]
   */
  public SC deserializeContext(byte[] contextBinary) {
    return deserializer.apply(contextBinary);
  }

  /**
   * [serializedContext description]
   * @return [description]
   */
  public byte[] serializedContext() {
    return serializer.apply(this.context);
  }

  /**
   * [appendTransition description]
   * @param  eventType  [description]
   * @param  transition [description]
   * @return            [description]
   */
  private <E> void appendTransition(Class<E> eventType, Transition<E, SC, ?> transition) {
    List<Transition<?, SC, ?>> transitions = transitionMap.get(eventType);
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
    public Buildr<S, SC, SMC, Object> perform(Action<E, SC, SMC> action) {
      Transition<E, SC, SMC> transition = new Transition<>(this.predicate, action);
      this.state.appendTransition(this.eventType, transition);
      return new Buildr<>(state);
    }
  }

  /**
   *
   */
  public static class Transition<E, SC, SMC> {
    Predicate<E, SC, SMC> predicate;
    Action<E, SC, SMC> action;

    public Transition(Predicate<E, SC, SMC> predicate, Action<E, SC, SMC> action) {
      this.predicate = predicate;
      this.action = action;
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
  public static interface Action<E, SC, SMC> {
    public TransitionAction apply(E event, StateMachineDef.Context<SC, SMC> context) throws Exception;
  }

}