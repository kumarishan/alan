package alan.core;

import java.util.concurrent.ExecutorService;
import java.util.Set;

import alan.core.Schema;


/**
 * 
 */
public interface MachineDef<S, SMC, T extends Tape> {
  public ExecutorService createExecutorService();
  public String getName();
  public Schema<T> getSchema();
  public Set<Class<?>> getEventTypes();
  public ExecutionId getExecutionId(Object event);
  public Machine createMachine(ExecutionId id, TapeLog<T> tapeLog, ExecutorService executor);
}