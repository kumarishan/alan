package markov.services.sm;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Dispatcher
 */
class Dispatcher {
  private ThreadFactory threadFactory; // = new CustomThreadFactory();
  private ExecutorService executorService;
  private Subscribers subscribers;
  {
    subscribers = new Subscribers();
  }

  public Dispatcher(ThreadFactory threadFactory, ExecutorService executorService) {
    this.threadFactory = threadFactory;
    this.executorService = executorService;
  }

  /**
   * [register description]
   * @param stateMachineDef [description]
   * @param executor        [description]
   */
  public void register(StateMachineDef<?, ?> stateMachineDef, StateMachineExecutor executor) {
    this.subscribers.add(new StateMachineSubscriber(stateMachineDef, executor));
  }

  /**
   * [dispatch description]
   * @param  event [description]
   * @return       [description]
   */
  DispatchResult dispatch(Object event) {
    Set<Subscriber> subscribers = this.subscribers.get(event.getClass());
    DispatchResult result = new DispatchResult();

    for (Subscriber subscriber : subscribers) {
      if (subscriber.isTerminated()) {
        result.error(subscriber.getId(), TerminatedError);
        subscribers.remove(subscriber);
      } else if (!subscriber.isActive())
        result.error(subscriber.getId(), InactiveError);
      else {
        boolean success = subscriber.receive(event);
        if (!success) { // fix here, more elaborate return values
          result.error(subscriber.getId(), SomeOtherError.withMessage("Receive returned false"));
        }
        result.success(subscriber.getId());
      }
    }
    return result;
  }

  private static DispatchError InactiveError = new DispatchError("Inactive");
  private static DispatchError TerminatedError = new DispatchError("Terminated");
  private static DispatchError SomeOtherError = new DispatchError("");
}

/**
 *
 */
final class DispatchError {
  private final String message;

  public DispatchError(String message) {
    this.message = message;
  }

  /**
   * [throwException description]
   * @throws Exception [description]
   */
  public void throwException() throws Exception {
    throw new Exception(this.message);
  }

  /**
   * [getMessage description]
   * @return [description]
   */
  public String getMessage() {
    return this.message;
  }

  public DispatchError withMessage(String message) {
    String m = (this.message.isEmpty() ? message : (this.message + " " + message));
    return new DispatchError(m);
  }

}

final class DispatchResult {
  private Set<String> successes;
  private Map<String, DispatchError> errors;
  {
    successes = new HashSet<>();
    errors = new HashMap<>();
  }

  /**
   * [success description]
   * @param id [description]
   */
  public void success(String id) {
    this.successes.add(id);
  }

  /**
   * [add description]
   * @param  id    [description]
   * @param  error [description]
   * @return       [description]
   */
  public void error(String id, DispatchError error) {
    errors.put(id, error);
  }

  /**
   * [get description]
   * @param  id [description]
   * @return    [description]
   */
  public DispatchError getError(String id) {
    return errors.get(id);
  }

  /**
   * [toString description]
   * @return [description]
   */
  public String toString() {
    StringBuilder buildr = new StringBuilder();
    for (Map.Entry<String, DispatchError> error : errors.entrySet()) {
      buildr.append("[" + error.getKey() + " -> " + error.getValue().getMessage() + "] ");
    }
    return buildr.toString();
  }

}

/**
 *
 */
interface Subscriber {
  /**
   * [getId description]
   * @return [description]
   */
  public String getId();

  /**
   *
   */
  public Set<Class<?>> getEventTypes();

  /**
   * [isReceiving description]
   * @return [description]
   */
  public boolean isActive();

  /**
   * [isTerminated description]
   * @return [description]
   */
  public boolean isTerminated();

  /**
   * fix return type
   * @param  event [description]
   * @return       [description]
   */
  public boolean receive(Object event);
}

/**
 *
 */
class StateMachineSubscriber implements Subscriber {
  private final StateMachineDef<?, ?> stateMachineDef;
  private final StateMachineExecutor executor;

  public StateMachineSubscriber(StateMachineDef<?, ?> stateMachineDef, StateMachineExecutor executor) {
    this.stateMachineDef = stateMachineDef;
    this.executor = executor;
  }

  /**
   * [getId description]
   * @return [description]
   */
  public String getId() {
    return this.stateMachineDef.getId();
  }

  /**
   *
   */
  public Set<Class<?>> getEventTypes() {
    return this.stateMachineDef.getEventTypes();
  }

  /**
   * [isActive description]
   * @return [description]
   */
  public boolean isActive() {
    return this.executor.isActive();
  }

  /**
   * [isTerminated description]
   * @return [description]
   */
  public boolean isTerminated() {
    return this.executor.isTerminated();
  }

  /**
   * [receive description]
   * @param  event [description]
   * @return       [description]
   */
  public boolean receive(Object event) {
    return this.executor.receive(event);
  }
}

/**
 * thread-safe
 */
class Subscribers {
  private final ConcurrentMap<Class<?>, Set<Subscriber>> subscribers;
  {
    subscribers = new ConcurrentHashMap<>();
  }

  /**
   * [add description]
   * @param subscriber [description]
   */
  public void add(Subscriber subscriber) {
    for (Class<?> eventType : subscriber.getEventTypes()) {
      this.subscribers.putIfAbsent(eventType, new CopyOnWriteArraySet<>());
      this.subscribers.get(eventType).add(subscriber);
    }
  }

  /**
   * [remove description]
   * @param  subscriber [description]
   * @return            [description]
   */
  public boolean remove(Subscriber subscriber) {
    boolean present = false;
    for (Class<?> eventType : subscriber.getEventTypes()) {
      Set<Subscriber> set = this.subscribers.get(eventType);
      if (set != null) {
        present |= set.remove(subscriber);
      }
    }
    return present;
  }

  /**
   * [get description]
   * @param  eventType [description]
   * @return           [description]
   */
  public Set<Subscriber> get(Class<?> eventType) {
    return new HashSet<>(this.subscribers.get(eventType));
  }

}