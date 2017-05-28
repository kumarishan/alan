package alan.core;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ExecutorService;

import alan.statemachine.StateMachineDef;
import alan.statemachine.StateMachineExecutor;


/**
 * Creates a Alan service
 * - a rest service to recieve events
 * - json mappers to/from events
 * - connections to other markov service in the cluster and monitor
 * - routing of events based on consistent hashing on instance id (if required)
 * - configurations
 *   - num threads
 */
public class Alan {

  private final AlanConfig config;
  private final Dispatcher dispatcher;
  private final ExecutorService executorService;

  public Alan(AlanConfig config) {
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