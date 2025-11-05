package trees;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClosableReadWriteLockImpl implements ClosableReadWriteLock {
  final ReadWriteLock rwLock;
  final ClosableLock readLock;
  final ClosableLock writeLock;

  public ClosableReadWriteLockImpl() {
    rwLock = new ReentrantReadWriteLock();
    readLock = new CloseableReadLock();
    writeLock = new CloseableWriteLock();
  }

  @Override
  public ClosableLock closeableReadLock() {
    rwLock.readLock().lock();;
    return readLock;
  }

  class CloseableReadLock implements ClosableLock {

    @Override
    public void close() {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public ClosableLock closeableWriteLock() {
    rwLock.writeLock().lock();;
    return writeLock;
  }

  class CloseableWriteLock implements ClosableLock {

    @Override
    public void close() {
      rwLock.writeLock().unlock();
    }
  }
}
