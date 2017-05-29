package alan.statemachine;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.io.Serializable;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.CompletableFuture;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.io.Input;

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

  protected class State<SC> extends StateDef<S, SC, SMC> {
    public State(S state, Class<SC> contextType, Supplier<SC> contextFactory) {
      super(state, contextType, contextFactory);
    }

    public State(S state) {
      super(state);
    }
  }

  private String name;
  private S startState;
  private RuntimeExceptionHandler<S, SMC> runtimeExceptionHandler;
  private Supplier<SMC> stateMachineContextFactory;
  private Supplier<ExecutorService> executorServiceFactory;
  private Class<SMC> stateMachineContextType;

  private final Kryo kryo;
  private final Map<Class<?>, Function<?, String>> executionIdFactories;
  private final Map<S, StateDef<S, ?, SMC>> states;
  private final Map<S, SinkStateDef<S, SMC, ?>> sinkStates;
  private final Map<String, S> nameToState;
  {
    kryo                 = new Kryo();
    executionIdFactories = new HashMap<>();
    states               = new HashMap<>();
    nameToState          = new HashMap<>();
    sinkStates           = new HashMap<>();

    // defaults
    // default serde using jackson...
    executorServiceFactory = () -> ForkJoinPool.commonPool();
    runtimeExceptionHandler = (S state, Object event, StateMachineActionContext<S, ?, SMC> context, Throwable exception) -> stop(exception);
  }

  /**
   * Returns the name of this StateMachine
   * @return the name of this StateMachine
   */
  public String getName() {
    return name;
  }

  /**
   * Returns StateMachineSchema
   * @return StateMachineSchema
   */
  public Schema<StateMachineTape> getSchema() {
    return schema;
  }

  /**
   * Return Event types this StateMachine receives
   * @return  eventy types this StateMachine receives
   */
  public Set<Class<?>> getEventTypes() {
    Set<Class<?>> eventTypes = new HashSet<>();
    for (Map.Entry<S, StateDef<S, ?, SMC>> entry : states.entrySet()) {
      eventTypes.addAll(entry.getValue().getEventTypes());
    }
    return eventTypes;
  }

  /**
   * Returns the execution id for the event
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

  /**
   * Creates a new StateMachine instance for this definition and with the given Execution Id and Tape Log.
   * @param  id       the Execution Id
   * @param  tapeLog  the Tape Log for StateMachineTape
   * @param  executor the ExecutorService
   * @return          A new StateMachine instance for this definition and with the given Execution Id and Tape Log
   */
  public Machine createMachine(ExecutionId id, TapeLog<StateMachineTape> tapeLog, ExecutorService executor) {
    return new StateMachine<S, SMC>(id, this, tapeLog, executor);
  }

  /**
   * Returns a new instance of configured executor service.
   * @return a new instance of configured executor service
   */
  public ExecutorService createExecutorService() {
    return this.executorServiceFactory.get();
  }

    /**
   * [toString description]
   * @return [description]
   */
  public String toString() {
    StringBuilder buildr = new StringBuilder();
    buildr.append("StateMachineDef(" + name + "): ");
    for (S state : states.keySet()) {
      StateDef<S, ?, SMC> stateDef = states.get(state);
      buildr.append("State(" + stateDef.getName() + ") \n");
      for (Class<?> eventType : stateDef.getHandledEventTypes()) {
        buildr.append("    " + eventType.getName() + " -> " + stateDef.getTransitions(eventType).size() + " transition \n");
      }
      buildr.append("\n");
    }
    return buildr.toString();
  }

  /////////////////////////////////////////////////////////////////////

  /**
   * Returns the first transition for the event in context.state that satisfies the predicate (if any).
   * @param  event     [description]
   * @param  context   [description]
   * @return           [description]
   */
  <E, SC> Transition<S, E, SC, SMC> getTransition(E event, StateMachineActionContext<S, SC, SMC> context) {
    @SuppressWarnings("unchecked")
    StateDef<S, SC, SMC> stateDef = (StateDef<S, SC, SMC>) states.get(context.getState());
    @SuppressWarnings("unchecked")
    List<Transition<S, E, SC, SMC>> transitions = stateDef.getTransitions((Class<E>)event.getClass());
    Transition<S, E, SC, SMC> transition = null;

    for (Transition<S, E, SC, SMC> trn : transitions) {
      if (trn.check(event, context)) {
        transition = trn;
        break;
      }
    }
    return transition;
  }

  /**
   * Returns the start state.
   * @return the start state
   */
  S getStartState() {
    return startState;
  }

  /**
   * [getStartStateContext description]
   * @return [description]
   */
  Object getStartStateContext() {
    @SuppressWarnings("unchecked")
    StateDef<S, Object, SMC> stateDef = (StateDef<S, Object, SMC>)states.get(getStartState());
    return stateDef.createContext();
  }

  /**
   * [hasStateContext description]
   * @param  state [description]
   * @return       [description]
   */
  boolean hasStateContext(S state) {
    return states.get(state).hasStateContext();
  }

  /**
   *
   */
  Object createStateContext(S state) {
    return states.get(state).createContext();
  }

  /**
   * [getStateMachineContextFactory description]
   * @return [description]
   */
  SMC createStateMachineContext() {
    return stateMachineContextFactory.get();
  }

  /**
   * Returns true if state is a success state.
   * @param  state state name to check
   * @return           true if state is a success state
   */
  final boolean isSuccessState(S state) {
    if (sinkStates.containsKey(state))
      return sinkStates.get(state).isSuccess();
    else
      return false;
  }

  /**
   * Returns true if state is a failure state.
   * @param  state state name to check
   * @return           true if state is a success state
   */
  final boolean isFailureState(S state) {
    if (sinkStates.containsKey(state))
      return !sinkStates.get(state).isSuccess();
    else
      return false;
  }

  /**
   * Returns the SinkState.
   * @param  state the state
   * @return       the SinkState
   */
  @SuppressWarnings("unchecked")
  SinkStateDef<S, SMC, Object> getSinkState(S state) {
    return (SinkStateDef<S, SMC, Object>)sinkStates.get(state);
  }

  /**
   * [getRuntimeExceptionHandler description]
   * @return [description]
   */
  RuntimeExceptionHandler<S, SMC> getRuntimeExceptionHandler() {
    return this.runtimeExceptionHandler;
  }

  /**
   * [validateContextType description]
   * @param  name    [description]
   * @param  context [description]
   * @return         [description]
   */
  <SC> boolean validateContextType(S state, SC context) {
    return states.get(state).validateContextType(context);
  }

  /**
   * Return the state for the given name string.
   * @param  stateNameStr [description]
   * @return              [description]
   */
  S stateFor(String name) {
    return nameToState.get(name);
  }

  /**
   * [nameFor description]
   * @param  name [description]
   * @return      [description]
   */
  String nameFor(S state) {
    if (state.getClass().isEnum())
      return state.toString();
    else
      return state.getClass().getName();
  }

  /**
   * [kryoSerialize description]
   * @param  object [description]
   * @return        [description]
   */
  private byte[] kryoSerialize(Object object) {
    try {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      Output output = new Output(stream);
      kryo.writeObject(output, object);
      output.close(); // Also calls output.flush()
      return stream.toByteArray();
    } catch (Throwable ex) {
      ex.printStackTrace();
      throw ex;
    }
  }

  /**
   * [kryoDeserialize description]
   * @param  binary [description]
   * @param  type   [description]
   * @return        [description]
   */
  private <T> T kryoDeserialize(byte[] binary, Class<T> type) {
    try {
    ByteArrayInputStream stream = new ByteArrayInputStream(binary);
    Input input = new Input(stream);
    return kryo.readObject(input, type);

    } catch (Throwable ex) {
      ex.printStackTrace();
      throw ex;
    }
  }

  /**
   * [serializeStateMachineContext description]
   * @param  context [description]
   * @return         [description]
   */
  byte[] serializeStateMachineContext(SMC context) {
    return kryoSerialize(context);
  }

  /**
   * [deserializeStateMachineContext description]
   * @param  binary [description]
   * @return        [description]
   */
  SMC deserializeStateMachineContext(byte[] binary) {
    return kryoDeserialize(binary, stateMachineContextType);
  }

  /**
   * [serializeStateContext description]
   * @param  context [description]
   * @return         [description]
   */
  @SuppressWarnings("unchecked")
  byte[] serializeStateContext(Object context) {
    if (context == null) return null;
    return kryoSerialize(context);
  }

  /**
   * [deserializeStateContext description]
   * @param  name          [description]
   * @param  contextBinary [description]
   * @return               [description]
   */
  Object deserializeStateContext(S state, byte[] binary) {
    if (binary == null) return null;
    @SuppressWarnings("unchecked")
    StateDef<S, Object, SMC> stateDef = (StateDef<S, Object, SMC>)states.get(state);
    if (stateDef == null)
      throw new IllegalArgumentException("Un registered state(" + state + "), it should be first defined using state(...)");
    return kryoDeserialize(binary, stateDef.getContextType());
  }

  /**
   * [serializeSinkStateResult description]
   * @param  clazz  [description]
   * @param  result [description]
   * @return        [description]
   */
  @SuppressWarnings("unchecked")
  byte[] serializeSinkStateResult(Object result) {
    if (result == null) return null;
    return kryoSerialize(result);
  }

  /**
   * [deserializeSinkStateResult description]
   * @param  sinkState [description]
   * @param  binary    [description]
   * @return           [description]
   */
  Object deserializeSinkStateResult(S sinkState, byte[] binary) {
    if (binary == null) return null;
    @SuppressWarnings("unchecked")
    SinkStateDef<S, SMC, Object> sinkStateDef = (SinkStateDef<S, SMC, Object>)sinkStates.get(sinkState);
    if (sinkStateDef == null)
      throw new IllegalArgumentException("Sink state " + sinkStates + " not present");
    return kryoDeserialize(binary, sinkStateDef.getResultType());
  }

  /**
   * [serializeEvent description]
   * @param  event [description]
   * @return       [description]
   */
  byte[] serializeEvent(Object event) {
    assert(event != null);
    return kryoSerialize(event);
  }

  /**
   * [deserializeEvent description]
   * @param  binary    [description]
   * @param  eventType [description]
   * @return           [description]
   */
  Object deserializeEvent(byte[] binary, Class<?> eventType) {
    return kryoDeserialize(binary, eventType);
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
  protected <SC> State<SC> defineState(S state, Class<SC> contextType, Supplier<SC> contextFactory) {
    if (states.containsKey(state))
      throw new IllegalArgumentException("State " + state + " already defined once, use the same instance using state(state)");

    if (sinkStates.containsKey(state))
      throw new IllegalArgumentException("State " + state + " is already defined as a sink state");

    State<SC> stateDef;
    if (contextType == null)
      stateDef = new State<SC>(state);
    else
      stateDef = new State<SC>(state, contextType, contextFactory);
    states.put(state, stateDef);
    addToNameToState(state);
    return stateDef;
  }

  /**
   * [state description]
   * @param  state [description]
   * @return       [description]
   */
  protected State<Object> defineState(S state) {
    return defineState(state, null, null);
  }

  /**
   * [start description]
   * @param  name [description]
   * @return      [description]
   */
  protected void start(S state) {
    if (!states.containsKey(state))
      throw new IllegalArgumentException("State " + state + " should be first defined before marking as start state");
    this.startState = state;
  }

  /**
   * [success description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <SC> void success(S state, Class<SC> resultType, Function<SMC, SC> action) {
    if (sinkStates.containsKey(state) || states.containsKey(state))
      throw new IllegalArgumentException("State " + state + " is already defined, cannot use as new success state");
    addToNameToState(state);
    sinkStates.put(state, new SinkStateDef<>(state, resultType, action, true));
  }

  /**
   * [success description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <SC> void successAsync(S state, Class<SC> resultType, BiFunction<SMC, ExecutorService, CompletableFuture<SC>> action) {
    if (sinkStates.containsKey(state) || states.containsKey(state))
      throw new IllegalArgumentException("State " + state + " is already defined, cannot use as new success state");
    addToNameToState(state);
    sinkStates.put(state, new SinkStateDef<>(state, resultType, action, true));
  }

  /**
   * [failure description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <EC> void failure(S state, Class<EC> resultType, Function<SMC, EC> action) {
    if (states.containsKey(state))
      throw new IllegalArgumentException("State " + state + " is already defined, cannot use as new failure state");
    addToNameToState(state);
    sinkStates.put(state, new SinkStateDef<>(state, resultType, action, false));
  }

  /**
   * [failure description]
   * @param  state       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <EC> void failureAsync(S state, Class<EC> resultType, BiFunction<SMC, ExecutorService, CompletableFuture<EC>> action) {
    if (states.containsKey(state))
      throw new IllegalArgumentException("State " + state + " is already defined, cannot use as new failure state");
    addToNameToState(state);
    sinkStates.put(state, new SinkStateDef<>(state, resultType, action, false));
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
  protected final Transition.To<S, ?> goTo(S state) {
    return new Transition.To<>(state);
  }

  /**
   * [failTo description]
   * @param  state [description]
   * @return       [description]
   */
  protected final Transition.To<S, ?> failTo(S state) {
    return new Transition.To<>(state);
  }

  /**
   *
   */
  protected final Transition.To<S, ?> stop() {
    return new Transition.Stop<>();
  }

  /**
   *
   */
  protected final Transition.To<S, ?> stop(Throwable exception) {
    return new Transition.Stop<>(exception);
  }

  /**
   * [kryo description]
   * @param  clazz [description]
   * @param  id    [description]
   * @return       [description]
   */
  protected final <T> void kryo(Class<T> clazz, int id) {
    kryo.register(clazz, id);
  }

  /**
   * [kryo description]
   * @param  clazz      [description]
   * @param  serializer [description]
   * @return            [description]
   */
  protected final <T> void kryo(Class<T> clazz, Serializer<T> serializer) {
    kryo.register(clazz, serializer);
  }

  /**
   * [verify description]
   */
  protected final void init() {
    if (this.name == null)
      throw new IllegalArgumentException("State Machine name not specified");
    checkStates();
    checkStateMachineContext();
  }

  /**
   * [checkStates description]
   */
  private void checkStates() {
    for (S state : states.keySet()) {
      for (Class<?> eventType : states.get(state).getEventTypes()) {
        if (!executionIdFactories.containsKey(eventType))
          throw new IllegalArgumentException("No execution id factory for event type " + eventType.getName());
      }
    }
  }

  /**
   * [checkStateMachineContext description]
   */
  private void checkStateMachineContext() {
    if (stateMachineContextFactory == null)
      throw new IllegalArgumentException("State Machine context factory not defined");
  }

  /**
   * [addToNameToState description]
   * @param name [description]
   */
  private void addToNameToState(S state) {
    nameToState.put(nameFor(state), state);
  }

}