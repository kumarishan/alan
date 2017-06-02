package alan.core;


/**
 * 
 */
public class MachineConf<C extends MachineConf<C>> {
  private final MachineDef<?, ?, ?> machineDef;
  private int parallelism;
  private TapeLog.Factory tapeLogFactory;
  protected C instance;

  public MachineConf(MachineDef<?, ?, ?> machineDef) {
    this.machineDef = machineDef;
    this.parallelism = 1;
  }

  public C withParallelism(int parallelism) {
    this.parallelism = parallelism;
    return instance;
  }

  public C withTapeLogFactory(TapeLog.Factory factory) {
    this.tapeLogFactory = factory;
    return instance;
  }

  public TapeLog.Factory getTapeLogFactory() {
    return tapeLogFactory;
  }

  public int getParallelism() {
    return parallelism;
  }

  public MachineDef<?, ?, ?> getMachineDef() {
    return machineDef;
  }

}