package trees;

public interface ClosableLock extends AutoCloseable {
  public void close();
}
