package markov.services.sm;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.io.Serializable;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.CompletableFuture;


/**
 *
 */
@FunctionalInterface
interface ContextSerializer<SC> {
  public byte[] apply(SC context);
}

/**
 *
 */
@FunctionalInterface
interface ContextDeserializer<SC> {
  public SC apply(byte[] binary);
}

/**
 *
 */
@FunctionalInterface
interface RuntimeExceptionHandler<S, SMC> {
  public State.To<S, ?> handle(S state, Object event, StateMachineDef.Context<?, SMC> context, Throwable exception);
}

/**
 * Sate Machine
 * @Immutable
 */
public abstract class StateMachineDef<S, SMC> {

  private String id;
  private S startState;
  private RuntimeExceptionHandler<S, SMC> runtimeExceptionHandler;
  private Supplier<SMC> stateMachineContextFactory;
  private Supplier<ExecutorService> executorServiceFactory;
  private ContextSerializer<SMC> stateMachineContextSerializer;
  private ContextDeserializer<SMC> stateMachineContextDeserializer;
  private Class<SMC> stateMachineContextType;

  private final Map<Class<?>, Function<?, ExecutionId>> executionIdFactories;
  private final Map<S, State<S, ?>> states;
  private final Map<S, SinkState<S, SMC, ?>> sinkStates;
  private final Map<String, S> nameToState;
  private final Set<Class<?>> eventTypes;
  private final Map<Class<?>, ContextSerializer<?>> serializers;
  private final Map<Class<?>, ContextDeserializer<?>> deserializers;
  {
    executionIdFactories = new HashMap<>();
    states               = new HashMap<>();
    nameToState          = new HashMap<>();
    sinkStates           = new HashMap<>();
    eventTypes           = new HashSet<>();
    serializers          = new HashMap<>();
    deserializers        = new HashMap<>();

    // defaults
    // default serde using jackson...
    executorServiceFactory = () -> ForkJoinPool.commonPool();
    runtimeExceptionHandler = (S state, Object event, StateMachineDef.Context<?, SMC> context, Throwable exception) -> stop(exception);
  }

  /**
   * [getId description]
   * @return [description]
   */
  public String getId() {
    return this.id;
  }

  /**
   *
   */
  public Set<Class<?>> getEventTypes() {
    Set<Class<?>> eventTypes = new HashSet<>();
    for (Map.Entry<S, State<S, ?>> entry : states.entrySet()) {
      eventTypes.addAll(entry.getValue().getEventTypes());
    }
    return eventTypes;
  }

  /**
   * [createExecutionId description]
   * @param  event [description]
   * @return       [description]
   */
  public <E> ExecutionId getExecutionId(E event) {
    @SuppressWarnings("unchecked")
    Function<E, ExecutionId> factory = (Function<E, ExecutionId>) executionIdFactories.get(event.getClass());
    if (factory == null)
      throw new NullPointerException("No factory method specified to create execution id for the event " + event.getClass().getName());
    return factory.apply(event);
  }

  /**
   * [getStartState description]
   * @return [description]
   */
  public S getStartState() {
    return startState;
  }

  /**
   * [getStartContext description]
   * @return [description]
   */
  public StateMachineDef.Context<?, SMC> getStartContext() {
    return _getStartContext();
  }

  // wild card capture
  private <SC> StateMachineDef.Context<SC, SMC> _getStartContext() {
    @SuppressWarnings("unchecked")
    State<S, SC> state = (State<S, SC>)states.get(getStartState());
    SC stateContext = state.createContext();
    SMC stateMachineContext = stateMachineContextFactory.get();
    return new StateMachineDef.Context<>(stateContext, stateMachineContext, null);
  }

  /**
   *
   */
  public Supplier<?> getContextFactory(S name) {
    return states.get(name).getContextFactory();
  }

  /**
   * [isSuccessState description]
   * @param  name [description]
   * @return      [description]
   */
  public final boolean isSuccessState(S name) {
    return sinkStates.get(name).isSuccess();
  }

  /**
   * [isFailureState description]
   * @param  name [description]
   * @return      [description]
   */
  public final boolean isFailureState(S name) {
    return !isSuccessState(name);
  }

  /**
   * [getTransition description]
   * @param  stateName [description]
   * @param  event     [description]
   * @param  context   [description]
   * @return           [description]
   */
  public <E, SC> State.Transition<S, E, SC, SMC> getTransition(S stateName, E event, StateMachineDef.Context<SC, SMC> context) {
    @SuppressWarnings("unchecked")
    State<S, SC> state = (State<S, SC>) states.get(stateName);
    @SuppressWarnings("unchecked")
    List<State.Transition<S, E, SC, SMC>> transitions = state.getTransitions((Class<E>)event.getClass());
    State.Transition<S, E, SC, SMC> transition = null;

    for (State.Transition<S, E, SC, SMC> trn : transitions) {
      if (trn.check(event, context)) {
        trn = transition;
        break;
      }
    }
    return transition;
  }

  /**
   * [getExecutorService description]
   * @return [description]
   */
  public ExecutorService createExecutorService() {
    return this.executorServiceFactory.get();
  }

  /**
   * [getRuntimeExceptionHandler description]
   * @return [description]
   */
  public RuntimeExceptionHandler<S, SMC> getRuntimeExceptionHandler() {
    return this.runtimeExceptionHandler;
  }

  /**
   *
   */
  public State<S, ?> getState(S name) {
    return states.get(name);
  }

  /**
   * [validateContextType description]
   * @param  name    [description]
   * @param  context [description]
   * @return         [description]
   */
  public final <SC1> boolean validateContextType(S name, SC1 context) {
    return states.get(name).validateContextType(context);
  }

  //////////////////////////////////// Definition helper methods ///////////////////////////////////////////////////

  /**
   * [id description]
   * @param id [description]
   */
  protected void id(String id) {
    this.id = id;
  }

  /**
   * [executorServiceFactory description]
   * @param factory [description]
   */
  protected void executorServiceFactory(Supplier<ExecutorService> factory) {
    this.executorServiceFactory = factory;
  }

  /**
   * [executionIdFor description]
   * @param  eventType [description]
   * @param  factory   [description]
   * @return           [description]
   */
  protected <E> void executionIdFor(Class<E> eventType, Function<E, ExecutionId> factory) {
    executionIdFactories.put(eventType, factory);
  }

  /**
   * [when description]
   * @param  buildr.forState(state [description]
   * @return                       [description]
   */
  protected <SC> State.Buildr<S, SC, SMC, Object> state(S name, Class<SC> contextType, Supplier<SC> contextFactory) {
    if (states.containsKey(name))
      throw new IllegalArgumentException("State " + name + " already defined once, use the same instance using state(name)");

    if (sinkStates.containsKey(name))
      throw new IllegalArgumentException("State " + name + " is already defined as a sink state");

    State<S, SC> state = new State<S, SC>(name, contextType, contextFactory);
    states.put(name, state);
    addToNameToState(name);
    return state.getBuildr();
  }

  /**
   * [start description]
   * @param  name [description]
   * @return      [description]
   */
  protected void start(S name) {
    if (!states.containsKey(name))
      throw new IllegalArgumentException("State " + name + " should be first defined before marking as start state");
    this.startState = name;
  }

  /**
   * [success description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <SC> void success(S name, Class<SC> resultType, Function<SMC, SC> action) {
    if (sinkStates.containsKey(name) || states.containsKey(name))
      throw new IllegalArgumentException("State " + name + " is already defined, cannot use as new success state");
    addToNameToState(name);
    sinkStates.put(name, new SinkState<>(name, resultType, action, true));
  }

  /**
   * [success description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <SC> void successAsync(S name, Class<SC> resultType, BiFunction<SMC, ExecutorService, CompletableFuture<SC>> action) {
    if (sinkStates.containsKey(name) || states.containsKey(name))
      throw new IllegalArgumentException("State " + name + " is already defined, cannot use as new success state");
    addToNameToState(name);
    sinkStates.put(name, new SinkState<>(name, resultType, action, true));
  }

  /**
   * [failure description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <EC> void failure(S name, Class<EC> resultType, Function<SMC, EC> action) {
    if (states.containsKey(name))
      throw new IllegalArgumentException("State " + name + " is already defined, cannot use as new failure state");
    addToNameToState(name);
    sinkStates.put(name, new SinkState<>(name, resultType, action, false));
  }

  /**
   * [failure description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <EC> void failureAsync(S name, Class<EC> resultType, BiFunction<SMC, ExecutorService, CompletableFuture<EC>> action) {
    if (states.containsKey(name))
      throw new IllegalArgumentException("State " + name + " is already defined, cannot use as new failure state");
    addToNameToState(name);
    sinkStates.put(name, new SinkState<>(name, resultType, action, false));
  }

  /**
   * [runtimeExceptionHandler description]
   * @param handler [description]
   */
  protected void runtimeExceptionHandler(RuntimeExceptionHandler<S, SMC> handler) {
    this.runtimeExceptionHandler = handler;
  }

  /**
   * [stateMachineContext description]
   * @param factory      [description]
   * @param serializer   [description]
   * @param deserializer [description]
   */
  public void stateMachineContextFactory(Class<SMC> stateMachineContextType, Supplier<SMC> factory) {
    this.stateMachineContextType = stateMachineContextType;
    this.stateMachineContextFactory = factory;
  }

  /**
   * [goTo description]
   * @param  stateType [description]
   * @return           [description]
   */
  protected final State.To<S, ?> goTo(S state) {
    return new State.To<>(state);
  }

  /**
   * [failTo description]
   * @param  state [description]
   * @return       [description]
   */
  protected final State.To<S, ?> failTo(S state) {
    return new State.To<>(state);
  }

  /**
   *
   */
  protected final State.To<S, ?> stop() {
    return new State.Stop<>();
  }

  /**
   *
   */
  protected final State.To<S, ?> stop(Throwable exception) {
    return new State.Stop<>(exception);
  }

  /**
   * [serde description]
   * @param  clazz        [description]
   * @param  serializer   [description]
   * @param  deserializer [description]
   * @return              [description]
   */
  protected final <T> void serde(Class<T> clazz, ContextSerializer<T> serializer, ContextDeserializer<T> deserializer) {
    serializers.put(clazz, serializer);
    deserializers.put(clazz, deserializer);
  }

  /**
   * [verify description]
   */
  protected final void verify() {
    if (this.id == null)
      throw new IllegalArgumentException("State Machine id not specified");
    checkStates();
    checkStateMachineContext();
    checkSinkStates();
  }

  /**
   * [checkStates description]
   */
  private void checkStates() {
    for (S name : states.keySet()) {
      Class<?> contextType = states.get(name).getContextType();
      if (!(serializers.containsKey(contextType) && deserializers.containsKey(contextType)))
        throw new IllegalArgumentException("Serializers not defined for " + contextType.getName());
      for (Class<?> eventType : states.get(name).getEventTypes()) {
        if (!executionIdFactories.containsKey(eventType))
          throw new IllegalArgumentException("No execution id factory for event type " + eventType.getName());
      }
    }
  }

  private void checkStateMachineContext() {
    if (stateMachineContextFactory == null)
      throw new IllegalArgumentException("State Machine context factory not defined");
    if (!(serializers.containsKey(stateMachineContextType) && deserializers.containsKey(stateMachineContextType)))
      throw new IllegalArgumentException("Serializers not defined for state machine context");
  }

  private void checkSinkStates() {
    for (S name : sinkStates.keySet()) {
      Class<?> resultType = sinkStates.get(name).getResultType();
      if (!(serializers.containsKey(resultType) && deserializers.containsKey(resultType)))
        throw new IllegalArgumentException("Serializers not defined for sink state result type " + resultType.getName());
    }
  }

  /**
   * [addToNameToState description]
   * @param name [description]
   */
  private void addToNameToState(S name) {
    nameToState.put(nameStrFor(name), name);
  }

  ///////////// Context Serialization Deserialization ///////////

  /**
   * [stateNameFor description]
   * @param  stateNameStr [description]
   * @return              [description]
   */
  public S stateNameFor(String stateNameStr) {
    return nameToState.get(stateNameStr);
  }

  /**
   * [nameStrFor description]
   * @param  name [description]
   * @return      [description]
   */
  public String nameStrFor(S name) {
    if (name.getClass().isEnum())
      return name.toString();
    else
      return name.getClass().getName();
  }

  /**
   * [serializeStateContext description]
   * @param  clazz   [description]
   * @param  context [description]
   * @return         [description]
   */
  @SuppressWarnings("unchecked")
  public <SC> byte[] serializeStateContext(Class<SC> clazz, SC context) {
    return ((ContextSerializer<SC>)serializers.get(clazz)).apply(context);
  }

  /**
   * [serializeStateContext description]
   * @param  state   [description]
   * @param  context [description]
   * @return         [description]
   */
  @SuppressWarnings("unchecked")
  public <SC> byte[] serializeStateContext(S state, SC context) {
    return serializeStateContext((Class<SC>)states.get(state).getContextType(), context);
  }

  /**
   * [serializeStateMachineContext description]
   * @param  context [description]
   * @return         [description]
   */
  public byte[] serializeStateMachineContext(SMC context) {
    return stateMachineContextSerializer.apply(context);
  }

  /**
   * [deserializeStateContext description]
   * @param  name          [description]
   * @param  contextBinary [description]
   * @return               [description]
   */
  public <SC> SC deserializeStateContext(S name, byte[] contextBinary) {
    @SuppressWarnings("unchecked")
    State<S, SC> state = (State<S, SC>) states.get(name);
    if (state == null)
      throw new IllegalArgumentException("Un registered state(" + name + "), it should be first defined using state(...)");
    @SuppressWarnings("unchecked")
    SC context = ((ContextDeserializer<SC>)deserializers.get(state.getContextType())).apply(contextBinary);
    return context;
  }

  /**
   * [deserializeStateMachineContext description]
   * @param  binary [description]
   * @return        [description]
   */
  public SMC deserializeStateMachineContext(byte[] binary) {
    return stateMachineContextDeserializer.apply(binary);
  }

  /**
   * [toString description]
   * @return [description]
   */
  public String toString() {
    StringBuilder buildr = new StringBuilder();
    for (S name : states.keySet()) {
      State<S, ?> state = states.get(name);
      buildr.append("State(" + state.getName() + ") \n");
      for (Class<?> eventType : state.getHandledEventTypes()) {
        buildr.append("    " + eventType.getName() + " -> " + state.getTransitions(eventType).size() + " transition \n");
      }
      buildr.append("\n");
    }
    return buildr.toString();
  }

  /**
   *
   */
  final static class Context<SC, SMC> {
    final SC stateContext;
    final SMC stateMachineContext;
    final ExecutorService executorService;

    Context(SC stateContext, SMC stateMachineContext, ExecutorService executorService) {
      this.stateContext = stateContext;
      this.stateMachineContext = stateMachineContext;
      this.executorService = executorService;
    }

    Context(SC stateContext, SMC stateMachineContext) {
      this(stateContext, stateMachineContext, null);
    }

    /**
     * [withExecutorService description]
     * @param  service [description]
     * @return         [description]
     */
    public Context<SC, SMC> copy(ExecutorService service) {
      return new Context<>(stateContext, stateMachineContext, service);
    }
  }

}