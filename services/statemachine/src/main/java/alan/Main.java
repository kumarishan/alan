package alan;

import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alan.core.Alan;
import alan.core.AlanConfig;
import alan.core.ExecutionId;
import alan.core.TapeLog;
import alan.core.InMemoryTapeLog;
import alan.core.Tape;
import alan.statemachine.StateMachineSchema;
import alan.statemachine.StateMachineTape;
import alan.statemachine.StateMachineDef;

import static alan.Turnstile.TurnstileState.*;
import static alan.Turnstile.TurnstileState;
import static alan.Turnstile.*;

/**
 *
 */
class Turnstile extends StateMachineDef<Turnstile.TurnstileState, TurnstileContext> {

  static enum TurnstileState {
    StateOne, StateTwo, Success, Failure
  }

  static class EventOne {
    String turnstile;
    int increment;
    public EventOne() {}
    public EventOne(String turnstile, int increment) {
      this.turnstile = turnstile;
      this.increment = increment;
    }
  }

  static class EventTwo {
    String turnstile;
    int decrement;
    public EventTwo() {}
    public EventTwo(String turnstile, int decrement) {
      this.turnstile = turnstile;
      this.decrement = decrement;
    }
  }

  static class StateOneContext {
    int count;
    public StateOneContext() {}
    public StateOneContext(int count) {
      this.count = count;
    }
  }

  static class StateTwoContext {
    int count;
    public StateTwoContext() {}
    public StateTwoContext(int count) {
      this.count = count;
    }
  }

  static class ErrorResult {
    int total;
    public ErrorResult() {}
    public ErrorResult(int total) {
      this.total = total;
    }
  }

  static class SuccessResult {
    int total;
    public SuccessResult() {}
    public SuccessResult(int total) {
      this.total = total;
    }
  }

  static class TurnstileContext {
    int total;
    public TurnstileContext() {}
    public TurnstileContext(int total) {
      this.total = total;
    }
  }

  Logger LOG = LoggerFactory.getLogger(this.getClass());

  {
    name("my-fsm");

    executionIdFor(EventOne.class, (event) -> event.turnstile);
    executionIdFor(EventTwo.class, (event) -> event.turnstile);

    stateMachineContextFactory(TurnstileContext.class, () -> new TurnstileContext(0));

    // to use executor service inside action for async computation
    // the created service is accessible as context.executorService
    // the service is created only once
    executorServiceFactory(() -> new ForkJoinPool());

    // State definitions
    // - statename, preferrable enum, string or an immutable singletons
    // - optinal state context with factories, serializers, deserializers
    // - transitions
    //   - Event, Predicate(optinal) -> Action
    //   - Action can mutate or reset state context
    //   - Action should return TransitionActions like goTo, failTo, stay()

    State<StateOneContext> stateOne = defineState(StateOne, StateOneContext.class, () -> new StateOneContext(0));
    State<StateTwoContext> stateTwo = defineState(StateTwo, StateTwoContext.class, () -> new StateTwoContext(0));

    stateOne.onEvent(EventOne.class)
            .perform((event, context) -> {
              context.getStateContext().count += event.increment;
              context.getStateMachineContext().total += event.increment;

              LOG.debug("In state {} received event {} state machine context total = {}, State context count = {}",
                  context.getState(), event.getClass().getName(), context.getStateMachineContext().total, context.getStateContext().count);

              if (context.getStateMachineContext().total > 8)
                return goTo(Success);
              else if (context.getStateMachineContext().total > 5)
                return goTo(StateTwo);
              else
                return goTo(StateOne);
            });

    stateOne.onEvent(EventTwo.class)
            .perform((event, context) -> {
              context.getStateMachineContext().total -= event.decrement;
              LOG.debug("In state {} received event {} state machine context total = {}",
                  context.getState(), event.getClass().getName(), context.getStateMachineContext().total);
              return goTo(StateTwo);
            });

    stateTwo.onEvent(EventTwo.class)
            .perform((event, context) -> {
              context.getStateContext().count += event.decrement;
              context.getStateMachineContext().total -= event.decrement;

              LOG.debug("In state {} received event {} state machine context total = {} state context count = {}",
                  context.getState(), event.getClass().getName(), context.getStateMachineContext().total, context.getStateContext().count);

              if (context.getStateMachineContext().total < -20)
                return goTo(Failure);
              else if (context.getStateMachineContext().total < 0)
                return goTo(StateOne);
              else
                return goTo(StateTwo);
            });

    stateTwo.onEvent(EventOne.class)
            .perform((event, context) -> {
              LOG.debug("In state {} received event {} state machine context total = {}",
                  context.getState(), event.getClass().getName(), context.getStateMachineContext().total);

              context.getStateMachineContext().total += event.increment;
              return goTo(StateOne);
            });

    // always call after state is defined
    start(StateOne);

    // optional can define success stage
    // allowing to mark execution stage as completed
    // and therefore never receive further events
    // can have multiple success state.
    success(Success, SuccessResult.class, (stateMachineContext) -> {
      return new SuccessResult(stateMachineContext.total);
    });

    // stages where u can only reach using
    // failTo transition with the exception
    // multiple failure stages
    // once in failure state, the Execution stage is marked as terminated
    // and failed
    // wont receive further events
    failure(Failure, ErrorResult.class, (stateMachineContext) -> {
      return new ErrorResult(stateMachineContext.total);
    });

    // handler for exceptions from actions
    runtimeExceptionHandler(
      (state, event, context, exception) -> {
        // handle exception
        // or optional go to some failed state
        // if no state change then the execution stage doesnot move forward
        // throw new Exception();
        return failTo(Failure);
      }
    );

    // serializers
    kryo(TurnstileContext.class, 10);
    kryo(StateOneContext.class, 11);

    init();
  }

  private static byte[] intToByteArray(int num) {
    return new byte[] {
      (byte)(num >>> 24),
      (byte)(num >>> 16),
      (byte)(num >>> 8),
      (byte)(num)
    };
  }

  private static int byteArrayToInt(byte[] binary) {
    int num = 0;
    num = num | binary[0];
    num = (num << 8) | binary[1];
    num = (num << 8) | binary[2];
    num = (num << 8) | binary[3];
    return num;
  }
}


public class Main {

  private static volatile int count = 0;

  public static void main(String[] args) {

    AlanConfig config = new AlanConfig();
    Alan alan = new Alan(config);

    Turnstile fsm = new Turnstile();
    alan.add(fsm, InMemoryTapeLog.factory, 4);
    alan.start();

    EventOne one1 = new EventOne("t-1", 1);
    EventOne one2 = new EventOne("t-1", 2);
    EventTwo two1 = new EventTwo("t-1", 1);
    EventTwo two2 = new EventTwo("t-1", 2);
    Object[] events = new Object[] {one1, one2, two1, two2};

    Random random = new Random(10);
    ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

    int delay = 0;
    int period = 1000;
    for (int i = 0; i < 15; i++) {
      scheduler.schedule(() -> {
        alan.send(events[random.nextInt(4)]);
      }, delay, TimeUnit.MILLISECONDS);
      delay += period;
    }

    try {
      Thread.sleep(10000);
    } catch (InterruptedException ex) {}
  }

}