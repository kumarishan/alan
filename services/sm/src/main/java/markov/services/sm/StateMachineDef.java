package markov.services.sm;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.io.Serializable;


/**
 *
 */
interface TransitionAction {}

/**
 *
 */
class GoTo<S> implements TransitionAction {
  S state;

  public GoTo(S state) {
    this.state = state;
  }
}

/**
 *
 */
class FailTo<S> implements TransitionAction {
  S state;

  public FailTo(S state) {
    this.state = state;
  }
}

/**
 *
 */
interface ContextFactory<SC> {
  public SC apply();
}

/**
 *
 */
interface ContextSerializer<SC> {
  public byte[] apply(SC context);
}

/**
 *
 */
interface ContextDeserializer<SC> {
  public SC apply(byte[] binary);
}

/**
 *
 */
interface UncaughtActionExceptionHandler<S, SMC> {
  public void handler(S state, Object event, Object stateContext, SMC stateMachineContext, Exception exception);
}

interface StateMachineExecutorServiceFactory {
  public ExecutorService create();
}

/**
 * Sate Machine
 */
public abstract class StateMachineDef<S, SMC> {

  private String id;
  private S startState;
  private UncaughtActionExceptionHandler<S, SMC> uncaughtActionExceptionHandler;
  private ContextFactory<SMC> stateMachineContextFactory;
  private ContextSerializer<SMC> stateMachineContextSerializer;
  private ContextDeserializer<SMC> stateMachineContextDeserializer;
  private StateMachineExecutorServiceFactory executorServiceFactory;

  private Map<Class<?>, ExecutionIdFactory<?>> executionIdFactories;
  private Map<S, State<S, ?>> states;
  private Map<String, S> nameToState;
  private Map<S, SuccessHandler<SMC, ?>> successStates;
  private Map<S, FailureHandler<SMC, ?>> failureStates;
  private Set<Class<?>> eventTypes;
  {
    executionIdFactories = new HashMap<>();
    states = new HashMap<>();
    nameToState = new HashMap<>();
    successStates = new HashMap<>();
    failureStates = new HashMap<>();
    eventTypes = new HashSet<>();
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
    ExecutionIdFactory<E> factory = (ExecutionIdFactory<E>) executionIdFactories.get(event.getClass());
    if (factory == null)
      throw new NullPointerException("No factory method specified to create execution id for the event " + event.getClass().getName());
    return factory.apply(event);
  }

  /**
   * [getStartState description]
   * @return [description]
   */
  public S getStartState() {
    return this.startState;
  }

  /**
   * [getExecutorService description]
   * @return [description]
   */
  public ExecutorService createExecutorService() {
    return this.executorServiceFactory.create();
  }

  /**
   * [getUncaughtActionExceptionHandler description]
   * @return [description]
   */
  public UncaughtActionExceptionHandler<S, SMC> getUncaughtActionExceptionHandler() {
    return this.uncaughtActionExceptionHandler;
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
  protected void executorServiceFactory(StateMachineExecutorServiceFactory factory) {
    this.executorServiceFactory = factory;
  }

  /**
   * [executionIdFor description]
   * @param  eventType [description]
   * @param  factory   [description]
   * @return           [description]
   */
  protected <E> void executionIdFor(Class<E> eventType, ExecutionIdFactory<E> factory) {
    executionIdFactories.put(eventType, factory);
  }

  /**
   * [when description]
   * @param  buildr.forState(state [description]
   * @return                       [description]
   */
  protected <SC> State.Buildr<S, SC, SMC, Object> state(S name, ContextFactory<SC> contextFactory,
                                                        ContextSerializer<SC> serializer,
                                                        ContextDeserializer<SC> deserializer) {
    if (states.containsKey(name))
      throw new IllegalArgumentException(name + " already defined once, use the same instance using state(name)");

    State<S, SC> state = new State<S, SC>(name, contextFactory, serializer, deserializer);
    states.put(name, state);
    nameToState.put(name.toString(), name);
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
  protected <SC> void success(S name, ContextSerializer<SC> serializer, SuccessHandler<SMC, SC> handler) {
    this.successStates.put(name, handler);
  }

  /**
   * [failure description]
   * @param  name       [description]
   * @param  serializer [description]
   * @param  handler    [description]
   * @return            [description]
   */
  protected <EC> void failure(S name, ContextSerializer<EC> serializer, FailureHandler<SMC, EC> handler) {
    this.failureStates.put(name, handler);
  }

  /**
   * [uncaughtActionExceptionHandler description]
   * @param handler [description]
   */
  protected void uncaughtActionExceptionHandler(UncaughtActionExceptionHandler<S, SMC> handler) {
    this.uncaughtActionExceptionHandler = handler;
  }

  /**
   * [stateMachineContext description]
   * @param factory      [description]
   * @param serializer   [description]
   * @param deserializer [description]
   */
  public void stateMachineContext(ContextFactory<SMC> factory, ContextSerializer<SMC> serializer, ContextDeserializer<SMC> deserializer) {
    this.stateMachineContextFactory = factory;
    this.stateMachineContextSerializer = serializer;
    this.stateMachineContextDeserializer = deserializer;
  }

  /**
   * [goTo description]
   * @param  stateType [description]
   * @return           [description]
   */
  protected GoTo<S> goTo(S state) {
    return new GoTo<>(state);
  }

  /**
   * [failTo description]
   * @param  state [description]
   * @return       [description]
   */
  protected FailTo<S> failTo(S state) {
    return new FailTo<>(state);
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
   * [deserializeContext description]
   * @param  stateName                 [description]
   * @param  stateContextBinary        [description]
   * @param  stateMachineContextBinary [description]
   * @return                           [description]
   */
  public <SC> Context<SC, SMC> deserializeContext(S stateName, byte[] stateContextBinary, byte[] stateMachineContextBinary) {
    @SuppressWarnings("unchecked")
    State<S, SC> state = (State<S, SC>) states.get(stateName);
    if (state == null)
      throw new IllegalArgumentException("Un registered state(" + stateName + "), it should be first defined using state(...)");
    SC context = state.deserializeContext(stateContextBinary);
    return new Context<>(context, null, null);
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
  }

  /**
   *
   */
  static interface SuccessHandler<SMC, SC> {
    public SC handler(SMC context);
  }

  /**
   *
   */
  static interface FailureHandler<SMC, EC> {
    public EC handler(SMC context);
  }

}