abstract class ReturnAndFinally {

  private Object foo(Object a) {
    try {
      Object b = bar(a);
      if (b != null) {
        return b;
      }
    } finally {
      System.out.println("foo");
    }
    return null;
  }

  public abstract Object bar(Object o) throws RuntimeException;
}
