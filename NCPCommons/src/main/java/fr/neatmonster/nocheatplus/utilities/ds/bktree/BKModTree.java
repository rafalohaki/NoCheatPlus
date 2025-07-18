/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.utilities.ds.bktree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree.LookupEntry;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree.Node;

/**
 * BK tree for int distances.
 * @author mc_dev
 *
 */
public abstract class BKModTree<V, N extends Node<V, N>, L extends LookupEntry<V, N>>{
	
       // Possible extension: support equality-based lookup rather than only distance.
       // Distance 0 may be treated as an exact match.
	
	/**
	 * Fat defaultimpl. it iterates over all Children
	 * @author mc_dev
	 *
	 * @param <V>
	 * @param <N>
	 */
        public static abstract class Node<V, N extends Node<V, N>>{
                private V value;

                public Node(V value){
                        this.value = value;
                }

                public V getValue() {
                        return value;
                }

                public void setValue(V value) {
                        this.value = value;
                }
		public abstract N putChild(final int distance, final N child);
		
		public abstract N getChild(final int distance);
		
		public abstract boolean hasChild(int distance);
		
		public abstract Collection<N> getChildren(final int distance, final int range, final Collection<N> nodes);
	}
	
	/**
	 * Node using a map as base, with basic implementation.
	 * @author mc_dev
	 *
	 * @param <V>
	 * @param <N>
	 */
	public static abstract class MapNode<V, N extends HashMapNode<V, N>> extends Node<V, N>{
		protected Map<Integer, N> children = null; // Only created if needed.
		protected int maxIterate = 12; // Maybe add a setter.
		public MapNode(V value) {
			super(value);
		}
		@Override
		public N putChild(final int distance, final N child){
			if (children == null) children = newMap();
			children.put(distance, child);
			return child;
		}
		@Override
		public N getChild(final int distance){
			if (children == null) return null;
			return children.get(distance);
		}
		@Override
		public boolean hasChild(int distance) {
			if (children == null) return false;
			return children.containsKey(distance);
		}
		@Override
		public Collection<N> getChildren(final int distance, final int range, final Collection<N> nodes){
			if (children == null) return nodes;
                       // Consider iterating from 0 to range to retrieve the closest nodes first without using keySet.
			if (children.size() > maxIterate){
				for (int i = distance - range; i < distance + range + 1; i ++){
					final N child = children.get(i);
					if (child != null) nodes.add(child);
				}
			}
			else{
                               for (final Integer key : children.keySet()){
                                       // Unclear if this approach is faster than using the EntrySet.
                                       if (Math.abs(distance - key.intValue()) <= range) nodes.add(children.get(key));
                               }
			}
			return nodes;
		}
		/**
		 * Map factory method.
		 * @return
		 */
		protected abstract Map<Integer, N> newMap();
	}
	
	/**
	 * Node using a simple HashMap.
	 * @author mc_dev
	 *
	 * @param <V>
	 * @param <N>
	 */
	public static class HashMapNode<V, N extends HashMapNode<V, N>> extends MapNode<V, N>{
		/** Map Levenshtein distance to next nodes. */
		protected int initialCapacity = 4;
		protected float loadFactor = 0.75f;
		public HashMapNode(V value) {
			super(value);
		}

		@Override
		protected Map<Integer, N> newMap() {
			return new HashMap<Integer, N>(initialCapacity, loadFactor);
		}
	}
	
	public static class SimpleNode<V> extends HashMapNode<V, SimpleNode<V>>{
		public SimpleNode(V content) {
			super(content);
		}
	}
	
        public static interface NodeFactory<V, N extends Node<V, N>>{
                N newNode(V value, N parent);
        }
	
	/**
	 * Result of a lookup.
	 * @author mc_dev
	 *
	 * @param <V>
	 * @param <N>
	 */
	public static class LookupEntry<V, N extends Node<V, N>>{
               // 'nodes' contains the visited nodes that were within range on the search path.
               // The distance for each node is not exposed and this may change in the future.
               // Additional fields such as depth could also be useful.
		
		/** All visited nodes within range of distance. */
		public final Collection<N> nodes;
		/** Matching node */
		public final N match;
		/** Distance from value to match.value */
		public final int distance;
		/** If the node match is newly inserted.*/
		public final boolean isNew;
		
		public LookupEntry(Collection<N> nodes, N match, int distance, boolean isNew){
			this.nodes = nodes;
			this.match = match;
			this.distance = distance;
			this.isNew = isNew;
		}
	}
	
        public static interface LookupEntryFactory<V, N extends Node<V, N>, L extends LookupEntry<V, N>>{
                L newLookupEntry(Collection<N> nodes, N match, int distance, boolean isNew);
        }

	protected final NodeFactory<V, N> nodeFactory;
	
	protected final LookupEntryFactory<V, N, L> resultFactory;
	
	protected N root = null;
	
	/** Set to true to have visit called */
	protected boolean visit = false;
	
	public BKModTree(NodeFactory<V, N> nodeFactory, LookupEntryFactory<V, N, L> resultFactory){
		this.nodeFactory = nodeFactory;
		this.resultFactory = resultFactory;
	}
	
	public void clear(){
		root = null;
	}
	
	/**
	 * 
	 * @param value
	 * @param range Maximum difference from distance of node.value to children.
	 * @param seekMax If node.value is within distance but not matching, this is the maximum number of steps to search on.
	 * @param create
	 * @return
	 */
       public L lookup(final V value, final int range, final int seekMax, final boolean create){
                final List<N> inRange = new LinkedList<>();
                if (root == null){
                        if (create){
                                root = nodeFactory.newNode(value, null);
                                return resultFactory.newLookupEntry(inRange, root, 0, true);
                        }
                        return resultFactory.newLookupEntry(inRange, null, 0, false);
                }
               final List<N> open = new ArrayList<>();
                open.add(root);
                final InsertionInfo<N> insertion = new InsertionInfo<>();
                do {
                        final N current = open.remove(open.size() - 1);
                        final int distance = computeDistanceAndVisit(current, value);
                        if (distance == 0) {
                                return resultFactory.newLookupEntry(inRange, current, distance, false);
                        }
                        trySetInsertion(current, distance, create, insertion);
                        if (withinRange(current, distance, range, inRange, seekMax, create, insertion.node)) {
                                break;
                        }
                        current.getChildren(distance, range, open);

                       // Child visitation order may vary because HashMap does not guarantee iteration order.
                } while (!open.isEmpty());

               if (create && insertion.node != null){
                        final N newNode = nodeFactory.newNode(value, insertion.node);
                        insertion.node.putChild(insertion.distance, newNode);
                        return resultFactory.newLookupEntry(inRange, newNode, 0, true);
                }
                return resultFactory.newLookupEntry(inRange, null, 0, false);
        }

       protected static class InsertionInfo<N> {
               N node;
               int distance;
       }

       protected int computeDistanceAndVisit(N current, V value) {
               int dist = distance(current.getValue(), value);
               if (visit) {
                       visit(current, value, dist);
               }
               return dist;
       }

       protected void trySetInsertion(N current, int distance, boolean create, InsertionInfo<N> info) {
               if (create && info.node == null && !current.hasChild(distance)) {
                       info.node = current;
                       info.distance = distance;
               }
       }

       protected boolean withinRange(N current, int distance, int range, List<N> inRange, int seekMax, boolean create, N insertion) {
               if (Math.abs(distance) <= range) {
                       inRange.add(current);
                       if (seekMax > 0 && inRange.size() >= seekMax) {
                               if (!create || insertion != null) {
                                       return true;
                               }
                       }
               }
               return false;
       }
	
	/**
	 * Visit a node during lookup.
	 * @param node
	 * @param distance 
	 * @param value 
	 */
	protected void visit(N node, V value, int distance){
		// Override if needed.
	}
	
	//////////////////////////////////////////////
	// Abstract methods.
	//////////////////////////////////////////////
	
	/**
	 * Calculate the distance of two values.
	 * @param v1
	 * @param v2
	 * @return
	 */
	public abstract int distance(V v1, V v2);
	
}
