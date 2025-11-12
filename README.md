# Java balanced Trees

This project contains  the following implementations:  
* BTreeLong - **Btree+** implementation optimized for **long** key
* ConcurrentBTreeLong - ReadWrite locked **Btree+** with **long** key
* BTree - generic **Btree+ <Key, Value>** implementation
* RTree - generic **RTree <Value>** implementation

## RTree specifics

**RTree** implementation requires:  
* Generic **Value** type 
* lambda for extracting **key** from **Value**
* lambda for extracting **RBox** implementation from **Value**

Internaly a BTree of the same order is used to store Keys to Values and nodes information.  
The Boxes are stored in RTree and each level has the union of the child Boxes.  
The last level is a reference to the Values.  
Parallel intersection is done via ForkJoinPool.  

**RBox** implements following interface:  
* RBox **clone()** - for creating a copy of a Box
* void **union(RBox box)** - for extending a box to cover given box
*IntersectResult **intersect(RBox box)** - returns [**CONTAINS**, **INTERSECTS**, **NO_COLLISION**] to decide where to look for elements while traversing the tree

