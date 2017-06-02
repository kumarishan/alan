package alan.akka;

import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import static akka.routing.ConsistentHashingRouter.ConsistentHashMapper;
import akka.routing.ConsistentHashingGroup;
import akka.cluster.routing.ClusterRouterGroup;
import akka.cluster.routing.ClusterRouterGroupSettings;

import alan.core.MachineDef;


/**
 * 
 */
class AlanRouter extends AbstractActor {

  final Map<Class<?>, Set<ActorRef>> routers;

  public AlanRouter(Set<AkkaMachineConf> confs) {
    routers = new HashMap<>();

    for (AkkaMachineConf conf : confs) {
      MachineDef<?, ?, ?> machineDef = conf.getMachineDef();
      ConsistentHashMapper hashMapper = new AlanConsistentHashMapper(machineDef);
      Iterable<String> routeesPath = Collections.singletonList("/user/alan-akka/" + machineDef.getName());
      ActorRef actor =
        getContext().actorOf(
          new ClusterRouterGroup(
            new ConsistentHashingGroup(routeesPath).withHashMapper(hashMapper)
            , new ClusterRouterGroupSettings(1000, routeesPath, true, "alan-akka")).props(), "router-" + machineDef.getName());

      Set<Class<?>> eventTypes = machineDef.getEventTypes();
      for (Class<?> type : eventTypes) {
        Set<ActorRef> rs = routers.get(type);
        if (rs == null) {
          rs = new HashSet<>();
          routers.put(type, rs);
        }
        rs.add(actor);
      }
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .matchAny(event -> {
        for (ActorRef router : routers.get(event.getClass())) {
          router.tell(event, null);
        }
      }).build();
  }

  static class AlanConsistentHashMapper implements ConsistentHashMapper {
    MachineDef<?, ?, ?> machineDef;
    public AlanConsistentHashMapper(MachineDef<?, ?, ?> machineDef) {
      this.machineDef = machineDef;
    }

    @Override
    public Object hashKey(Object event) {
      return machineDef.getExecutionId(event).hashCode();
    }
  }
}