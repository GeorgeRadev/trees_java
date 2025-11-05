package trees;

import java.util.Arrays;
import java.util.Comparator;
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
   * @param box box to intersect with.
   * @return Array of values mathing the box.
   */
  public void intersect(RBox box, Consumer<VALUE> consumer) {
    _search(root, height, box, consumer);
  }

  /**
   * @return all values from the tree.
   */
  public void getAll(Consumer<VALUE> consumer) {
    _searchAll(root, height, consumer);
  }

  /**
   * Returns a string representation of this B-tree (for debugging).
   *
   * @return a string representation of this B-tree.
   */
  public String toString() {
    return _toString(root, height, "") + "\n";
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
      newRoot.append(root.getBox(), root);
      newRoot.append(newNode.getBox(), newNode);
      root.parent = newRoot;
      newNode.parent = newRoot;
      root = newRoot;
      height++;
    }
    return oldValue;
  }

  // return new node if added
  private Node<VALUE> _insert(Node<VALUE> node, int level, InsertContext<KEY, VALUE> context) {
    if (level == 0) {
      // value node
      if (node.count < ORDER) {
        // insert into the current node
        node.append(context.box, context.value);
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
        ix = Arrays.binarySearch(node.boxes, 0, node.count, box);
        if (ix < 0) {
          ix = -ix - 1;
        }
        if (ix >= node.count) {
          ix = node.count - 1;
        }
        // if (ix > 0 && ((RBox) node.boxes[ix]).compareTo(context.box) > 0) {
        //   ix--;
        // }
      }
      // insert at position ix
      var newNode = _insert(node.getChild(ix), level - 1, context);

      if (newNode == null) {
        return null;
      } else {
        Node<VALUE> result = null;
        // insert returned node as value in the current one
        if (node.count < ORDER) {
          // insert into the current node
          node.append(newNode.getBox(0), newNode);
          node._updateUpward();
        } else {
          // split and insert
          result = _splitAndAdd(node, context, newNode);
          result.parent = node;
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
      return (array[index2]).compareTo(array[index1]);
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
            child.append(k, v);
            child2.delete(0);
            if (!(v instanceof Node<?>)) {
              _updateIndex(toKey.apply((VALUE) v), (VALUE) v, child);
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

  private void _search(Node<VALUE> node, int level, RBox box, Consumer<VALUE> consumer) {
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

  private void _searchAll(Node<VALUE> node, int level, Consumer<VALUE> consumer) {
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

    void append(RBox box, Object value) {
      boxes[count] = box;
      children[count] = value;
      count++;
    }

    // void insert(int ix, RBox box, Object value) {
    // System.arraycopy(boxes, ix, boxes, ix + 1, count - ix);
    // System.arraycopy(children, ix, children, ix + 1, count - ix);
    // boxes[ix] = box;
    // children[ix] = value;
    // count++;
    // }

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
      System.arraycopy(secondNode.boxes, 0, boxes, count, secondNode.count);
      System.arraycopy(secondNode.children, 0, children, count, secondNode.count);
      count += secondNode.count;
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
}
