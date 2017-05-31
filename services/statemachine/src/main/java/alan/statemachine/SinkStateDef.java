package alan.statemachine;

import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;


/**
 *
 */
interface SinkStateDef<S, R> {

  /**
   * [getState description]
   * @return [description]
   */
  public S getState();

  /**
   *
   */
  public Class<R> getResultType();

  /**
   * [isSuccess description]
   * @return [description]
   */
  public boolean isSuccess();

  /**
   * [isActionAsync description]
   * @return [description]
   */
  public boolean isActionAsync();

}