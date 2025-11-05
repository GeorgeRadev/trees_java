package trees;

/**
 * replacement for try{lock();} finally {unlock();}
 * 
 * <code>
 * try(rwLock.closableReadLock()){
 *    // do something
 * }
 * </code>
 */
public interface ClosableReadWriteLock {

  /**
   * @return locked closeable read lock
   */
  ClosableLock closeableReadLock();

  /**
   * @return locked closeable write lock
   */
  ClosableLock closeableWriteLock();
}
