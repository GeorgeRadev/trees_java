package trees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import java.util.function.Function;

import trees.RBox.IntersectResult;

public class RTree<KEY extends Comparable<KEY>, VALUE extends Comparable> {
  private final int ORDER;
  private final Function<VALUE, KEY> toKey;
  private final Function<VALUE, RBox> toBox;

  private Node<VALUE> root;
  private BTree<KEY, IndexRef> indexKey;
  private int height;

  public RTree(int order, Function<VALUE, KEY> toKey, Function<VALUE, RBox> toBox) {
    if (order < 3) {
      throw new IllegalArgumentException("Order must be at least 3");
    }
    ORDER = order;
    this.toKey = toKey;
    this.toBox = toBox;
    root = new Node<VALUE>(ORDER);
    indexKey = new BTree<>(ORDER);
  }

  public void clear() {
    height = 0;
    root = new Node<VALUE>(ORDER);
    indexKey.clear();
  }

  public boolean isEmpty() {
    return indexKey.isEmpty();
  }

  public int size() {
    return indexKey.size();
  }

  public int height() {
    return height;
  }

  /**
   * Returns the value associated with the given key.
   *
   * @param key the key
   * @return the value associated with the given key if the key is in the symbol
   *         table
   *         and {@code null} if the key is not in the symbol table
   */
  public VALUE get(KEY key) {
    var ref = indexKey.get(key);
    return ref == null ? null : ((VALUE) ref.value);
  }

  /**
   * removes the value associated with the given key.
   *
   * @param key the key
   * @return the value associated with the given key if the key is in the symbol
   *         table
   *         and {@code null} if the key is not in the symbol table
   */
  public VALUE remove(KEY key) {
    var ref = indexKey.remove(key);
    if (ref != null) {
      var node = ref.node;
      node.delete(ref.value);
      if (node.parent != null) {
        _removeEmptyAndMerge(node.parent);
      }
      // check if we can lower the level
      while (root.count == 1 && height > 0) {
        var child = root.getChild(0);
        root = child;
        root.parent = null;
        height--;
      }
      return (VALUE) ref.value;
    } else {
      return null;
    }
  }

  /**
   * removes the value from the tree.
   *
   * @param value the value
   * @return the value associated with the given key if the key is in the symbol
   *         table
   *         and {@code null} if the key is not in the symbol table
   */
  public VALUE removeByValue(VALUE value) {
    var key = toKey.apply(value);
    return remove(key);
  }

  /**
   * Store value associated with the given key.
   *
   * @param value the value
   * @throws IllegalArgumentException if {@code value} is {@code null}
   */
  public VALUE add(VALUE value) {
    if (value == null) {
      throw new IllegalArgumentException("Value cannot be null");
    }
    return _put(value);
  }

  /**
   * Gets all values intersecting with a box.
   *
   * @param box      box to intersect with.
   * @param consumer callback for the values.
   * @return Array of values mathing the box.
   */
  public void intersect(RBox box, Consumer<VALUE> consumer) {
    _search(root, height, box, consumer);
  }

  /**
   * Gets all values intersecting with a box in parallel.
   * The consumer may be invoked concurrently from multiple threads and must be
   * thread-safe.
   *
   * @param box      box to intersect with.
   * @param consumer callback for the values.
   */
  public void intersectParallel(RBox box, Consumer<VALUE> consumer) {
    var action = new SearchAction(root, height, box, consumer);
    executeTask(action, 0);
  }

  /**
   * Gets all values intersecting with a box in parallel with specified threads.
   * The consumer may be invoked concurrently from multiple threads and must be
   * thread-safe.
   *
   * @param box             box to intersect with.
   * @param consumer        callback for the values.
   * @param parallelThreads number of threads in the pool.
   */
  public void intersectParallel(RBox box, Consumer<VALUE> consumer, int parallelThreads) {
    var action = new SearchAction(root, height, box, consumer);
    executeTask(action, parallelThreads);
  }

  /**
   * @return all values from the tree.
   */
  public void getAll(Consumer<VALUE> consumer) {
    _searchAll(root, height, consumer);
  }

  /**
   * Traverses all values from the tree in parallel. The consumer may be invoked
   * concurrently from multiple threads and must be thread-safe.
   */
  public void getAllParallel(Consumer<VALUE> consumer) {
    var action = new SearchAllAction(root, height, consumer);
    executeTask(action, 0);
  }

  /**
   * Returns a string representation of this B-tree (for debugging).
   *
   * @return a string representation of this B-tree.
   */
  public String toString() {
    return _toString(root, height, "") + "\n";
  }

  private void executeTask(RecursiveAction action, int parallelThreads) {
    final ForkJoinPool pool;
    if (parallelThreads == 0) {
      pool = ForkJoinPool.commonPool();
    } else {
      pool = new ForkJoinPool(parallelThreads);
    }
    pool.execute(action);
    action.join();
    pool.shutdown();
  }

  private VALUE _put(VALUE value) {
    var key = toKey.apply(value);
    // remove if exists
    var oldValue = remove(key);
    // insert
    var box = toBox.apply(value);
    var context = new InsertContext<KEY, VALUE>();
    context.key = key;
    context.box = box;
    context.value = value;
    var newNode = _insert(root, height, context);
    if (newNode != null) {
      // need to split root
      var newRoot = new Node<VALUE>(ORDER);
      newRoot.insert(ORDER, root.getBox(), root);
      newRoot.insert(ORDER, newNode.getBox(), newNode);
      root.parent = newRoot;
      newNode.parent = newRoot;
      root = newRoot;
      height++;
    }
    return oldValue;
  }

  // modified binary search for getting the place to insert also as search closest
  static int binarySearch(Object[] a, int fromIndex, int toIndex, Object key) {
    if (fromIndex == toIndex) {
      return fromIndex;
    } else if (fromIndex > toIndex) {
      throw new IllegalArgumentException(
          "fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
    }
    if (fromIndex < 0) {
      throw new ArrayIndexOutOfBoundsException(fromIndex);
    }
    if (toIndex > a.length) {
      throw new ArrayIndexOutOfBoundsException(toIndex);
    }
    int low = fromIndex;
    int high = toIndex - 1;
    int mid;
    int cmp = 0;

    while (low <= high) {
      mid = (low + high) >>> 1;
      cmp = ((Comparable) a[mid]).compareTo(key);

      if (cmp < 0)
        low = mid + 1;
      else if (cmp > 0)
        high = mid - 1;
      else
        return mid;
    }
    return low;
  }

  // return new node if added
  private Node<VALUE> _insert(Node<VALUE> node, int level, InsertContext<KEY, VALUE> context) {
    if (level == 0) {
      // value node
      if (node.count < ORDER) {
        // insert into the current node
        int ix = binarySearch(node.boxes, 0, node.count, context.box);
        node.insert(ix, context.box, context.value);
        _updateIndex(context.key, context.value, node);
        if (node.parent != null) {
          node.parent._updateUpward();
        }
        return null;
      } else {
        // split and insert
        var secondNode = (Node<VALUE>) _splitAndAdd(node, context, null);
        return secondNode;
      }
    } else {
      // tree node
      var box = context.box;
      // find least changable box to insert
      int ix = -1;
      for (int i = 0; i < node.count; i++) {
        if (((RBox) node.boxes[i]).intersect(box) == IntersectResult.CONTAINS) {
          ix = i;
          break;
        }
      }
      if (ix < 0) {
        // we have collision in boxes
        ix = binarySearch(node.boxes, 0, node.count, box);
        if (ix >= node.count) {
          ix = node.count - 1;
        }
      }
      // insert at position ix
      var newNode = _insert(node.getChild(ix), level - 1, context);

      if (newNode == null) {
        return null;
      } else {
        Node<VALUE> result = null;
        // The child we descended into grew and split, so its stored box is stale.
        // Refresh it before it is read below; otherwise the split path (which does
        // not call _updateUpward) copies the stale box and can leave a parent box
        // that under-covers the child, causing search to prune away real matches.
        node.boxes[ix] = node.getChild(ix).getBox();
        // insert returned node as value in the current one
        if (node.count < ORDER) {
          // insert into the current node
          node.insert(ix + 1, newNode.getBox(), newNode);
          node._updateUpward();
        } else {
          // split and insert
          result = _splitAndAdd(node, context, newNode);
        }
        return result;
      }
    }
  }

  private void _updateIndex(KEY key, VALUE value, Node<VALUE> node) {
    var ref = new IndexRef<VALUE>();
    ref.value = value;
    ref.node = node;
    indexKey.put(key, ref);
  }

  public static class ArrayIndexComparator implements Comparator<Integer> {
    private final RBox[] array;

    public ArrayIndexComparator(RBox[] array) {
      this.array = array;
    }

    @Override
    public int compare(Integer index1, Integer index2) {
      return (array[index1]).compareTo(array[index2]);
    }
  }

  private Node<VALUE> _splitAndAdd(Node<VALUE> node, InsertContext<KEY, VALUE> context, Node<VALUE> appendNode) {
    // rearange children to the distance index
    final var indexes = new Integer[ORDER + 1];
    final var boxes = new RBox[ORDER + 1];
    final var children = new Object[ORDER + 1];
    for (int i = 0; i < ORDER; i++) {
      indexes[i] = Integer.valueOf(i);
      boxes[i] = (RBox) node.boxes[i];
      children[i] = node.children[i];
    }
    indexes[ORDER] = ORDER;
    boxes[ORDER] = (appendNode != null) ? appendNode.getBox() : (RBox) (context.box);
    children[ORDER] = (appendNode != null) ? appendNode : context.value;

    // arange indexes by the order
    Arrays.sort(indexes, new ArrayIndexComparator(boxes));

    // split
    final var pivot = (ORDER + 2) >> 1;
    var newNode = new Node<VALUE>(ORDER);
    Arrays.fill(node.boxes, pivot, ORDER, null);
    Arrays.fill(node.children, pivot, ORDER, null);
    node.count = pivot;
    newNode.count = ORDER + 1 - pivot;
    newNode.parent = node.parent;
    // order nodes
    int newIndex = 0;
    for (int i = 0; i <= ORDER; i++) {
      var ix = indexes[i].intValue();
      if (ix == ORDER) {
        newIndex = i;
      }
      if (i < pivot) {
        node.boxes[i] = boxes[ix];
        node.children[i] = children[ix];
      } else {
        int j = i - pivot;
        newNode.boxes[j] = boxes[ix];
        newNode.children[j] = children[ix];
      }
    }
    if (appendNode == null) {
      // update index refs
      for (int i = 0; i < newNode.count; i++) {
        VALUE value = (VALUE) newNode.children[i];
        _updateIndex(toKey.apply(value), value, newNode);
      }
      if (newIndex < pivot) {
        // update new element index if needed
        VALUE value = (VALUE) node.children[newIndex];
        _updateIndex(toKey.apply(value), value, node);
      }
    } else {
      // update parents
      for (int i = 0; i < newNode.count; i++) {
        var child = newNode.getChild(i);
        child.parent = newNode;
      }
    }
    return newNode;
  }

  void _removeEmptyAndMerge(Node<VALUE> node) {
    if (node.count > 1) {
      for (int i = node.count - 2; i >= 0; i--) {
        var child = node.getChild(i);
        var child2 = node.getChild(i + 1);
        int count = child.count;
        if (count + child2.count <= ORDER) {
          child.merge(child2);
          node.delete(i + 1);
          if (!((child.children[0]) instanceof Node<?>)) {
            // level 0 - update index
            for (int l = child.count - 1; l >= count; l--) {
              VALUE value = (VALUE) child.children[l];
              _updateIndex(toKey.apply(value), value, child);
            }
          }
        } else if (count < (ORDER >> 1)) {
          // just move some nodes to distribute
          var pivot = (ORDER >> 1);
          // get some from the second node
          while (child.count < pivot) {
            var k = (RBox) child2.boxes[0];
            var v = child2.children[0];
            child.insert(ORDER, k, v);
            child2.delete(0);
            if (!(v instanceof Node<?>)) {
              _updateIndex(toKey.apply((VALUE) v), (VALUE) v, child);
            } else {
              // reparent the moved internal-node child
              ((Node<VALUE>) v).parent = child;
            }
          }
        }
      }
      node._updateBoxes();
    }
    if (node.parent != null) {
      _removeEmptyAndMerge(node.parent);
    }
  }

  private static <VALUE> void _search(Node<VALUE> node, int level, RBox box, Consumer<VALUE> consumer) {
    if (level == 0) {
      // values
      for (int i = 0; i < node.count; i++) {
        var b = node.getBox(i);
        switch (box.intersect(b)) {
          case CONTAINS, INTERSECTS -> consumer.accept(node.getValue(i));
          case NO_COLLISION -> {
            /* nothing to do */}
        }
      }
    } else {
      // nodes
      for (int i = 0; i < node.count; i++) {
        var b = node.getBox(i);
        switch (box.intersect(b)) {
          case CONTAINS -> _searchAll(node.getChild(i), level - 1, consumer);
          case INTERSECTS -> _search(node.getChild(i), level - 1, box, consumer);
          case NO_COLLISION -> {
            /* nothing to do */}
        }
      }
    }
  }

  private static <VALUE> void _searchAll(Node<VALUE> node, int level, Consumer<VALUE> consumer) {
    if (level == 0) {
      // values
      for (int i = 0; i < node.count; i++) {
        consumer.accept(node.getValue(i));
      }
    } else {
      // nodes
      for (int i = 0; i < node.count; i++) {
        _searchAll(node.getChild(i), level - 1, consumer);
      }
    }
  }

  private String _toString(Node<VALUE> node, int level, String indent) {
    StringBuilder s = new StringBuilder();

    if (level == 0) {
      for (int i = 0; i < node.count; i++) {
        s.append(indent + node.boxes[i] + ":" + node.getValue(i) + "\n");
      }
    } else {
      for (int i = 0; i < node.count; i++) {
        s.append(indent + node.boxes[i] + "\n");
        s.append(_toString(node.getChild(i), level - 1, indent + "     "));
      }
    }
    return s.toString();
  }

  /**
   * Debugging helper: asserts every node's {@code parent} back-pointer points at
   * its actual parent, and every stored child box <em>covers</em> the child's real
   * MBR. Coverage (not exact tightness) is the correctness-critical invariant: an
   * under-covering box would let a search wrongly prune a subtree that holds
   * matches. Stored boxes may be loose (over-covering) after cascading splits,
   * which only costs pruning efficiency, so this does not assert exact equality.
   */
  void _validateStructure() {
    if (root.parent != null) {
      throw new IllegalStateException("root parent must be null");
    }
    _validateNode(root, height);
  }

  private void _validateNode(Node<VALUE> node, int level) {
    if (level == 0) {
      return;
    }
    for (int i = 0; i < node.count; i++) {
      var child = node.getChild(i);
      if (child.parent != node) {
        throw new IllegalStateException("parent pointer mismatch at level " + level);
      }
      var stored = node.getBox(i);
      var actual = child.getBox();
      // stored must contain the child's true MBR (no under-coverage -> no false negatives)
      if (stored.intersect(actual) != IntersectResult.CONTAINS) {
        throw new IllegalStateException("stored box does not cover the child's MBR at level " + level);
      }
      _validateNode(child, level - 1);
    }
  }

  void _validateIndex() {
    var it = indexKey.range(null, null);
    next: while (it.hasNext()) {
      var ix = it.next();
      for (int i = 0; i < ix.node.count; i++) {
        if (ix.node.children[i] == ix.value) {
          continue next;
        }
      }
      throw new IllegalStateException("value not in the node");
    }
  }

  private static class IndexRef<VALUE> {
    VALUE value;
    Node<VALUE> node;
  }

  private static class Node<VALUE> {
    int count;
    Node<VALUE> parent;
    Object[] boxes;
    Object[] children;

    Node(int capacily) {
      count = 0;
      parent = null;
      boxes = new Object[capacily];
      children = new Object[capacily];
    }

    RBox getBox() {
      try {
        var box = ((RBox) boxes[0]).clone();
        for (int i = 1; i < count; i++) {
          ((RBox) boxes[i]).union(box);
        }
        return box;

      } catch (Exception e) {
        throw e;
      }
    }

    RBox getBox(int ix) {
      return (RBox) boxes[ix];
    }

    VALUE getValue(int ix) {
      return (VALUE) children[ix];
    }

    Node<VALUE> getChild(int ix) {
      return (Node<VALUE>) children[ix];
    }

    void insert(int ix, RBox box, Object value) {
      if (ix < count) {
        // insert
        System.arraycopy(boxes, ix, boxes, ix + 1, count - ix);
        System.arraycopy(children, ix, children, ix + 1, count - ix);
      } else {
        // append
        ix = count;
      }
      boxes[ix] = box;
      children[ix] = value;
      count++;
    }

    void delete(int ix) {
      if (count > 1 && ix + 1 < count) {
        System.arraycopy(boxes, ix + 1, boxes, ix, count - ix - 1);
        System.arraycopy(children, ix + 1, children, ix, count - ix - 1);
      }
      count--;
      boxes[count] = null;
      children[count] = null;
    }

    void delete(Object obj) {
      for (int ix = 0; ix < count; ix++) {
        if (children[ix] == obj) {
          delete(ix);
          return;
        }
      }
      throw new IllegalStateException("index is not consistent with node elements");
    }

    void _updateBoxes() {
      for (int i = 0; i < count; i++) {
        var b = getChild(i).getBox();
        boxes[i] = b;
      }
    }

    void _updateUpward() {
      _updateBoxes();
      if (parent != null) {
        parent._updateUpward();
      }
    }

    public void merge(Node<VALUE> secondNode) {
      int start = count;
      System.arraycopy(secondNode.boxes, 0, boxes, count, secondNode.count);
      System.arraycopy(secondNode.children, 0, children, count, secondNode.count);
      count += secondNode.count;
      // reparent any moved internal-node children to this node
      for (int i = start; i < count; i++) {
        if (children[i] instanceof Node) {
          ((Node<VALUE>) children[i]).parent = this;
        }
      }
      Arrays.fill(secondNode.boxes, 0, secondNode.count, null);
      Arrays.fill(secondNode.children, 0, secondNode.count, null);
      secondNode.count = 0;
    }
  }

  private static class InsertContext<KEY, VALUE> {
    KEY key;
    RBox box;
    VALUE value;
  }

  // At or below this level a task traverses sequentially instead of spawning
  // child tasks, so fork/join overhead does not dominate near the leaves.
  private static final int PARALLEL_CUTOFF_LEVEL = 1;

  private static class SearchAction<VALUE> extends RecursiveAction {
    private final Node<VALUE> node;
    private final int level;
    private final RBox box;
    private final Consumer<VALUE> consumer;

    public SearchAction(Node<VALUE> node, int level, RBox box, Consumer<VALUE> consumer) {
      this.node = node;
      this.level = level;
      this.box = box;
      this.consumer = consumer;
    }

    @Override
    protected void compute() {
      if (level <= PARALLEL_CUTOFF_LEVEL) {
        // small subtree - traverse sequentially
        _search(node, level, box, consumer);
        return;
      }
      // fan out: fork a task per intersecting child, then join them all
      var tasks = new ArrayList<RecursiveAction>();
      for (int i = 0; i < node.count; i++) {
        var b = node.getBox(i);
        switch (box.intersect(b)) {
          case CONTAINS -> tasks.add(new SearchAllAction<>(node.getChild(i), level - 1, consumer));
          case INTERSECTS -> tasks.add(new SearchAction<>(node.getChild(i), level - 1, box, consumer));
          case NO_COLLISION -> {
            /* nothing to do */}
        }
      }
      invokeAll(tasks);
    }
  }

  private static class SearchAllAction<VALUE> extends RecursiveAction {
    private final Node<VALUE> node;
    private final int level;
    private final Consumer<VALUE> consumer;

    public SearchAllAction(Node<VALUE> node, int level, Consumer<VALUE> consumer) {
      this.node = node;
      this.level = level;
      this.consumer = consumer;
    }

    @Override
    protected void compute() {
      if (level <= PARALLEL_CUTOFF_LEVEL) {
        _searchAll(node, level, consumer);
        return;
      }
      var tasks = new ArrayList<RecursiveAction>();
      for (int i = 0; i < node.count; i++) {
        tasks.add(new SearchAllAction<>(node.getChild(i), level - 1, consumer));
      }
      invokeAll(tasks);
    }
  }
}
