package alan.core;

import java.util.Set;


/**
 *
 */
public class MachineSubscriber implements Subscriber {
  private final MachineExecutor<?, ?, ?> executor;

  public MachineSubscriber(MachineExecutor<?, ?, ?> executor) {
    this.executor = executor;
  }

  /**
   * [getId description]
   * @return [description]
   */
  public String getName() {
    return executor.getName();
  }

  /**
   *
   */
  public Set<Class<?>> getEventTypes() {
    return executor.getEventTypes();
  }

  /**
   * [isActive description]
   * @return [description]
   */
  public boolean isActive() {
    return executor.isActive();
  }

  /**
   * [isTerminated description]
   * @return [description]
   */
  public boolean isTerminated() {
    return executor.isTerminated();
  }

  /**
   * [receive description]
   * @param  event [description]
   * @return       [description]
   */
  public boolean receive(Object event) {
    return executor.receive(event);
  }
}
