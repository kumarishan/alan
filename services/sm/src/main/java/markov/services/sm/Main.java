package markov.services.sm;

import static markov.services.sm.MyFSM.State.*;
import static markov.services.sm.MyFSM.State;
import static markov.services.sm.MyFSM.*;


/**
 *
 */
class MyFSM extends StateMachineDef<MyFSM.State, MyFSMContext> {

  static enum State {
    StateOne, StateTwo, Success, Failure
  }

  static class EventOne {}
  static class EventTwo {}

  static class StateOneContext {};
  static class StateTwoContext {};
  static class ErrorContext {};
  static class SuccessContext {};

  static class MyFSMContext {}

  static class MyExecutionId implements ExecutionId {}

  {
    // State definitions
    // - statename, preferrable enum, string or an immutable singletons
    // - optinal state context with factories, serializers, deserializers
    // - transitions
    //   - Event, Predicate(optinal) -> Action
    //   - Action can mutate or reset state context
    //   - Action should return TransitionActions like goTo, failTo, stay()

    state(StateOne, () -> new StateOneContext(),
          (context) -> new byte[1024],
          (binary) -> new StateOneContext())
      .onEvent(EventOne.class).perform((event, context) -> {
          // do something
          // reset state context
          return goTo(StateOne);
      })
      .onEvent(EventOne.class,
              (event, context) -> true).perform((event, context) -> {
          // do something
          return goTo(StateTwo);
      });

    state(StateTwo, () -> new StateTwoContext(),
          (context) -> new byte[1024],
          (binary) -> new StateTwoContext())
      .onEvent(EventTwo.class).perform((event, context) -> {
        // do something
        return goTo(StateOne);
      });

    // always call after state is defined
    start(StateOne);

    // optional can define success stage
    // allowing to mark execution stage as completed
    // and therefore never receive further events
    // can have multiple success state.
    success(Success,
            (successContext) -> new byte[1024],
            (stateMachineContext) -> {
      // do something
      // optionally update statemachine context
      return new SuccessContext();
    });

    // stages where u can only reach using
    // failTo transition with the exception
    // multiple failure stages
    // once in failure state, the Execution stage is marked as terminated
    // and failed
    // wont receive further events
    failure(Failure,
            (errorContext) -> new byte[1024],
            (stateMachineContext) -> {
      // do something
      return new ErrorContext();
    });

    // handler for exceptions from actions
    uncaughtActionExceptionHandler(
      (state, event, stateContext, stateMachineContext, exception) -> {
        // handle exception
        // or optional go to some failed state
        // if no state change then the execution stage doesnot move forward
        failTo(Failure);
      }
    );

    stateMachineContext(() -> new MyFSMContext(),
                        (context) -> new byte[1024],
                        (binary) -> new MyFSMContext());

    executionIdFor(EventOne.class, (event) -> new MyExecutionId());
    executionIdFor(EventTwo.class, (event) -> new MyExecutionId());
  }

}


public class Main {
  public static void main(String[] args) {
    // EventJsonMappers serializers = new EventJsonMappers();
    // serializers.add(EventOne.class, (event) -> "", (json) -> new EventOne())
    //            .add(EventTwo.class, (event) -> "", (json) -> new EventTwo());

    // MarkovConfig config = new MarkovConfig();
    // Markov markov = new Markov(config);

    // MyFSM fsmOne = new MyFSM();
    // MyFSM fsmTwo = new MyFSM();

    // markov.add(fsmOne);
    // markov.add(fsmTwo);

    // markov.start();

    // System.out.println("Markov service started");
  }
}