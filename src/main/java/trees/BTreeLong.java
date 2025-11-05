package trees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class BTreeLong<VALUE> {
  private final int ORDER;

  private Node<VALUE> root;
  private int height;
  private int size;
  private LeafNode<VALUE> level0;

  /**
   * Creates a BTree of given order (> 2).
   */
  public BTreeLong(int order) {
    if (order < 3) {
      throw new IllegalArgumentException("Order must be at least 3");
    }
    ORDER = order;
    level0 = new LeafNode<VALUE>(ORDER);
    root = level0;
  }

  /**
   * Removes all elements from the tree.
   */
  public void clear() {
    height = 0;
    size = 0;
    level0 = new LeafNode<VALUE>(ORDER);
    root = level0;
  }

  /**
   * @return true - if tree is empty.
   */
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * @return tree size - number elements inside.
   */
  public int size() {
    return size;
  }

  /**
   * @return tree height
   */
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
  public VALUE get(long key) {
    var context = new SearchContext<VALUE>();
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
  public VALUE put(long key, VALUE value) {
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
  public VALUE computeIfAbsent(long key, Supplier<VALUE> valueFunction) {
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
  public VALUE remove(long key) {
    var context = new SearchContext<VALUE>();
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
   * Iterate over values within key range.
   *
   * @param start start ot the search interval
   * @param end   end of the search interval
   * @return iterator for the assoiated values mathing the search interval
   * @throws IllegalArgumentException if {@code start} is greater than {@code end}
   */
  public Iterator<VALUE> range(long start, long end) {
    if (start > end) {
      throw new IllegalArgumentException("Value supplier cannot be null");
    }
    var context = new SearchContext<VALUE>();
    _search(root, height, start, context);
    // context.start = start;
    context.end = end;
    return context;
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

  private int treeNodeIx(int ix, long key, Node<VALUE> node) {
    if (ix < 0) {
      ix = -ix - 1;
    }
    if (ix >= node.count) {
      ix = node.count - 1;
    }
    if (ix > 0 && node.keys[ix] > key) {
      ix--;
    }
    return ix;
  }

  private void _search(Node<VALUE> node, int level, long key, SearchContext<VALUE> context) {
    int ix = Arrays.binarySearch(node.keys, 0, node.count, key);
    if (level == 0) {
      // value node
      context.node = (LeafNode<VALUE>) node;
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
      ix = treeNodeIx(ix, key, node);
      _search(node.getChild(ix), level - 1, key, context);
    }
  }

  private void _put(long key, InsertContext<VALUE> context) {
    Node<VALUE> u = insert(root, height, key, context);
    if (u != null) {
      // need to split root
      var newRoot = new Node<VALUE>(ORDER);
      newRoot.append(root.keys[0], root);
      newRoot.append(u.keys[0], u);
      root = newRoot;
      height++;
    }
  }

  private Node<VALUE> insert(Node<VALUE> node, int level, long key, InsertContext<VALUE> context) {
    int ix = Arrays.binarySearch(node.keys, 0, node.count, key);

    if (level == 0) {
      // value node
      if (ix >= 0 && ix < node.count) {
        // found the element - overwrite if needed
        if (context.value != null) {
          var t = node.getValue(ix);
          node.children[ix] = context.value;
          context.value = t;
        }
        return null;
      } else {
        if (ix < 0) {
          ix = -ix - 1;
        }
        // needs to insert/append
        if (context.value == null) {
          context.value = context.valueFunction.get();
          if (context.value == null) {
            throw new IllegalArgumentException("Supplied value cannot be null");
          }
        }
        size++;
        if (node.count < ORDER) {
          // insert into the current node
          if (ix >= node.count) {
            node.append(key, context.value);
          } else {
            node.insert(ix, key, context.value);
          }
          return null;
        } else {
          // split and insert
          var firstNode = (LeafNode<VALUE>) node;
          var secondNode = (LeafNode<VALUE>) splitAndAdd(node, level, key, context.value, ix);
          secondNode.next = firstNode.next;
          // secondNode.prev = firstNode;
          firstNode.next = secondNode;
          return secondNode;
        }
      }
    } else {
      // tree node
      ix = treeNodeIx(ix, key, node);
      var newNode = insert(node.getChild(ix), level - 1, key, context);
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
          return splitAndAdd(node, level, newNode.keys[0], newNode, ix + 1);
        }
      }
    }
  }

  private void _delete(Node<VALUE> node, int level, long key, SearchContext<VALUE> context) {
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
      ix = treeNodeIx(ix, key, node);
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
            if (firstNode.count + secondNode.count < ORDER) {
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

  private Node<VALUE> splitAndAdd(Node<VALUE> node, int level, long key, Object value, int ix) {
    // split
    final var pivot = (ORDER + 1) >> 1;
    Node<VALUE> newNode = (level == 0) ? new LeafNode<VALUE>(ORDER) : new Node<VALUE>(ORDER);
    System.arraycopy(node.keys, pivot, newNode.keys, 0, node.count - pivot);
    System.arraycopy(node.children, pivot, newNode.children, 0, node.count - pivot);
    node.count = pivot;
    newNode.count = ORDER - pivot;
    Arrays.fill(node.keys, pivot, ORDER, 0);
    Arrays.fill(node.children, pivot, ORDER, null);
    // add key-value
    if (ix < pivot) {
      node.insert(ix, key, value);
    } else {
      newNode.insert(ix - pivot, key, value);
    }
    return newNode;
  }

  /**
   * Returns a string representation of this B-tree (for debugging).
   *
   * @return a string representation of this B-tree.
   */
  public String toString() {
    return toString(root, height, "") + "\n";
  }

  private String toString(Node<VALUE> node, int level, String indent) {
    StringBuilder s = new StringBuilder();

    if (level == 0) {
      for (int i = 0; i < node.count; i++) {
        s.append(indent + node.keys[i] + ":" + node.getValue(i) + "\n");
      }
    } else {
      for (int i = 0; i < node.count; i++) {
        s.append(indent + "(" + node.keys[i] + ")\n");
        s.append(toString(node.getChild(i), level - 1, indent + "     "));
      }
    }
    return s.toString();
  }

  private static class Node<VALUE> {
    int count;
    long[] keys;
    Object[] children;

    Node(int capacily) {
      keys = new long[capacily];
      children = new Object[capacily];
      count = 0;
    }

    VALUE getValue(int ix) {
      return (VALUE) children[ix];
    }

    Node<VALUE> getChild(int ix) {
      return (Node<VALUE>) children[ix];
    }

    void append(long key, Object value) {
      keys[count] = key;
      children[count] = value;
      count++;
    }

    void insert(int ix, long key, Object value) {
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
      keys[count] = 0;
      children[count] = null;
    }

    public void merge(Node<VALUE> secondNode) {
      System.arraycopy(secondNode.keys, 0, keys, count, secondNode.count);
      System.arraycopy(secondNode.children, 0, children, count, secondNode.count);
      count += secondNode.count;
      Arrays.fill(secondNode.children, 0, secondNode.count, null);
      secondNode.count = 0;
    }
  }

  private static class LeafNode<VALUE> extends Node<VALUE> {
    LeafNode(int capacily) {
      super(capacily);
    }

    // LeafNode<VALUE> prev;
    LeafNode<VALUE> next;
  }

  private static class InsertContext<VALUE> {
    VALUE value;
    Supplier<VALUE> valueFunction;
  }

  private static class SearchContext<VALUE> implements Iterator<VALUE> {
    VALUE value;
    int index;
    LeafNode<VALUE> node;
    // long start;
    long end;

    @Override
    public boolean hasNext() {
      if (node == null) {
        return false;
      }
      if (index < node.count && node.keys[index] <= end) {
        return true;
      }
      return false;
    }

    @Override
    public VALUE next() {
      VALUE v = node.getValue(index);
      index++;
      if (index >= node.count) {
        index = 0;
        do {
          node = node.next;
        } while (node != null && node.count <= 0);
      }
      return v;
    }
  }
}
