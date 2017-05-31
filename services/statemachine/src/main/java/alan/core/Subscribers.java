package alan.core;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * thread-safe
 */
public class Subscribers {
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