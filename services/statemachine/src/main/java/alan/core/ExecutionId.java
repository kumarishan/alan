package alan.core;

/**
 *
 */
public class ExecutionId {
  String mname;
  String uuid;

  public ExecutionId(String mname, String uuid) {
    this.mname = mname;
    this.uuid = uuid;
  }

  public int hashCode() {
    int hash = mname.hashCode();
    hash = hash * 31 + uuid.hashCode();
    return hash;
  }

  public boolean equals(Object other) {
    ExecutionId o;
    return (other instanceof ExecutionId) && (o = (ExecutionId)other).mname.equals(mname) && o.uuid.equals(uuid);
  }
};