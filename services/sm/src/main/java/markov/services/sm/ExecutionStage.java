package markov.services.sm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * [TODO]
 */
class ExecutionStage<S> {
  private final ExecutionId instanceId;
  private final S stateName;
  private final StateMachineDef.Context<?, ?> context;
  private final boolean isFailureState;

  public ExecutionStage(ExecutionId instanceId, S stateName, StateMachineDef.Context<?, ?> context, boolean isFailureState) {
    this.instanceId = instanceId;
    this.stateName = stateName;
    this.context = context;
    this.isFailureState = isFailureState;
  }

  public boolean isFailure() { return this.isFailureState; }

  /**
   * [TODO]
   * - [IMP] return should never complete with Exception
   *         at worst it is the ExecutionStage representing user defined/defauly Failure stage.
   *
   * @param  stateMachineDef [description]
   * @param  executorService [description]
   * @return                 [description]
   */
  public CompletableFuture<ExecutionStage> run(StateMachineDef<S, ?> stateMachineDef, ExecutorService executorService) {
    return CompletableFuture.completedFuture(this);
  }

  /**
   * [failed description]
   * @return [description]
   */
  public ExecutionStage failed() {
    return this;
  }
}



/**
 * State machine dependent
 * Contains
 * - current stage
 * -
 */
// class ExecutionStage<S> {

// }

interface ExecutionId {};

interface ExecutionIdFactory<E> {
  public ExecutionId apply(E event);
}