package ca.ipredict.predictor.TDAG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Node {

	/**
	 * Symbol of the Node
	 */
	public Integer symbol;
	
	/**
	 * Incoming weight
	 */
	public Integer inCount;
	
	/**
	 * Outgoing weight
	 */
	public Integer outCount;
	
	/**
	 * List of symbols from Root (included) to this node (included)
	 */
	public List<Integer> pathFromRoot;
	
	/**
	 * List of children of this node
	 */
	public HashMap<Integer, Node> children;
	
	/**
	 * Probability of getting this node given its parent
	 */
	public Double score;
	
	
	/**
	 * Construct a node with the given symbol
	 * @param symbol Symbol of the node
	 */
	public Node(Integer symbol, List<Integer> parentPath) {
		this.symbol = symbol;
		inCount = 0;
		outCount = 0;
		children = new HashMap<Integer, Node>();
		
		pathFromRoot = new ArrayList<Integer>(parentPath);
		pathFromRoot.add(symbol);
	}
	
	/**
	 * Create and Add a new child to this node.
	 * @param item Item to use to create the child node.
	 * @return Returns the new child.
	 */
	public Node addChild(Integer symbol) {
		
		//If necessery: create and insert the node in the children
		//Else extract the existing child from the children
		Node child = children.get(symbol);
		if(child == null) {
			child = new Node(symbol, pathFromRoot);
			children.put(symbol, child);
		}
		
		//increments this node's outCount
		outCount++;
		
		//increments the new child inCount to 1
		child.inCount++;
		
		return child;
	}
	
	@Override
	public String toString() {
		return symbol + "("+ inCount + "," + outCount +")";
	}
}