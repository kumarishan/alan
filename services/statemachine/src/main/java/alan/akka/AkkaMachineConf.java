package alan.akka;

import akka.routing.Resizer;

import alan.core.MachineDef;
import alan.core.MachineConf;


/**
 * 
 */
public class AkkaMachineConf extends MachineConf<AkkaMachineConf> {
  private Resizer resizer;

  public AkkaMachineConf(MachineDef<?, ?, ?> machineDef) {
    super(machineDef);
    instance = this;
  }

  public AkkaMachineConf withResizer(Resizer resizer) {
    this.resizer = resizer;
    return this;
  }

  public Resizer getResizer() {
    return resizer;
  }

  public static AkkaMachineConf create(MachineDef<?, ?, ?> machineDef) {
    return new AkkaMachineConf(machineDef);
  }
}