package alan.akka;

import java.util.Set;

import akka.actor.ActorRef;

import alan.core.Subscriber;
import alan.core.MachineDef;


/**
 * 
 */
class ActorSubscriber implements Subscriber {
  private final MachineDef<?, ?, ?> machineDef;
  private final ActorRef actor;

  public ActorSubscriber(MachineDef<?, ?, ?> machineDef, ActorRef actor) {
    this.machineDef = machineDef;
    this.actor = actor;
  }

  public String getName() {
    return machineDef.getName();
  }

  public Set<Class<?>> getEventTypes() {
    return machineDef.getEventTypes();
  }

  public boolean isActive() {
    return true;
  }

  public boolean isTerminated() {
    return false;
  }

  public boolean receive(Object event) {
    actor.tell(event, null);
    return true;
  }
}