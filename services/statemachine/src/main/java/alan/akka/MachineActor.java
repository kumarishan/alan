package alan.akka;

import akka.actor.AbstractActor;

import alan.core.Machine;
import alan.core.MachineDef;
import alan.core.ExecutionId;
import alan.core.Tape;
import alan.core.TapeLog;


/**
 * 
 */
public class MachineActor<S, SMC, T extends Tape> extends AbstractActor {
  private final MachineDef<S, SMC, T> machineDef;
  private final TapeLog<T> tapeLog;

  public MachineActor(MachineDef<S, SMC, T> machineDef, TapeLog<T> tapeLog) {
    this.machineDef = machineDef;
    this.tapeLog = tapeLog;
  }

  public MachineActor(MachineDef<S, SMC, T> machineDef, TapeLog.Factory tapeLogFactory) {
    this.machineDef = machineDef;
    this.tapeLog = tapeLogFactory.create(machineDef.getSchema(), getContext().dispatcher());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .matchAny(event -> {
        ExecutionId id = machineDef.getExecutionId(event);
        Machine machine = machineDef.createMachine(id, tapeLog, getContext().dispatcher());
        machine.run(event);
      })
      .build();
  }
}