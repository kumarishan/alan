package alan.statemachine;

import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;


/**
 *
 */
class SuccessStateDef<S, SMC, R> implements SinkStateDef<S, R> {

  private final S state;
  private final Class<R> resultType;
  private final BiFunction<SMC, Executor, CompletableFuture<R>> asyncAction;
  private final Function<SMC, R> action;

  public SuccessStateDef(S state, Class<R> resultType, Function<SMC, R> action) {
    this.state = state;
    this.resultType = resultType;
    this.asyncAction = null;
    this.action = action;
  }

  public SuccessStateDef(S state, Class<R> resultType, BiFunction<SMC, Executor, CompletableFuture<R>> asyncAction) {
    this.state = state;
    this.resultType = resultType;
    this.asyncAction = asyncAction;
    this.action = null;
  }

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
  public Function<SMC, R> getAction() {
    return action;
  }

  /**
   * [getAsyncAction description]
   * @return [description]
   */
  public BiFunction<SMC, Executor, CompletableFuture<R>> getAsyncAction() {
    return asyncAction;
  }

  /**
   * [isSuccess description]
   * @return [description]
   */
  public final boolean isSuccess() {
    return true;
  }

  /**
   * [isActionAsync description]
   * @return [description]
   */
  public final boolean isActionAsync() {
    return this.asyncAction != null;
  }

}