package markov.services.sm;

import java.util.concurrent.Executor;

/**
 *
 */
class ExecutionException extends Exception {
  public ExecutionException() {}
  public ExecutionException(String message) {
    super(message);
  }
}

/**
 * State Machine executor
 * thread safe
 */
public abstract class StateMachineExecutor {
  // do not call directly
  private volatile int _currentStatus = 0;
  private int receiveFailures = 0; // @GaurdedBy("failureLock")
  private final Object failureLock = new Object();
  protected StateMachineDef<?, ?> stateMachine;


  /**
   * Receive an event if it's not Suspended or Terminated
   * if cannot receive then the executor is Suspended
   * @param  event [description]
   * @return       true if successfully scheduled the event
   *               false if either executor failed to schedule the event
   *               or is suspended or terminated
   */
  public final boolean receive(Object event) {
    if ((this.currentStatus() & (Suspended | Terminated)) != 0) return false;
    boolean received = this.receiveEvent(event);
    if (received) {
      start(); // Idle -> Processing
      markAsScheduled();
      return true;
    } else {
      synchronized (failureLock) {
        this.receiveFailures++;
        if (this.receiveFailures > RECEIVE_FAILURE_THRESHOLD) {
          this.receiveFailures = 0;
          this.markAsSuspended();
        }
      }
      return (this.currentStatus() & (Suspended | Terminated)) == 0;
    }
  }

  /**
   * [isActive description]
   * @return [description]
   */
  public final boolean isActive() {
    return (currentStatus() & ~(Terminated | Suspended)) == 0;
  }

  /**
   * [isTerminated description]
   * @return [description]
   */
  public final boolean isTerminated() {
    return (currentStatus() & Terminated) != 0;
  }

  /**
   * CAS on the volatile status variable
   * @param  oldStatus status to compare
   * @param  newStatus status to swap
   * @return           true if update successfull else false
   */
  protected final boolean updateStatus(int oldStatus, int newStatus) {
    return Unsafe.instance.compareAndSwapInt(this, STATUS_OFFSET, oldStatus, newStatus);
  }

  /**
   * get current status
   * @return current status
   */
  protected final int currentStatus() {
    return Unsafe.instance.getIntVolatile(this, STATUS_OFFSET);
  }

  /**
   * if (not terminated and not )
   *   Idle -> 1
   *   Scheduled -> 0
   *   Processing -> 0
   *   Suspended -> 0
   *   Terminated -> 0
   */
  // protected final boolean markAsIdle() {
  //   int status = currentStatus();
  //   if ((status & ()))
  // }

  /**
  * if (not terminated)
  *   Idle -> no change
  *   Scheduled -> 1
  *   Processing -> no change
  *   Suspended -> no change
  *   Terminated -> 0
  */
  protected final boolean markAsScheduled() {
    int status = currentStatus();
    if ((status & Terminated) != 0) return false;
    else {
      int newStatus = (Scheduled | (status & (Idle | Processing | Suspended)));
      return (this.updateStatus(status, newStatus) || markAsScheduled());
    }
  }

  /**
   * if (not terminated)
   *   Idle -> not change
   *   Scheduled -> not change
   *   Processing -> not change
   *   Suspended -> 1
   *   Terminated -> 0
   * @return [description]
   */
  protected final boolean markAsSuspended() {
    int status = currentStatus();
    if ((status & Terminated) != 0) return false;
    else {
      int newStatus = (Suspended | (status & (Idle | Scheduled | Processing)));
      return (this.updateStatus(status, newStatus) || markAsSuspended());
    }
  }

  public abstract void start();
  public abstract void register(StateMachineDef<?, ?> stateMachine);
  protected abstract boolean hasEvents();
  protected abstract boolean receiveEvent(Object event);


  /////////////////// Statuses //////////////////
  /**
   * Possible States
   * ---------------
   * [Idle | Scheduled]
   * [Processing | Scheduled]
   * [Processing | Scheduled | Suspended]
   * [Idle | Scheduled | Suspended]
   * [Terminated]
   */

  private static int Idle = 1;          // Not processing
  private static int Scheduled = 2;     // gaurantees atleast one event is scheduled (ie in queue)
  private static int Processing = 4;    // Processing
  private static int Suspended = 8;     // not receiving
  private static int Terminated = 16;   // nothing is, can or will happen, end

  private static int notRecievingMask = Suspended | Terminated;

  private static int RECEIVE_FAILURE_THRESHOLD;
  private static long STATUS_OFFSET;
  static {
    try {
      STATUS_OFFSET = Unsafe.instance.objectFieldOffset(StateMachineExecutor.class.getDeclaredField("_currentStatus"));
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}