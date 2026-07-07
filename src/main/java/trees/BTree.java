package trees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class BTree<KEY extends Comparable<KEY>, VALUE> {
  private final int ORDER;

  private Node<KEY, VALUE> root;
  private int height;
  private int size;
  private LeafNode<KEY, VALUE> level0;

  public BTree(int order) {
    if (order < 3) {
      throw new IllegalArgumentException("Order must be at least 3");
    }
    ORDER = order;
    level0 = new LeafNode<KEY, VALUE>(ORDER);
    root = level0;
  }

  public void clear() {
    height = 0;
    size = 0;
    level0 = new LeafNode<KEY, VALUE>(ORDER);
    root = level0;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public int size() {
    return size;
  }

  public int height() {
    return height;
  }

  /**
   * @return the smallest key in the tree
   * @throws NoSuchElementException if the tree is empty
   */
  public KEY getMinKey() {
    if (size == 0) {
      return null;
    }
    Node<KEY, VALUE> node = root;
    for (int l = height; l > 0; l--) {
      node = node.getChild(0);
    }
    return (KEY) node.keys[0];
  }

  /**
   * @return the largest key in the tree
   * @throws NoSuchElementException if the tree is empty
   */
  public KEY getMaxKey() {
    if (size == 0) {
      return null;
    }
    Node<KEY, VALUE> node = root;
    for (int l = height; l > 0; l--) {
      node = node.getChild(node.count - 1);
    }
    return (KEY) node.keys[node.count - 1];
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
    var context = new SearchContext<KEY, VALUE>();
    _search(root, height, key, context);
    return context.value;
  }

  /**
   * Store value associated with the given key.
   *
   * @param key   the key
   * @param value the value
   * @return the old value associated with the given key if the key is in the
   *         symbol
   *         table
   *         and {@code null} if the key is not in the symbol table
   * @throws IllegalArgumentException if {@code value} is {@code null}
   */
  public VALUE put(KEY key, VALUE value) {
    if (value == null) {
      throw new IllegalArgumentException("Value cannot be null");
    }
    var context = new InsertContext<VALUE>();
    context.value = value;
    _put(key, context);
    return context.value;
  }

  /**
   * Store the value associated with the given key.
   *
   * @param key           the key
   * @param valueFunction the value supplier function
   * @return the value associated with the given key if the key is in the symbol
   *         table
   *         and {@code null} if the key is not in the symbol table
   * @throws IllegalArgumentException if {@code value} is {@code null}
   */
  public VALUE computeIfAbsent(KEY key, Supplier<VALUE> valueFunction) {
    if (valueFunction == null) {
      throw new IllegalArgumentException("Value supplier cannot be null");
    }
    var context = new InsertContext<VALUE>();
    context.valueFunction = valueFunction;
    _put(key, context);
    return context.value;
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
    var context = new SearchContext<KEY, VALUE>();
    _delete(root, height, key, context);
    // check if we can lower the level
    while (root.count == 1 && height > 0) {
      var child = root.getChild(0);
      root = child;
      height--;
    }
    return context.value;
  }

  /**
   * Iterate over values within the key range, <b>inclusive on both ends</b>:
   * {@code [start, end]}. A {@code null} bound means unbounded on that side.
   *
   * @param start start of the search interval (inclusive), or {@code null} for no
   *              lower bound
   * @param end   end of the search interval (inclusive), or {@code null} for no
   *              upper bound
   * @return iterator for the associated values matching the search interval
   * @throws IllegalArgumentException if {@code start} is greater than {@code end}
   */
  public Iterator<VALUE> range(KEY start, KEY end) {
    if (start != null && end != null && start.compareTo(end) > 0) {
      throw new IllegalArgumentException("start must not be greater than end");
    }
    var context = new SearchContext<KEY, VALUE>();
    if (start != null) {
      _search(root, height, start, context);
    } else {
      // start from th first element
      context.node = (LeafNode<KEY, VALUE>) level0;
      context.index = 0;
      if (level0.count > 0) {
        context.value = level0.getValue(0);
      }
    }
    // context.start = start;
    context.end = end;
    return context;
  }

  /**
   * Iterate over key&rarr;value entries within the key range, inclusive on both
   * ends: {@code [start, end]}. A {@code null} bound means unbounded on that side.
   *
   * @param start start of the search interval (inclusive), or {@code null}
   * @param end   end of the search interval (inclusive), or {@code null}
   * @return iterator over {@link Entry} for the matching keys
   * @throws IllegalArgumentException if {@code start} is greater than {@code end}
   */
  public Iterator<Entry<KEY, VALUE>> rangeEntries(KEY start, KEY end) {
    if (start != null && end != null && start.compareTo(end) > 0) {
      throw new IllegalArgumentException("start must not be greater than end");
    }
    var context = new SearchContext<KEY, VALUE>();
    if (start != null) {
      _search(root, height, start, context);
    } else {
      context.node = (LeafNode<KEY, VALUE>) level0;
      context.index = 0;
      if (level0.count > 0) {
        context.value = level0.getValue(0);
      }
    }
    context.end = end;
    return context.entries();
  }

  /**
   * @return all values in a list
   */
  public List<VALUE> getAll() {
    List<VALUE> result = new ArrayList<>(size);
    var node = level0;
    int index = 0;
    int count = 0;

    while (count < size) {
      VALUE v = node.getValue(index);
      result.add(v);
      count++;
      if (index < node.count) {
        index++;
      }
      if (index == node.count) {
        node = node.next;
        index = 0;
      }
    }
    return result;
  }

  /**
   * Returns a string representation of this B-tree (for debugging).
   *
   * @return a string representation of this B-tree.
   */
  public String toString() {
    return _toString(root, height, "") + "\n";
  }

  private int _treeNodeIx(int ix, KEY key, Node<KEY, VALUE> node) {
    if (ix < 0) {
      ix = -ix - 1;
    }
    if (ix >= node.count) {
      ix = node.count - 1;
    }
    if (ix > 0 && key.compareTo((KEY) (node.keys[ix])) < 0) {
      ix--;
    }
    return ix;
  }

  private void _search(Node<KEY, VALUE> node, int level, KEY key, SearchContext<KEY, VALUE> context) {
    int ix = Arrays.binarySearch(node.keys, 0, node.count, key);
    if (level == 0) {
      // value node
      context.node = (LeafNode<KEY, VALUE>) node;
      if (ix >= 0) {
        // found the element
        context.value = node.getValue(ix);
      } else {
        // it is a miss
      }
      if (ix < 0) {
        ix = -ix - 1;
      }
      context.index = ix;
      return;
    } else {
      // tree node
      ix = _treeNodeIx(ix, key, node);
      _search(node.getChild(ix), level - 1, key, context);
    }
  }

  private void _put(KEY key, InsertContext<VALUE> context) {
    Node<KEY, VALUE> u = _insert(root, height, key, context);
    if (u != null) {
      // need to split root
      var newRoot = new Node<KEY, VALUE>(ORDER);
      newRoot.append(root.keys[0], root);
      newRoot.append(u.keys[0], u);
      root = newRoot;
      height++;
    }
  }

  private Node<KEY, VALUE> _insert(Node<KEY, VALUE> node, int level, KEY key, InsertContext<VALUE> context) {
    int ix = Arrays.binarySearch(node.keys, 0, node.count, key);

    if (level == 0) {
      // value node
      if (ix >= 0 && ix < node.count) {
        // found the element
        if (context.value != null) {
          // put: overwrite and return the previous value
          var t = node.getValue(ix);
          node.children[ix] = context.value;
          context.value = t;
        } else {
          // computeIfAbsent: key present, return the existing value (no overwrite)
          context.value = node.getValue(ix);
        }
        return null;
      } else {
        if (ix < 0) {
          ix = -ix - 1;
        }
        // new key: put supplies context.value; computeIfAbsent computes it.
        // Capture the mode before the shared context.value slot is overwritten.
        final boolean wasPut = context.value != null;
        if (context.value == null) {
          context.value = context.valueFunction.get();
          if (context.value == null) {
            throw new IllegalArgumentException("Supplied value cannot be null");
          }
        }
        size++;
        final VALUE inserted = context.value;
        Node<KEY, VALUE> result;
        if (node.count < ORDER) {
          // insert into the current node
          if (ix >= node.count) {
            node.append(key, inserted);
          } else {
            node.insert(ix, key, inserted);
          }
          result = null;
        } else {
          // split and insert
          var firstNode = (LeafNode<KEY, VALUE>) node;
          var secondNode = (LeafNode<KEY, VALUE>) _splitAndAdd(node, level, key, inserted, ix);
          secondNode.next = firstNode.next;
          // secondNode.prev = firstNode;
          firstNode.next = secondNode;
          result = secondNode;
        }
        if (wasPut) {
          // put of a new key has no previous value
          context.value = null;
        }
        return result;
      }
    } else {
      // tree node
      ix = _treeNodeIx(ix, key, node);
      var newNode = _insert(node.getChild(ix), level - 1, key, context);
      if (ix == 0) {
        // update index
        node.keys[ix] = node.getChild(ix).keys[0];
      }

      if (newNode == null) {
        return null;
      } else {
        // insert returned node as value in the current one
        if (node.count < ORDER) {
          // insert into the current node
          if (ix + 1 >= node.count) {
            node.append(newNode.keys[0], newNode);
          } else {
            node.insert(ix + 1, newNode.keys[0], newNode);
          }
          return null;
        } else {
          // split and insert
          return _splitAndAdd(node, level, newNode.keys[0], newNode, ix + 1);
        }
      }
    }
  }

  private void _delete(Node<KEY, VALUE> node, int level, KEY key, SearchContext<KEY, VALUE> context) {
    int ix = Arrays.binarySearch(node.keys, 0, node.count, key);
    if (level == 0) {
      // value node
      if (ix >= 0) {
        // found the element
        context.value = node.getValue(ix);
        node.delete(ix);
        size--;
      } else {
        // it is a miss
      }
      return;
    } else {
      // tree node
      ix = _treeNodeIx(ix, key, node);
      var child = node.getChild(ix);
      _delete(child, level - 1, key, context);
      if (context.value != null) {
        // tree has changed
        // check for merging
        if (node.count > 1) {
          for (int i = node.count - 1; i > 0; i--) {
            var firstNode = node.getChild(i - 1);
            var secondNode = node.getChild(i);
            // try to merge
            if (firstNode.count + secondNode.count <= ORDER) {
              // we have enough space to merge both nodes
              firstNode.merge(secondNode);
              if (level == 1) {
                ((LeafNode) firstNode).next = (((LeafNode) secondNode).next);
              }
              node.delete(i);
            } else if (firstNode.count < (ORDER >> 1)) {
              // just move some nodes to distribute
              var pivot = (ORDER >> 1);
              // get some from the second node
              while (firstNode.count < pivot) {
                var k = secondNode.keys[0];
                var v = secondNode.children[0];
                firstNode.append(k, v);
                secondNode.delete(0);
              }
              // update index
              node.keys[i] = secondNode.keys[0];
            }
          }
        }
      }
    }
  }

  private Node<KEY, VALUE> _splitAndAdd(Node<KEY, VALUE> node, int level, Object key, Object value, int ix) {
    // split
    final var pivot = (ORDER + 1) >> 1;
    Node<KEY, VALUE> newNode = (level == 0) ? new LeafNode<KEY, VALUE>(ORDER) : new Node<KEY, VALUE>(ORDER);
    System.arraycopy(node.keys, pivot, newNode.keys, 0, node.count - pivot);
    System.arraycopy(node.children, pivot, newNode.children, 0, node.count - pivot);
    node.count = pivot;
    newNode.count = ORDER - pivot;
    Arrays.fill(node.keys, pivot, ORDER, null);
    Arrays.fill(node.children, pivot, ORDER, null);
    // add key-value
    if (ix < pivot) {
      node.insert(ix, key, value);
    } else {
      newNode.insert(ix - pivot, key, value);
    }
    return newNode;
  }

  private String _toString(Node<KEY, VALUE> node, int level, String indent) {
    StringBuilder s = new StringBuilder();

    if (level == 0) {
      for (int i = 0; i < node.count; i++) {
        s.append(indent + node.keys[i] + ":" + node.getValue(i) + "\n");
      }
    } else {
      for (int i = 0; i < node.count; i++) {
        s.append(indent + "(" + node.keys[i] + ")\n");
        s.append(_toString(node.getChild(i), level - 1, indent + "     "));
      }
    }
    return s.toString();
  }

  private static class Node<KEY extends Comparable<KEY>, VALUE> {
    int count;
    Object[] keys;
    Object[] children;

    Node(int capacily) {
      keys = new Object[capacily];
      children = new Object[capacily];
      count = 0;
    }

    VALUE getValue(int ix) {
      return (VALUE) children[ix];
    }

    Node<KEY, VALUE> getChild(int ix) {
      return (Node<KEY, VALUE>) children[ix];
    }

    void append(Object key, Object value) {
      keys[count] = key;
      children[count] = value;
      count++;
    }

    void insert(int ix, Object key, Object value) {
      System.arraycopy(keys, ix, keys, ix + 1, count - ix);
      System.arraycopy(children, ix, children, ix + 1, count - ix);
      keys[ix] = key;
      children[ix] = value;
      count++;
    }

    void delete(int ix) {
      if (count > 1 && ix + 1 < count) {
        System.arraycopy(keys, ix + 1, keys, ix, count - ix - 1);
        System.arraycopy(children, ix + 1, children, ix, count - ix - 1);
      }
      count--;
      keys[count] = null;
      children[count] = null;
    }

    public void merge(Node<KEY, VALUE> secondNode) {
      System.arraycopy(secondNode.keys, 0, keys, count, secondNode.count);
      System.arraycopy(secondNode.children, 0, children, count, secondNode.count);
      count += secondNode.count;
      Arrays.fill(secondNode.children, 0, secondNode.count, null);
      secondNode.count = 0;
    }
  }

  private static class LeafNode<KEY extends Comparable<KEY>, VALUE> extends Node<KEY, VALUE> {
    LeafNode(int capacily) {
      super(capacily);
    }

    // LeafNode<VALUE> prev;
    LeafNode<KEY, VALUE> next;
  }

  /** A key&rarr;value pair yielded by {@link #rangeEntries(Comparable, Comparable)}. */
  public record Entry<KEY, VALUE>(KEY key, VALUE value) {
  }

  private static class InsertContext<VALUE> {
    VALUE value;
    Supplier<VALUE> valueFunction;
  }

  private static class SearchContext<KEY extends Comparable<KEY>, VALUE> implements Iterator<VALUE> {
    VALUE value;
    int index;
    LeafNode<KEY, VALUE> node;
    KEY end;

    @Override
    public boolean hasNext() {
      // roll past exhausted or empty leaves before checking the range bound;
      // _search can land us at index == count, and next leaves may be empty
      while (node != null && index >= node.count) {
        node = node.next;
        index = 0;
      }
      if (node == null) {
        return false;
      }
      return end == null || end.compareTo((KEY) (node.keys[index])) >= 0;
    }

    // advance past the current element (rolls over exhausted/empty leaves)
    private void step() {
      index++;
      if (index >= node.count) {
        index = 0;
        do {
          node = node.next;
        } while (node != null && node.count <= 0);
      }
    }

    @Override
    public VALUE next() {
      VALUE v = node.getValue(index);
      step();
      return v;
    }

    // entry view over the same cursor - shares hasNext/step so the range rollover
    // logic lives in one place
    Iterator<Entry<KEY, VALUE>> entries() {
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return SearchContext.this.hasNext();
        }

        @Override
        public Entry<KEY, VALUE> next() {
          var e = new Entry<KEY, VALUE>((KEY) node.keys[index], node.getValue(index));
          step();
          return e;
        }
      };
    }
  }
}
