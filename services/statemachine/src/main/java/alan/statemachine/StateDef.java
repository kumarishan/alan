package alan.statemachine;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;


/**
 *
 */
class StateDef<S, SC, SMC> {

  private final S state;
  private final Class<SC> contextType;
  private final Supplier<SC> contextFactory;
  private final Map<Class<?>, List<Transition<S, ?, SC, ?>>> transitionMap;
  private final Set<Class<?>> eventTypes;
  {
    eventTypes = new HashSet<>();
    transitionMap = new HashMap<>();
  }

  /**
   * [StateDef description]
   * @param  state          [description]
   * @param  contextType    [description]
   * @param  contextFactory [description]
   * @return                [description]
   */
  public StateDef(S state, Class<SC> contextType, Supplier<SC> contextFactory) {
    assert(state != null && contextType != null && contextFactory != null);
    this.state = state;
    this.contextType = contextType;
    this.contextFactory = contextFactory;
  }

  /**
   * [StateDef description]
   * @param  state [description]
   * @return       [description]
   */
  public StateDef(S state) {
    this.state = state;
    this.contextType = null;
    this.contextFactory = null;
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
   * [hasStateContext description]
   * @return [description]
   */
  boolean hasStateContext() {
    return contextType != null;
  }

  /**
   * [validateContextType description]
   * @param  context [description]
   * @return         [description]
   */
  boolean validateContextType(Object context) {
    if (contextType == null) return false;
    return contextType.isInstance(context);
  }

  /**
   * [createContext description]
   * @return [description]
   */
  SC createContext() {
    if (contextFactory == null) return null;
    else return contextFactory.get();
  }

  /**
   * [getContextType description]
   * @return [description]
   */
  Class<SC> getContextType() {
    return contextType;
  }

  /**
   *
   */
  Set<Class<?>> getEventTypes() {
    return new HashSet<>(this.eventTypes);
  }

  /**s
   *
   */
  Set<Class<?>> getHandledEventTypes() {
    return transitionMap.keySet();
  }

  /**
   * [getName description]
   * @return [description]
   */
  S getName() {
    return state;
  }

  /**
   * [onEvent description]
   * @param  eventType [description]
   * @return           [description]
   */
  public <E> Transition.Buildr<S, SC, SMC, E> onEvent(Class<E> eventType) {
    return new Transition.Buildr<>(this, eventType);
  }

  /**
   * [handlesEvent description]
   * @param  eventType [description]
   * @return           [description]
   */
  <E> boolean handlesEvent(Class<E> eventType) {
    return transitionMap.containsKey(eventType);
  }

  /**
   * [appendTransition description]
   * @param  eventType  [description]
   * @param  transition [description]
   * @return            [description]
   */
  <E> void appendTransition(Class<E> eventType, Transition<S, E, SC, ?> transition) {
    List<Transition<S, ?, SC, ?>> transitions = transitionMap.get(eventType);
    if (transitions == null) {
      transitions = new LinkedList<>();
      transitionMap.put(eventType, transitions);
    }
    eventTypes.add(eventType);
    transitions.add(transition);
  }

}