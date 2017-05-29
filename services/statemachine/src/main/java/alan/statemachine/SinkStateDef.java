package alan.statemachine;

import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;


/**
 *
 */
class SinkStateDef<S, SMC, R> {

  private final S name;
  private final Class<R> resultType;
  private final BiFunction<SMC, ExecutorService, CompletableFuture<R>> asyncAction;
  private final Function<SMC, R> action;
  private final boolean isSuccess;

  public SinkStateDef(S name, Class<R> resultType, Function<SMC, R> action, boolean isSuccess) {
    this.name = name;
    this.resultType = resultType;
    this.asyncAction = null;
    this.action = action;
    this.isSuccess = isSuccess;
  }

  public SinkStateDef(S name, Class<R> resultType, BiFunction<SMC, ExecutorService, CompletableFuture<R>> asyncAction, boolean isSuccess) {
    this.name = name;
    this.resultType = resultType;
    this.asyncAction = asyncAction;
    this.action = null;
    this.isSuccess = isSuccess;
  }

  /**
   *
   */
  public Class<?> getResultType() {
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
  public BiFunction<SMC, ExecutorService, CompletableFuture<R>> getAsyncAction() {
    return asyncAction;
  }

  /**
   * [isSuccess description]
   * @return [description]
   */
  public final boolean isSuccess() {
    return isSuccess;
  }

  /**
   * [isActionAsync description]
   * @return [description]
   */
  public final boolean isActionAsync() {
    return this.asyncAction != null;
  }

}