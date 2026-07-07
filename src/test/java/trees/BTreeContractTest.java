package trees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Behavioral contract shared by every long-keyed B+tree implementation.
 *
 * <p>{@link BTree} and {@link BTreeLong} are deliberately duplicated —
 * {@code BTreeLong} specializes primitive {@code long} keys to avoid boxing — so
 * the same logic (and, historically, the same bugs) lives in both. Every case
 * here runs against <b>both</b> via {@link LongTree} adapters, so a fix or change
 * applied to one implementation but not the other shows up as a red build.
 * {@code ConcurrentBTreeLong} is covered too, since it inherits {@code BTreeLong}.
 */
public class BTreeContractTest {

  /** Minimal long-keyed map surface, so one test can drive every implementation. */
  interface LongTree<V> {
    V get(long key);

    V put(long key, V value);

    V computeIfAbsent(long key, Supplier<V> valueFunction);

    V remove(long key);

    Iterator<V> range(long start, long end);

    List<V> getAll();

    int size();

    long getMinKey();

    long getMaxKey();

    List<String> entriesInRange(long start, long end);
  }

  private static LongTree<String> btree(int order) {
    return new LongTree<>() {
      final BTree<Long, String> t = new BTree<>(order);

      public String get(long k) {
        return t.get(k);
      }

      public String put(long k, String v) {
        return t.put(k, v);
      }

      public String computeIfAbsent(long k, Supplier<String> f) {
        return t.computeIfAbsent(k, f);
      }

      public String remove(long k) {
        return t.remove(k);
      }

      public Iterator<String> range(long s, long e) {
        return t.range(s, e);
      }

      public List<String> getAll() {
        return t.getAll();
      }

      public int size() {
        return t.size();
      }

      public long getMinKey() {
        return t.getMinKey();
      }

      public long getMaxKey() {
        return t.getMaxKey();
      }

      public List<String> entriesInRange(long s, long e) {
        var out = new ArrayList<String>();
        var it = t.rangeEntries(s, e);
        while (it.hasNext()) {
          var en = it.next();
          out.add(en.key() + "=" + en.value());
        }
        return out;
      }
    };
  }

  private static LongTree<String> btreeLong(int order) {
    return new LongTree<>() {
      final BTreeLong<String> t = new BTreeLong<>(order);

      public String get(long k) {
        return t.get(k);
      }

      public String put(long k, String v) {
        return t.put(k, v);
      }

      public String computeIfAbsent(long k, Supplier<String> f) {
        return t.computeIfAbsent(k, f);
      }

      public String remove(long k) {
        return t.remove(k);
      }

      public Iterator<String> range(long s, long e) {
        return t.range(s, e);
      }

      public List<String> getAll() {
        return t.getAll();
      }

      public int size() {
        return t.size();
      }

      public long getMinKey() {
        return t.getMinKey();
      }

      public long getMaxKey() {
        return t.getMaxKey();
      }

      public List<String> entriesInRange(long s, long e) {
        var out = new ArrayList<String>();
        var it = t.rangeEntries(s, e);
        while (it.hasNext()) {
          var en = it.next();
          out.add(en.key() + "=" + en.value());
        }
        return out;
      }
    };
  }

  /** Run one contract body against every implementation as separate dynamic tests. */
  private Stream<DynamicTest> contract(String name, int order, Consumer<LongTree<String>> body) {
    return Stream.of(
        DynamicTest.dynamicTest("BTree[" + order + "]: " + name, () -> body.accept(btree(order))),
        DynamicTest.dynamicTest("BTreeLong[" + order + "]: " + name, () -> body.accept(btreeLong(order))));
  }

  private static List<String> drain(Iterator<String> it) {
    var out = new ArrayList<String>();
    while (it.hasNext()) {
      out.add(it.next());
    }
    return out;
  }

  @TestFactory
  Stream<DynamicTest> putReturnValues() {
    return contract("put returns null for a new key, old value for an existing key", 4, t -> {
      assertNull(t.put(1, "a"), "put of a new key must return null");
      assertEquals("a", t.put(1, "b"), "put of an existing key must return the old value");
      assertEquals("b", t.get(1));
    });
  }

  @TestFactory
  Stream<DynamicTest> computeIfAbsentReturnValues() {
    return contract("computeIfAbsent returns computed value / existing value", 4, t -> {
      assertEquals("x", t.computeIfAbsent(1, () -> "x"), "new key must return the computed value");
      assertEquals("x", t.computeIfAbsent(1, () -> fail("supplier must not run for an existing key")),
          "existing key must return the stored value");
    });
  }

  @TestFactory
  Stream<DynamicTest> nullValuesRejected() {
    return contract("null value is rejected", 4, t -> {
      assertThrows(IllegalArgumentException.class, () -> t.put(1, null));
      assertThrows(IllegalArgumentException.class, () -> t.computeIfAbsent(1, () -> null));
    });
  }

  @TestFactory
  Stream<DynamicTest> rangeInclusiveOnBothEnds() {
    return contract("range is inclusive on both ends", 4, t -> {
      for (long i = 0; i <= 10; i++) {
        t.put(i, String.valueOf(i));
      }
      assertEquals(List.of("3", "4", "5", "6", "7"), drain(t.range(3, 7)));
      assertEquals(List.of("0"), drain(t.range(0, 0)));
      assertEquals(List.of("10"), drain(t.range(10, 10)));
    });
  }

  @TestFactory
  Stream<DynamicTest> rangeStartInGapCrossesLeaves() {
    return contract("range whose start falls in a gap still finds later leaves", 3, t -> {
      final int n = 40; // even keys 0,2,...,78 across many order-3 leaves
      for (int i = 0; i < n; i++) {
        t.put(i * 2, String.valueOf(i * 2));
      }
      for (int start = 1; start < 2 * n; start += 2) { // every odd start lands in a gap
        var got = drain(t.range(start, 2L * n));
        int expected = 0;
        for (int k = start + 1; k < 2 * n; k++) {
          if (k % 2 == 0) {
            expected++;
          }
        }
        assertEquals(expected, got.size(), "range(start=" + start + ")");
      }
    });
  }

  @TestFactory
  Stream<DynamicTest> rangeFullSpanReturnsAllSorted() {
    return contract("range over the full long span returns every value in order", 3, t -> {
      int[] keys = { 7, 1, 9, 3, 5, 0, 8, 2, 6, 4 };
      for (int k : keys) {
        t.put(k, String.valueOf(k));
      }
      assertEquals(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
          drain(t.range(Long.MIN_VALUE, Long.MAX_VALUE)));
    });
  }

  @TestFactory
  Stream<DynamicTest> getAllReturnsEverySortedValue() {
    return contract("getAll returns every value in ascending key order", 3, t -> {
      int[] keys = { 7, 1, 9, 3, 5, 0, 8, 2, 6, 4 };
      for (int k : keys) {
        t.put(k, String.valueOf(k));
      }
      assertEquals(10, t.size());
      assertEquals(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), t.getAll());
    });
  }

  @TestFactory
  Stream<DynamicTest> rangeEntriesYieldsKeyAndValue() {
    return contract("rangeEntries yields key=value pairs inclusive on both ends", 4, t -> {
      for (long i = 0; i <= 10; i++) {
        t.put(i, "v" + i);
      }
      assertEquals(List.of("3=v3", "4=v4", "5=v5", "6=v6", "7=v7"), t.entriesInRange(3, 7));
      assertEquals(List.of("0=v0"), t.entriesInRange(0, 0));
      assertEquals(List.of(), t.entriesInRange(100, 200));
    });
  }

  @TestFactory
  Stream<DynamicTest> minMaxKey() {
    return contract("getMinKey/getMaxKey after shuffled inserts and a delete", 3, t -> {
      int[] keys = { 7, 1, 9, 3, 5, 0, 8, 2, 6, 4 };
      for (int k : keys) {
        t.put(k, String.valueOf(k));
      }
      assertEquals(0, t.getMinKey());
      assertEquals(9, t.getMaxKey());
      t.remove(0);
      t.remove(9);
      assertEquals(1, t.getMinKey(), "min tracks after deleting the smallest");
      assertEquals(8, t.getMaxKey(), "max tracks after deleting the largest");
    });
  }

  @TestFactory
  Stream<DynamicTest> deleteFromBothEndsKeepsStructureConsistent() {
    return contract("deleting alternately from min/max keeps getAll, size, min, max consistent", 3, t -> {
      final int n = 50;
      for (int i = 0; i < n; i++) {
        t.put(i, String.valueOf(i));
      }
      var remaining = new java.util.TreeSet<Long>();
      for (long i = 0; i < n; i++) {
        remaining.add(i);
      }
      boolean fromLow = true;
      while (!remaining.isEmpty()) {
        long k = fromLow ? remaining.first() : remaining.last();
        fromLow = !fromLow;
        t.remove(k);
        remaining.remove(k);
        // no stranded empty node may perturb size / order / extremes
        assertEquals(remaining.size(), t.size());
        var expected = new ArrayList<String>();
        for (long r : remaining) {
          expected.add(String.valueOf(r));
        }
        assertEquals(expected, t.getAll());
        if (!remaining.isEmpty()) {
          assertEquals(remaining.first().longValue(), t.getMinKey());
          assertEquals(remaining.last().longValue(), t.getMaxKey());
        }
      }
    });
  }

  @TestFactory
  Stream<DynamicTest> removeBehavior() {
    return contract("remove returns the value and shrinks the tree; missing key returns null", 4, t -> {
      for (long i = 0; i < 10; i++) {
        t.put(i, String.valueOf(i));
      }
      assertEquals("5", t.remove(5));
      assertNull(t.get(5));
      assertEquals(9, t.size());
      assertNull(t.remove(100), "removing an absent key returns null");
    });
  }
}
