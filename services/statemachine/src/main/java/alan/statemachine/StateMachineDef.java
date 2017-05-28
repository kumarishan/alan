package alan.statemachine;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.io.Serializable;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.CompletableFuture;

import alan.core.ExecutionId;
import alan.core.MachineDef;
import alan.core.Machine;
import alan.core.Schema;
import alan.core.TapeLog;


/**
 * Sate Machine
 * @Immutable
 */
public abstract class StateMachineDef<S, SMC> implements MachineDef<S, SMC, StateMachineTape> {
  private static Schema<StateMachineTape> schema = new StateMachineSchema();

  private String name;
  private S startState;
  private RuntimeExceptionHandler<S, SMC> runtimeExceptionHandler;
  private Supplier<SMC> stateMachineContextFactory;
  private Supplier<ExecutorService> executorServiceFactory;
  private Class<SMC> stateMachineContextType;

  private final Map<Class<?>, Function<?, String>> executionIdFactories;
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
    runtimeExceptionHandler = (S state, Object event, StateMachineDef.Context<S, ?, SMC> context, Throwable exception) -> stop(exception);
  }

  /**
   * [getId description]
   * @return [description]
   */
  public String getName() {
    return this.name;
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
  public ExecutionId getExecutionId(Object event) {
    @SuppressWarnings("unchecked")
    Function<Object, String> factory = (Function<Object, String>) executionIdFactories.get(event.getClass());
    if (factory == null)
      throw new NullPointerException("No factory method specified to create execution id for the event " + event.getClass().getName());
    return new ExecutionId(name, factory.apply(event));
  }

  public Schema<StateMachineTape> getSchema() {
    return schema;
  }

  /**
   * [createMachine description]
   * @param  id       [description]
   * @param  tapeLog  [description]
   * @param  executor [description]
   * @return          [description]
   */
  public Machine createMachine(ExecutionId id, TapeLog<StateMachineTape> tapeLog, ExecutorService executor) {
    return new StateMachine<S, SMC>(id, this, tapeLog, executor);
  }

  /**
   * [getStartState description]
   * @return [description]
   */
  public S getStartState() {
    return startState;
  }

  /**
   * [getStartStateContext description]
   * @return [description]
   */
  public Object getStartStateContext() {
    @SuppressWarnings("unchecked")
    State<S, Object> state = (State<S, Object>)states.get(getStartState());
    return state.createContext();
  }

  /**
   *
   */
  public Supplier<?> getStateContextFactory(S name) {
    return states.get(name).getContextFactory();
  }

  /**
   * [getStateMachineContextFactory description]
   * @return [description]
   */
  public Supplier<SMC> getStateMachineContextFactory() {
    return stateMachineContextFactory;
  }

  /**
   * [getSinkState description]
   * @param  state [description]
   * @return       [description]
   */
  @SuppressWarnings("unchecked")
  public SinkState<S, SMC, Object> getSinkState(S state) {
    return (SinkState<S, SMC, Object>)sinkStates.get(state);
  }

  /**
   * [isSuccessState description]
   * @param  name [description]
   * @return      [description]
   */
  public final boolean isSuccessState(S name) {
    if (sinkStates.containsKey(name))
      return sinkStates.get(name).isSuccess();
    else
      return false;
  }

  /**
   * [isFailureState description]
   * @param  name [description]
   * @return      [description]
   */
  public final boolean isFailureState(S name) {
    if (sinkStates.containsKey(name))
      return !sinkStates.get(name).isSuccess();
    else
      return false;
  }

  /**
   * [getTransition description]
   * @param  stateName [description]
   * @param  event     [description]
   * @param  context   [description]
   * @return           [description]
   */
  public <E, SC> State.Transition<S, E, SC, SMC> getTransition(E event, StateMachineDef.Context<S, SC, SMC> context) {
    @SuppressWarnings("unchecked")
    State<S, SC> state = (State<S, SC>) states.get(context.state);
    @SuppressWarnings("unchecked")
    List<State.Transition<S, E, SC, SMC>> transitions = state.getTransitions((Class<E>)event.getClass());
    State.Transition<S, E, SC, SMC> transition = null;

    for (State.Transition<S, E, SC, SMC> trn : transitions) {
      if (trn.check(event, context)) {
        transition = trn;
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
  protected void name(String name) {
    this.name = name;
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
  protected <E> void executionIdFor(Class<E> eventType, Function<E, String> factory) {
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
    if (this.name == null)
      throw new IllegalArgumentException("State Machine name not specified");
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
  public byte[] serializeStateContext(Class<?> clazz, Object context) {
    return ((ContextSerializer<Object>)serializers.get(clazz)).apply(context);
  }

  @SuppressWarnings("unchecked")
  public byte[] serializeStateContext(Object context) {
    return ((ContextSerializer<Object>)serializers.get(context.getClass())).apply(context);
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
   * [serializeSinkStateResult description]
   * @param  clazz  [description]
   * @param  result [description]
   * @return        [description]
   */
  @SuppressWarnings("unchecked")
  public byte[] serializeSinkStateResult(Object result) {
    return ((ContextSerializer<Object>)serializers.get(result.getClass())).apply(result);
  }

  /**
   * [serializeStateMachineContext description]
   * @param  context [description]
   * @return         [description]
   */
  @SuppressWarnings("unchecked")
  public byte[] serializeStateMachineContext(SMC context) {
    return ((ContextSerializer<SMC>)serializers.get(context.getClass())).apply(context);
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
  @SuppressWarnings("unchecked")
  public SMC deserializeStateMachineContext(byte[] binary) {
    return (SMC)deserializers.get(stateMachineContextType).apply(binary);
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
   * [RENAME]
   */
  public final static class Context<S, SC, SMC> {
    private SC stateContext;
    private SMC stateMachineContext;
    private final ExecutorService executorService;
    private final S state;
    private final StateMachineDef<S, SMC> stateMachineDef;

    Context(S state, SC stateContext, SMC stateMachineContext, ExecutorService executorService, StateMachineDef<S, SMC> stateMachineDef) {
      this.state = state;
      this.stateContext = stateContext;
      this.stateMachineContext = stateMachineContext;
      this.executorService = executorService;
      this.stateMachineDef = stateMachineDef;
    }

    Context(S state, SC stateContext, SMC stateMachineContext, StateMachineDef<S, SMC> stateMachineDef) {
      this(state, stateContext, stateMachineContext, null, stateMachineDef);
    }

    /**
     * [getState description]
     * @return [description]
     */
    public S getState() {
      return state;
    }

    /**
     * [getStateContext description]
     * @return [description]
     */
    public SC getStateContext() {
      return stateContext;
    }

    /**
     * [getStateMachineContext description]
     * @return [description]
     */
    public SMC getStateMachineContext() {
      return stateMachineContext;
    }

    /**
     * [resetStateContext description]
     * @return [description]
     */
    @SuppressWarnings("unchecked")
    public SC resetStateContext() {
      stateContext = (SC)stateMachineDef.getStateContextFactory(state).get();
      return stateContext;
    }

    /**
     * [resetStateMachineContext description]
     * @return [description]
     */
    public SMC resetStateMachineContext() {
      stateMachineContext = stateMachineDef.getStateMachineContextFactory().get();
      return stateMachineContext;
    }

    /**
     * [setStateContext description]
     * @param context [description]
     */
    public void setStateContext(SC context) {
      stateContext = context;
    }

    /**
     * [setStateMachineContext description]
     * @param context [description]
     */
    public void setStateMachineContext(SMC context) {
      stateMachineContext = context;
    }

    /**
     * [withExecutorService description]
     * @param  service [description]
     * @return         [description]
     */
    public Context<S, SC, SMC> copy(ExecutorService service) {
      return new Context<>(state, stateContext, stateMachineContext, service, stateMachineDef);
    }
  }

}