package alan.statemachine;

import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;

import alan.util.FI.TriFunction;


/**
 *
 */
class FailureStateDef<S, SMC, R> implements SinkStateDef<S, R> {

  private final S state;
  private final Class<R> resultType;
  private final TriFunction<SMC, Throwable, ExecutorService, CompletableFuture<R>> asyncAction;
  private final BiFunction<SMC, Throwable, R> action;

  public FailureStateDef(S state, Class<R> resultType, BiFunction<SMC, Throwable, R> action) {
    this.state = state;
    this.resultType = resultType;
    this.asyncAction = null;
    this.action = action;
  }

  public FailureStateDef(S state, Class<R> resultType, TriFunction<SMC, Throwable, ExecutorService, CompletableFuture<R>> asyncAction) {
    this.state = state;
    this.resultType = resultType;
    this.asyncAction = asyncAction;
    this.action = null;
  }

  /**
   * [getState description]
   * @return [description]
   */
  public S getState() {
    return state;
  }

  /**
   *
   */
  public Class<R> getResultType() {
    return resultType;
  }

  /**
   * [getAction description]
   * @return [description]
   */
  public BiFunction<SMC, Throwable, R> getAction() {
    return action;
  }

  /**
   * [getAsyncAction description]
   * @return [description]
   */
  public TriFunction<SMC, Throwable, ExecutorService, CompletableFuture<R>> getAsyncAction() {
    return asyncAction;
  }

  /**
   * [isSuccess description]
   * @return [description]
   */
  public final boolean isSuccess() {
    return false;
  }

  /**
   * [isActionAsync description]
   * @return [description]
   */
  public final boolean isActionAsync() {
    return this.asyncAction != null;
  }

}