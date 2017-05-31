package alan.akka;

import java.util.Set;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.routing.RoundRobinPool;

import alan.core.Subscribers;
import alan.core.Subscriber;


/**
 * 
 */
public class AlanAkka extends AbstractActor {

  private final Subscribers subscribers;
  {
    subscribers = new Subscribers();
  }

  public AlanAkka(Set<AkkaMachineConf> confs) {
    for (AkkaMachineConf conf : confs) {
      RoundRobinPool pool = new RoundRobinPool(conf.getParallelism());
      if (conf.getResizer() != null)
        pool = pool.withResizer(conf.getResizer());
      Props props = pool.props(Props.create(MachineActor.class, conf.getMachineDef(), conf.getTapeLogFactory()));
      ActorRef actor = getContext().actorOf(props);
      getContext().watch(actor);
      subscribers.add(new ActorSubscriber(conf.getMachineDef(), actor));
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .matchAny(event -> {
        for (Subscriber subscriber : subscribers.get(event.getClass())) {
          if (subscriber.isTerminated()) {
            subscribers.remove(subscriber);
          } else if (!subscriber.isActive()) {
            // do something
          } else {
            boolean success = subscriber.receive(event);
            if (!success) {}
          }
        }
      }).build();
  }
}