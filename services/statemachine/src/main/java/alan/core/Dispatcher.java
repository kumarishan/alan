package alan.core;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Dispatcher
 */
public class Dispatcher {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private Subscribers subscribers;
  {
    subscribers = new Subscribers();
  }

  /**
   * [register description]
   * @param stateMachineDef [description]
   * @param executor        [description]
   */
  public void register(MachineExecutor<?, ?, ?> executor) {
    this.subscribers.add(new MachineSubscriber(executor));
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
      } else if (!subscriber.isActive()) {
        result.error(subscriber.getId(), InactiveError);
      } else {
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