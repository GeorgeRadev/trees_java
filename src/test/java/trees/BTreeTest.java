package trees;

import org.junit.jupiter.api.Test;

public class BTreeTest {
  boolean validateIndex = true;

  @Test
  public void testOrders() {
    test(3, 16);
    test(4, 16);
    test(8, 64);
    validateIndex = false;
    test(64, 150_000);
  }

  public void test(int order, int elementsCount) {
    var btree = new BTree<Long, String>(order);

    var elementValues = new long[elementsCount];

    // randomize elements
    for (int i = 0; i < elementsCount; i++) {
      elementValues[i] = i;
    }
    for (int i = 0; i < elementsCount; i++) {
      int i1 = (int) (Math.random() * elementsCount);
      int i2 = (int) (Math.random() * elementsCount);
      var t = elementValues[i1];
      elementValues[i1] = elementValues[i2];
      elementValues[i2] = t;
    }

    // insert compute
    for (int i = 0; i < elementsCount; i++) {
      var key = elementValues[i];
      var value = String.valueOf(elementValues[i]);
      // printTree(btree);
      // System.out.println("add: " + key);
      btree.computeIfAbsent(key, () -> value);
      validateIndex(btree);
      var stored = btree.get(key);
      if (!value.equals(stored)) {
        throw new RuntimeException("element not stored");
      }
    }
    if (elementsCount != btree.size()) {
      throw new RuntimeException("count does not match");
    }

    { // test range
      long start = elementsCount >> 2;
      long end = start * 3;
      String oldV = "-1";
      int c = 0;

      var it = btree.range(start, end);
      while (it.hasNext()) {
        var v = it.next();
        if (Long.parseLong(v) < Long.parseLong(oldV)) {
          throw new RuntimeException("iterator should be incremental");
        }
        oldV = v;
        c++;
      }
      if (c != end - start) {
        throw new RuntimeException("range does not match");
      }
    }

    { // test range
      long end = elementsCount >> 2;
      String oldV = "-1";
      int c = 0;

      var it = btree.range(null, end);
      while (it.hasNext()) {
        var v = it.next();
        if (Long.parseLong(v) < Long.parseLong(oldV)) {
          throw new RuntimeException("iterator should be incremental");
        }
        oldV = v;
        c++;
      }
      if (c != end) {
        throw new RuntimeException("range does not match");
      }
    }

    // hit
    for (int i = 0; i < 5; i++) {
      int ix = (int) (Math.random() * elementsCount);
      var v = elementValues[ix];
      if (btree.get(v) == null) {
        throw new RuntimeException("should be hit");
      }
    }
    // miss
    for (int i = 0; i < 5; i++) {
      var v = i + elementsCount + 2;
      if (btree.get((long) v) != null) {
        throw new RuntimeException("should be miss");
      }
    }

    validateIndex(btree);

    // delete sequential
    for (int i = 0; i < elementsCount; i++) {
      // printTree(btree);
      // System.out.println("delete: " + i);
      btree.remove((long) i);
      validateIndex(btree);
      // printTree(btree);
      var value = btree.get((long) i);
      if (value != null) {
        throw new RuntimeException("element not deleted");
      }
    }
    if (0 != btree.size()) {
      throw new RuntimeException("count after deletion does not match");
    }

    // insert direct
    for (int i = 0; i < elementsCount; i++) {
      var key = elementValues[i];
      var value = String.valueOf(elementValues[i]);
      // printTree(btree);
      // System.out.println("add: " + key);
      btree.put(key, value);
      validateIndex(btree);
      var stored = btree.get(key);
      if (!value.equals(stored)) {
        throw new RuntimeException("element not stored");
      }
    }
    if (elementsCount != btree.size()) {
      throw new RuntimeException("count does not match");
    }

    // delete in insert order
    for (int i = 0; i < elementsCount; i++) {
      var key = elementValues[i];
      // printTree(btree);
      // System.out.println("delete: " + key);
      btree.remove(key);
      // printTree(btree);
      var value = btree.get(key);
      if (value != null) {
        throw new RuntimeException("element not deleted");
      }
    }
    if (0 != btree.size()) {
      throw new RuntimeException("count after deletion does not match");
    }
    {
      // insert for clear
      int count = Math.min(elementsCount, 10);
      for (int i = 0; i < count; i++) {
        var key = elementValues[i];
        var value = String.valueOf(elementValues[i]);
        btree.put(key, value);
        var stored = btree.get(key);
        if (!value.equals(stored)) {
          throw new RuntimeException("element not stored");
        }
      }
      if (count != btree.size()) {
        throw new RuntimeException("count does not match");
      }
      var elements = btree.getAll();
      if (count != elements.size()) {
        throw new RuntimeException("count does not match");
      }
      for (var element : elements) {
        if (element == null) {
          throw new RuntimeException("element should not be null");
        }
      }
      validateIndex(btree);

      btree.clear();
      if (0 != btree.size()) {
        throw new RuntimeException("count does not match");
      }
    }

    btree.computeIfAbsent((long) 999, () -> "zzz");
    if (!btree.get((long) 999).equals("zzz")) {
      throw new RuntimeException("if missing failed");
    }

    try {
      btree.put((long) 3, null);
    } catch (IllegalArgumentException e) {
      // ok
    }

    try {
      btree.computeIfAbsent((long) 3, null);
    } catch (IllegalArgumentException e) {
      // ok
    }
    try {
      btree.computeIfAbsent((long) 3, () -> null);
    } catch (IllegalArgumentException e) {
      // ok
    }

    // printTree(btree);
  }

  private void validateIndex(BTree<Long, String> btree) {
    if(!validateIndex){
      return;
    }
    long prev = -1;
    var it = btree.range(prev, Long.MAX_VALUE);
    while (it.hasNext()) {
      var n = it.next();
      long v = Long.parseLong(n);
      if (v <= prev) {
        throw new RuntimeException("iterator should be incremental");
      }
      prev = v;
    }
  }

  static void printTree(BTreeLong<?> btree) {

    System.out.println("-----------------------");
    System.out.println(btree);
    System.out.println("size:    " + btree.size());
    System.out.println("height:  " + btree.height());
    System.out.println("-----------------------");
    System.out.println();
  }
}
