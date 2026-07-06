package trees;

public interface RBox {

  /* create a copy of this box */
  public RBox clone();

  /* union with box and write into box (mutates the argument to also cover this) */
  public void union(RBox box);

  enum IntersectResult {
    CONTAINS, // contains the target box
    INTERSECTS, // intersects with the target box
    NO_COLLISION // no overlaping with the target box
  }

  public IntersectResult intersect(RBox box);

  /** Measure of this box: length (1-D), area (2-D), volume (3-D). Must be &gt;= 0. */
  public long measure();

  /** Volume of the overlap between this and {@code box}; 0 if disjoint. Must be &gt;= 0. */
  public long intersectionVolume(RBox box);

  /** Increase in measure if this box were enlarged to also cover {@code box}. */
  default long enlargement(RBox box) {
    RBox u = this.clone(); // u covers this
    box.union(u); // u now also covers box (union mutates the argument)
    return u.measure() - this.measure();
  }
}
