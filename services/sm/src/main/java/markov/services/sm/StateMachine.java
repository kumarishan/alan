package markov.services.sm;

import java.util.*;
import java.io.Serializable;


interface Predicate<E, D, S> {
  public boolean apply(E event, Context<D, S> context);
}

interface Action<E, D, S> {
  public TransitionAction apply(E event, Context<D, S> context);
}

abstract class State<D> {
  private D data;
  public void setData(D data) {
    this.data = data;
  }
  public D getData() {
    return this.data;
  }
}

// interface Event extends Serializable {}

// immutable
class Context<D, S> {
  final D stateContext;
  final S stateMachineContext;

  Context(D d, S s) {
    this.stateContext = d;
    this.stateMachineContext = s;
  }
}

interface TransitionAction {}

class GoTo implements TransitionAction {
  Class<? extends State> stateType;

  public GoTo(Class<? extends State> stateType) {
    this.stateType = stateType;
  }
}

class Transition<E, D, S> {
  Predicate<E, D, S> predicate;
  Action<E, D, S> action;

  public Transition(Predicate<E, D, S> predicate, Action<E, D, S> action) {
    this.predicate = predicate;
    this.action = action;
  }

  public boolean check(E event, Context<D, S> context) {
    return (this.predicate == null ? true : this.predicate.apply(event, context));
  }
}

class TransitionBuildr<S, D, E> {
  StateMachine<S> fsm;
  State<D> state;
  Class<E> eventType;
  Predicate<E, D, S> predicate;

  // Immutable buildr
  public TransitionBuildr(StateMachine<S> fsm, State<D> state, Class<E> eventType, Predicate<E, D, S> predicate) {
    this.fsm = fsm;
    this.state = state;
    this.eventType = eventType;
    this.predicate = predicate;
  }

  public TransitionBuildr(StateMachine<S> fsm, State<D> state, Class<E> eventType) {
    this(fsm, state, eventType, null);
  }

  public TransitionBuildr(StateMachine<S> fsm, State<D> state) {
    this(fsm, state, null);
  }

  public TransitionBuildr(StateMachine<S> fsm) {
    this(fsm, null);
  }

  /**
   * [forState description]
   * @param  state [description]
   * @return       [description]
   */
  public <D1> TransitionBuildr<S, D1, E> forState(State<D1> state) {
    return new TransitionBuildr<S, D1, E>(this.fsm, state);
  }

  /**
   * [onEvent description]
   * @param  eventType [description]
   * @return           [description]
   */
  public <E1> TransitionBuildr<S, D, E1> onEvent(Class<E1> eventType) {
    return new TransitionBuildr<S, D, E1>(this.fsm, state, eventType);
  }

  /**
   * [onEvent description]
   * @param  eventType [description]
   * @param  predicate [description]
   * @return           [description]
   */
  public <E1> TransitionBuildr<S, D, E1> onEvent(Class<E1> eventType, Predicate<E1, D, S> predicate) {
    return new TransitionBuildr<S, D, E1>(this.fsm, state, eventType, predicate);
  }

  /**
   * [perform description]
   * @param  action [description]
   * @return        [description]
   */
  public TransitionBuildr<S, D, Object> perform(Action<E, D, S> action) {
    this.fsm.addTransition(eventType, new Transition(predicate, action));
    return new TransitionBuildr<S, D, Object>(this.fsm, this.state);
  }

}




/**
 * Sate Machine
 */
public abstract class StateMachine<S> {

  private StateMachineBehavior behavior;
  private Map<Class<?>, List<Transition>> transitions;
  private Map<Class<?>, ExecutionIdFactory<?>> executionIdFactories;
  private TransitionBuildr<S, Object, Object> buildr;
  {
    transitions = new HashMap<Class<?>, List<Transition>>();
    executionIdFactories = new HashMap<Class<?>, ExecutionIdFactory<?>>();
    buildr = new TransitionBuildr(this);
  }

  public void setBehavior(StateMachineBehavior behavior) {
    this.behavior = behavior;
  }

  public StateMachineBehavior getBehavior() {
    return this.behavior;
  }

  public <E> ExecutionId createExecutionId(E event) {
    ExecutionIdFactory<E> factory = (ExecutionIdFactory<E>) executionIdFactories.get(event.getClass());
    if (factory == null)
      throw new NullPointerException("No factory method specified to create execution id for the event " + event.getClass().getName());
    return factory.apply(event);
  }

  protected <E> void executionIdFor(Class<E> eventType, ExecutionIdFactory<E> factory) {
    executionIdFactories.put(eventType, factory);
  }

  /**
   * [when description]
   * @param  buildr.forState(state [description]
   * @return                       [description]
   */
  public <D> TransitionBuildr<S, D, Object> when(State<D> state) { return buildr.forState(state); }

  public <E, D> void addTransition(Class<E> eventType, Transition<E, D, S> transition) {
    if (eventType == null)
      throw new NullPointerException("Event type is null");
    List<Transition> trns = transitions.get(eventType);
    if (trns == null) {
      trns = new LinkedList();
      transitions.put(eventType, trns);
    }
    trns.add(transition);
  }

  /**
   * [goTo description]
   * @param  stateType [description]
   * @return           [description]
   */
  protected TransitionAction goTo(Class<? extends State> stateType) {
    return new GoTo(stateType);
  }

  /**
   * [receiveEvent description]
   * @param  event [description]
   * @return       [description]
   */
  public <E> void receiveEvent(E event) {
    List<Transition> candidates = new LinkedList<Transition>();
    for (Transition transition : transitions.get(event.getClass())) {
      if (transition.check(event, null)) /////////////
        candidates.add(transition);
    }

    // candidates = behavior.selectTransitions(candidates);
    // for ()
  }

  /**
   * [toString description]
   * @return [description]
   */
  public String toString() {
    StringBuilder buildr = new StringBuilder();
    for (Class<?> eventType : transitions.keySet()) {
      buildr.append(eventType.getName() + " -> " + transitions.get(eventType).size() + " transitions \n");
    }
    return buildr.toString();
  }

}

interface ExecutionId {};

interface ExecutionIdFactory<E> {
  public ExecutionId apply(E event);
}

class ExecutionStageStore {
  public ExecutionStage forId(ExecutionId executionId) {
    return new ExecutionStage(executionId);
  }
}

/**
 * State machine dependent
 */
class ExecutionStage {
  ExecutionId instanceId;

  public ExecutionStage(ExecutionId instanceId) {
    this.instanceId = instanceId;
  }
}

/**
 * State Machine Behavior
 */
abstract class StateMachineBehavior {

}

class StandardBehavior extends StateMachineBehavior {

}