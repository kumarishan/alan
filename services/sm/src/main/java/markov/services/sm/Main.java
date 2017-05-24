package markov.services.sm;

import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import static markov.services.sm.Turnstile.State.*;
import static markov.services.sm.Turnstile.State;
import static markov.services.sm.Turnstile.*;

/**
 *
 */
class Turnstile extends StateMachineDef<Turnstile.State, TurnstileContext> {

  static enum State {
    StateOne, StateTwo, Success, Failure
  }

  static class EventOne {
    String turnstile;
    int increment;
    public EventOne(String turnstile, int increment) {
      this.turnstile = turnstile;
      this.increment = increment;
    }
  }

  static class EventTwo {
    String turnstile;
    int decrement;
    public EventTwo(String turnstile, int decrement) {
      this.turnstile = turnstile;
      this.decrement = decrement;
    }
  }

  static class StateOneContext {
    int count;
    public StateOneContext(int count) {
      this.count = count;
    }
  }

  static class StateTwoContext {
    int count;
    public StateTwoContext(int count) {
      this.count = count;
    }
  }

  static class ErrorResult {
    int total;
    public ErrorResult(int total) {
      this.total = total;
    }
  }

  static class SuccessResult {
    int total;
    public SuccessResult(int total) {
      this.total = total;
    }
  }

  static class TurnstileContext {
    int total;
    public TurnstileContext(int total) {
      this.total = total;
    }
  }

static class MyExecutionId implements ExecutionId {
  String turnstile;
  public MyExecutionId(String turnstile) {
    this.turnstile = turnstile;
  }

  public String toString() {
    return turnstile;
  }
}

  {
    id("my-fsm");

    executionIdFor(EventOne.class, (event) -> new MyExecutionId(event.turnstile));
    executionIdFor(EventTwo.class, (event) -> new MyExecutionId(event.turnstile));

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

    state(StateOne, StateOneContext.class, () -> new StateOneContext(0))
      .onEvent(EventOne.class).perform((event, context) -> {
        System.out.println("Received EventOne in StateOne");
        context.getStateContext().count += event.increment;
        context.getStateMachineContext().total += event.increment;

        if (context.getStateMachineContext().total > 40)
            return goTo(Success);
        else if (context.getStateMachineContext().total > 20)
          return goTo(StateTwo);
        else
          return goTo(StateOne);
      })
      .onEvent(EventTwo.class).perform((event, context) -> {
        System.out.println("Received EventTwo in StateOne");
        context.getStateMachineContext().total -= event.decrement;
        return goTo(StateTwo);
      });


    state(StateTwo, StateTwoContext.class, () -> new StateTwoContext(0))
      .onEvent(EventTwo.class).perform((event, context) -> {
        System.out.println("Received EventTwo in StateTwo");

        context.getStateContext().count += event.decrement;
        context.getStateMachineContext().total -= event.decrement;

        if (context.getStateMachineContext().total < -20)
          return goTo(Failure);
        else if (context.getStateMachineContext().total < 0)
          return goTo(StateOne);
        else
          return goTo(StateTwo);
      })
      .onEvent(EventOne.class).perform((event, context) -> {
        System.out.println("Received EventOne in StateTwo");

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
    serde(TurnstileContext.class,
      (context) -> intToByteArray(context.total),
      (binary) -> new TurnstileContext(byteArrayToInt(binary)));

    serde(StateOneContext.class,
      (context) -> intToByteArray(context.count),
      (binary) -> new StateOneContext(byteArrayToInt(binary)));

    serde(StateTwoContext.class,
      (context) -> intToByteArray(context.count),
      (binary) -> new StateTwoContext(byteArrayToInt(binary)));

    serde(SuccessResult.class,
      (result) -> intToByteArray(result.total),
      (binary) -> new SuccessResult(byteArrayToInt(binary)));

    serde(ErrorResult.class,
      (result) -> intToByteArray(result.total),
      (binary) -> new ErrorResult(byteArrayToInt(binary)));

    verify();
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

interface CState {};
class CStateOne implements CState {};


public class Main {

  public static void main(String[] args) {

    MarkovConfig config = new MarkovConfig();
    Markov markov = new Markov(config);

    Turnstile fsm = new Turnstile();
    markov.add(fsm, 1);
    markov.start();

    EventOne one1 = new EventOne("t-1", 1);
    EventOne one2 = new EventOne("t-1", 2);
    EventTwo two1 = new EventTwo("t-1", 1);
    EventTwo two2 = new EventTwo("t-1", 2);
    Object[] events = new Object[] {one1, one2, two1, two2};

    Random random = new Random(10);
    for (int i = 0; i < 10; i++) {
      markov.send(events[random.nextInt(4)]);
    }

    try {
      Thread.sleep(10000);
    } catch (InterruptedException ex) {}
  }

}