package alan.core;

import java.util.concurrent.CompletableFuture;


/**
 * 
 */
public interface Machine {

  /**
   * [getExecutionId description]
   * @return [description]
   */
  public ExecutionId getExecutionId();

  /**
   * Name of the state machine
   * @return [description]
   */
  public String getName();

  /**
   * Run Cycle (high level)
   *   - Acquire Lock
   *   - Start / Get current
   *   - Transition
   *   - Update
   *   - Release Lock
   *
   * @param  event [description]
   * @return       [description]
   */
  public <E> CompletableFuture<Response> run(E event);

  public static enum Response {
    SUCCESS,
    RETRY_TASK,
    FAILED,
    FAILED_TO_PERSIST_NEXT_STAGE,
    FAILED_INCONSISTENT_LOCK,
    FAILED_ACTION_TIMEOUT,
    FAILED_UNHANDLED_EVENT,
    FAILED_ALREADY_COMPLETE,
    FAILED_TO_START,
    FAILED_LOCK_ALREADY_HELD,
    FAILED_INVALID_TRANSITION;
  }

}