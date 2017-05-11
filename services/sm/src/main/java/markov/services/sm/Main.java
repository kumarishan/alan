package markov.services.sm;

class MyFSM extends FSM {}

public class Main {
  public static void main(String[] args) {
    FSM fsm = new MyFSM();
    System.out.println("Hello! I am markov");
  }
}