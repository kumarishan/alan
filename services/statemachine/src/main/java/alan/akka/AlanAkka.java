package alan.akka;

import java.util.Set;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.routing.RoundRobinPool;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import alan.core.Subscribers;
import alan.core.Subscriber;


/**
 * 
 */
public class AlanAkka extends AbstractActor {
  private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

  final ActorRef router;

  public AlanAkka(Set<AkkaMachineConf> confs) {
    this.router = getContext().actorOf(Props.create(AlanRouter.class, confs), "alan-router");
    getContext().watch(router);

    for (AkkaMachineConf conf : confs) {
      RoundRobinPool pool = new RoundRobinPool(conf.getParallelism());
      if (conf.getResizer() != null)
        pool = pool.withResizer(conf.getResizer());
      Props props = pool.props(Props.create(MachineActor.class, conf.getMachineDef(), conf.getTapeLogFactory()));
      ActorRef actor = getContext().actorOf(props, conf.getMachineDef().getName());
      getContext().watch(actor);
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .matchAny(event -> {
        LOG.info("Received event={}", event.getClass().getName());
        router.tell(event, null);
      }).build();
  }
}