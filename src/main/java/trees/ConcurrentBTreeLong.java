package trees;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class ConcurrentBTreeLong<VALUE> extends BTreeLong<VALUE> {
  ClosableReadWriteLock rwLock;

  public ConcurrentBTreeLong(int order) {
    super(order);
    rwLock = new ClosableReadWriteLockImpl();
  }

  @Override
  public VALUE get(long key) {
    try (var lock = rwLock.closeableReadLock()) {
      return super.get(key);
    }
  }

  @Override
  public VALUE put(long key, VALUE value) {
    try (var lock = rwLock.closeableWriteLock()) {
      return super.put(key, value);
    }
  }

  @Override
  public VALUE computeIfAbsent(long key, Supplier<VALUE> valueFunction) {
    try (var lock = rwLock.closeableWriteLock()) {
      return super.computeIfAbsent(key, valueFunction);
    }
  }

  @Override
  public VALUE remove(long key) {
    try (var lock = rwLock.closeableWriteLock()) {
      return super.remove(key);
    }
  }

  @Override
  public List<VALUE> getAll() {
    try (var lock = rwLock.closeableReadLock()) {
      return super.getAll();
    }
  }

  @Override
  public Iterator<VALUE> range(long start, long end) {
    try (var lock = rwLock.closeableWriteLock()) {
      var iterator = super.range(start, end);
      return new ConcurrentIterator<>(iterator);
    }
  }

  class ConcurrentIterator<V> implements Iterator<V> {
    final Iterator<V> it;

    ConcurrentIterator(Iterator<V> it) {
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      try (var lock = rwLock.closeableReadLock()) {
        return it.hasNext();
      }
    }

    @Override
    public V next() {
      try (var lock = rwLock.closeableReadLock()) {
        return it.next();
      }
    }
  }
}
