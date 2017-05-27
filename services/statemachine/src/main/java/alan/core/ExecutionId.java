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

  public static SchemaRow toSchemaRow(ExecutionId id) {
    SchemaRow row = new SchemaRow();
    row.put("mname", String.class, id.mname);
    row.put("uuid", String.class, id.uuid);
    return row;
  }
};