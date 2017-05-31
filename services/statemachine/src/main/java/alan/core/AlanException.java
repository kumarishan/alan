package alan.core;


/**
 *
 */
public class AlanException extends Exception {
  private final Code code;

  public AlanException(Code code, Object... arguments) {
    super(code.format(arguments));
    this.code = code;
  }

  public Code getCode() {
    return code;
  }

  /**
   *
   */
  public static enum Code {
    EXECUTION_FAILURE("Failed to execute {}-{} for event {}");

    String format;

    Code(String format) {
      this.format = format;
    }

    public String format(Object... arguments) {
      return String.format(format, arguments);
    }
  }
}