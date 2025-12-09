package trees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

public class RTreeTest {
  boolean validateIndex = true;

  @Test
  public void testRtreeOrders() {
    test(3, 16);
    test(4, 16);
    test(8, 64);
    validateIndex = false;
    test(64, 150_000);
  }

  public static class RangeBox implements RBox {
    int s, e;

    public RangeBox(int s, int e) {
      if (s > e) {
        throw new IllegalArgumentException("start must be smaller than end");
      }
      this.s = s;
      this.e = e;
    }

    @Override
    public String toString() {
      return "[" + s + "-" + e + "]";
    }

    @Override
    public RBox clone() {
      return new RangeBox(s, e);
    }

    @Override
    public void union(RBox box) {
      ((RangeBox) box).s = Math.min(s, ((RangeBox) box).s);
      ((RangeBox) box).e = Math.max(e, ((RangeBox) box).e);
    }

    @Override
    public IntersectResult intersect(RBox box) {
      var bs = ((RangeBox) box).s;
      var be = ((RangeBox) box).e;
      if (bs >= s && be <= e) {
        return IntersectResult.CONTAINS;
      } else if (be < s || e < be) {
        return IntersectResult.NO_COLLISION;
      } else {
        return IntersectResult.INTERSECTS;
      }
    }

    @Override
    public int compareTo(Object o) {
      var b = ((RangeBox) o);
      if (s == b.s) {
        return e - b.e;
      } else {
        return s - b.s;
      }
    }
  }

  public static class Range implements Comparable {
    String id;
    int s, e;

    public Range(String id, int s, int e) {
      if (s > e) {
        throw new IllegalArgumentException("start must be smaller than end");
      }
      this.id = id;
      this.s = s;
      this.e = e;
    }

    @Override
    public String toString() {
      return "(" + id + ")[" + s + "-" + e + "]";
    }

    @Override
    public int compareTo(Object o) {
      return id.compareTo(((Range) o).id);
    }

    public static RangeBox toRangeBox(Range r) {
      return new RangeBox(r.s, r.e);
    }

    public static String toRangeKey(Range r) {
      return r.id;
    }
  }

  public void testRangeBox() {
    {
      var a = new RangeBox(0, 98);
      var b = new RangeBox(93, 139);
      var boxes = new RangeBox[] { a, b };
      var box = new RangeBox(10, 120);

      var ix = RTree.binarySearch(boxes, 0, boxes.length, box);
      assertEquals(0, ix);
    }
    {
      var a = new RangeBox(0, 98);
      var b = new RangeBox(93, 139);
      var boxes = new RangeBox[] { a, b };
      var box = new RangeBox(153, 181);

      var ix = RTree.binarySearch(boxes, 0, boxes.length, box);
      assertEquals(1, ix);
    }
    {
      var a = new RangeBox(0, 98);
      var b = new RangeBox(93, 139);
      var c = new RangeBox(120, 180);
      var boxes = new RangeBox[] { a, b, c };
      var box = new RangeBox(153, 181);

      var ix = RTree.binarySearch(boxes, 0, boxes.length, box);
      assertEquals(2, ix);
    }
  }

  public void test(int order, int elementsCount) {
    testRangeBox();

    var rtree = new RTree<String, Range>(order, Range::toRangeKey, Range::toRangeBox);

    // init elements
    var elementValues = new Range[elementsCount];
    for (int i = 0; i < elementsCount; i++) {
      int start = 10 * i + (int) (Math.random() * 5);
      int end = start + 1 + (int) (Math.random() * 30);
      elementValues[i] = new Range(String.valueOf(i), start, end);
    }

    // randomize elements
    for (int i = 0; i < elementsCount; i++) {
      int i1 = (int) (Math.random() * elementsCount);
      int i2 = (int) (Math.random() * elementsCount);
      var t = elementValues[i1];
      elementValues[i1] = elementValues[i2];
      elementValues[i2] = t;
    }

    // insert
    for (int i = 0; i < elementsCount; i++) {
      // System.out.println("before add " + elementValues[i].id);
      // printTree(rtree);
      int s = rtree.size();
      rtree.add(elementValues[i]);
      if ((s + 1) != rtree.size()) {
        throw new RuntimeException("element not stored");
      }
      // System.out.println("after add " + elementValues[i].id);
      // printTree(rtree);
      validateIndex(rtree);
      var stored = rtree.get(elementValues[i].id);
      if (!elementValues[i].id.equals(stored.id)) {
        throw new RuntimeException("element not stored");
      }
    }
    if (elementsCount != rtree.size()) {
      throw new RuntimeException("count does not match");
    }

    { // test all
      final int[] counter = new int[] { 0 };
      Consumer<Range> consumer = (e) -> {
        counter[0]++;
      };

      rtree.getAll(consumer);
      if (counter[0] != elementsCount || rtree.size() != elementsCount) {
        throw new RuntimeException("count does not match");
      }
    }

    { // test all parallel
      final int[] counter = new int[] { 0 };
      Consumer<Range> consumer = (e) -> {
        counter[0]++;
      };

      rtree.getAllParallel(consumer);
      if (counter[0] != elementsCount || rtree.size() != elementsCount) {
        throw new RuntimeException("count does not match");
      }
    }

    { // test range
      int start = elementsCount >> 2;
      int end = start * 3;
      var box = new RangeBox(10 * start, 10 * end);

      final int[] counter = new int[] { 0 };
      {
        Consumer<Range> consumer = (e) -> {
          counter[0]++;
        };

        rtree.intersect(box, consumer);
        if (counter[0] >= elementsCount) {
          throw new RuntimeException("range does not match");
        }
      }
      final var counterParallel = new AtomicInteger(0);
      {
        Consumer<Range> consumer = (e) -> {
          counterParallel.incrementAndGet();
        };

        rtree.intersectParallel(box, consumer);
        if (counterParallel.get() >= elementsCount) {
          throw new RuntimeException("range does not match");
        }
      }
      final var counterParallelN = new AtomicInteger(0);
      {
        Consumer<Range> consumer = (e) -> {
          counterParallelN.incrementAndGet();
        };

        rtree.intersectParallel(box, consumer, 8);
        if (counterParallelN.get() >= elementsCount) {
          throw new RuntimeException("range does not match");
        }
      }
      if (counter[0] != counterParallel.get()) {
        throw new RuntimeException("search does not match");
      }
      if (counter[0] != counterParallelN.get()) {
        throw new RuntimeException("search does not match");
      }
    }

    // hit
    for (int i = 0; i < 5; i++) {
      int ix = (int) (Math.random() * elementsCount);
      var key = elementValues[ix].id;
      if (rtree.get(key) == null) {
        throw new RuntimeException("should be hit");
      }
    }
    // miss
    for (int i = 0; i < 5; i++) {
      var v = String.valueOf(i + elementsCount + 2);
      if (rtree.get(v) != null) {
        throw new RuntimeException("should be miss");
      }
    }

    validateIndex(rtree);

    // delete sequential
    for (int i = 0; i < elementsCount; i++) {
      var key = elementValues[i].id;
      // System.out.println("before delete " + key);
      // printTree(rtree);
      rtree.removeByValue(elementValues[i]);
      validateIndex(rtree);
      // System.out.println("after delete " + key);
      // printTree(rtree);
      var value = rtree.get(key);
      if (value != null) {
        throw new RuntimeException("element not deleted");
      }
    }
    if (0 != rtree.size()) {
      throw new RuntimeException("count after deletion does not match");
    }

    // insert
    for (int i = 0; i < elementsCount; i++) {
      var value = elementValues[i];
      // System.out.println("add: " + value);
      // printTree(rtree);
      rtree.add(value);
      var key = value.id;
      var stored = rtree.get(key);
      if (!value.equals(stored)) {
        throw new RuntimeException("element not stored");
      }
    }
    if (elementsCount != rtree.size()) {
      throw new RuntimeException("count does not match");
    }

    // delete in insert order
    for (int i = 0; i < elementsCount; i++) {
      var value = elementValues[i];
      // printTree(btree);
      // System.out.println("delete: " + value);
      rtree.remove(value.id);
      // printTree(btree);
      var v = rtree.get(value.id);
      if (v != null) {
        throw new RuntimeException("element not deleted");
      }
    }
    if (0 != rtree.size()) {
      throw new RuntimeException("count after deletion does not match");
    }
    {
      // insert for clear
      int count = Math.min(elementsCount, 10);
      for (int i = 0; i < count; i++) {
        var key = elementValues[i].id;
        rtree.add(elementValues[i]);
        var stored = rtree.get(key);
        if (!key.equals(stored.id)) {
          throw new RuntimeException("element not stored");
        }
      }
      if (count != rtree.size()) {
        throw new RuntimeException("count does not match");
      }
      {
        final int[] counter = new int[] { 0 };
        Consumer<Range> consumer = (e) -> {
          if (e == null) {
            throw new RuntimeException("element should not be null");
          }
          counter[0]++;
        };

        rtree.getAll(consumer);
        if (count != counter[0]) {
          throw new RuntimeException("count does not match");
        }
      }

      rtree.clear();
      if (0 != rtree.size()) {
        throw new RuntimeException("count does not match");
      }
    }

    try {
      rtree.add(null);
    } catch (IllegalArgumentException e) {
      // ok
    }

    // printTree(btree);
  }

  void validateIndex(RTree<?, ?> rtree) {
    if (validateIndex) {
      rtree._validateIndex();
    }
  }

  static void printTree(RTree<?, ?> rtree) {
    System.out.println("-----------------------");
    System.out.println("size:    " + rtree.size());
    System.out.println("height:  " + rtree.height());
    System.out.print(rtree);
    System.out.println("-----------------------");
    System.out.println();
  }
}
