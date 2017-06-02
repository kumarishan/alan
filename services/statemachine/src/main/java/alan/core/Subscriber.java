package alan.core;

import java.util.Set;


/**
 *
 */
public interface Subscriber {
  /**
   * [getId description]
   * @return [description]
   */
  public String getName();

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