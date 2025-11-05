package trees;

public interface RBox extends Comparable {

  /* union with box and write into box */
  public RBox clone();

  /* union with box and write into box */
  public void union(RBox box);

  enum IntersectResult {
    CONTAINS, // contains the target box
    INTERSECTS, // intersects with the target box
    NO_COLLISION // no overlaping with the target box
  }

  public IntersectResult intersect(RBox box);
}
