package alan.statemachine;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ExecutorService;


/**
 * Creates a Markov service
 * - a rest service to recieve events
 * - json mappers to/from events
 * - connections to other markov service in the cluster and monitor
 * - routing of events based on consistent hashing on instance id (if required)
 * - configurations
 *   - num threads
 */
public class Markov {

  private final MarkovConfig config;
  private final Dispatcher dispatcher;
  private final ExecutorService executorService;

  public Markov(MarkovConfig config) {
    this.config = config;
    this.dispatcher = new Dispatcher();
    this.executorService = new ForkJoinPool();
  }

  /**
   * [add description]
   * @param  stateMachineDef [description]
   * @param  parallelism     [description]
   * @return                 [description]
   */
  public <S, SMC> void add(StateMachineDef<S, SMC> stateMachineDef, int parallelism) {
    StateMachineExecutor<S, SMC> executor = new StateMachineExecutor<>(stateMachineDef, parallelism, 10, 10000);
    dispatcher.register(executor);
  }

  /**
   * [add description]
   * @param stateMachine [description]
   */
  public void add(StateMachineDef<?, ?> stateMachine) {
    add(stateMachine, 1);
  }

  /**
   * [send description]
   * @param event [description]
   */
  public void send(Object event) {
    dispatcher.dispatch(event);
  }

  /**
   * [start description]
   */
  public void start() {}

  /**
   * [terminate description]
   */
  void terminate() {}
}

// TODO
class Seed {}
class MarkovConfig {
  String host;
  int port;
  List<Seed> seeds;
}