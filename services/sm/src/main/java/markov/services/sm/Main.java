package markov.services.sm;

class EventOne {}
class StateOneData {};
class StateTwoData {};
class StateThreeData {};

class StateOne extends State<StateOneData> {
  public StateOne() {
    setData(new StateOneData());
  }
}

class StateTwo extends State<StateTwoData> {
  public StateTwo() {
    setData(new StateTwoData());
  }
}


class MyFSMData {}

class MyExecutionId implements ExecutionId {}

class MyFSM extends StateMachine<MyFSMData> {
  {
    when(new StateOne())
      .onEvent(EventOne.class).perform((event, context) -> {
          // do something
          return goTo(StateTwo.class);
      })
      .onEvent(EventOne.class, (event, context) -> true).perform((event, context) -> {
          // do something
          return goTo(StateTwo.class);
      });

    executionIdFor(EventOne.class, (event) -> new MyExecutionId());
  }
}

public class Main {
  public static void main(String[] args) {
    ExecutionStageStore exStore = new ExecutionStageStore();
    StandardBehavior behavior = new StandardBehavior();
    MyFSM fsm = new MyFSM();
    fsm.setBehavior(behavior);

    // Executing FSM
    // 1. receive an event;
    EventOne event1 = new EventOne();
    // 2. get associated instance id
    // depends on metadata from event and the state machine
    ExecutionId executionId = fsm.createExecutionId(event1);

    // 3. get the execution context for the instance id;
    ExecutionStage exStage = exStore.forId(executionId);

    // ExecutionStage newExStage =     // 6. get the new execution context
    //   fsm.withExecutionStage(context) // 4. init fsm with the execution context
    //      .receiveEvent(event1);         // 5. receive the event

    // 7. Persiste the execution context


    System.out.println("Hello! I am markov");
    System.out.println(fsm.toString());
  }
}