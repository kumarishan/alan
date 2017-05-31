package alan.util;

import java.util.concurrent.CompletableFuture;


/**
 * 
 */
public class FutureUtil {

  /**
   * [completedF description]
   * @param  value [description]
   * @return       [description]
   */
  public static <T> CompletableFuture<T> completedF(T value) {
    return CompletableFuture.completedFuture(value);
  }
}