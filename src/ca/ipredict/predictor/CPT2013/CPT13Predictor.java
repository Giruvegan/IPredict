package ca.ipredict.predictor.CPT2013;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ca.ipredict.database.Item;
import ca.ipredict.database.Sequence;
import ca.ipredict.helpers.MemoryLogger;
import ca.ipredict.predictor.Paramable;
import ca.ipredict.predictor.Predictor;

/**
 * CPT - Compact Prediction Tree 
 * 1st iteration from  ADMA 2013, without speed enhancement
 */
public class CPT13Predictor extends Predictor {

	private PredictionTree Root; //Compact tree
	private Map<Integer, PredictionTree> LT; //Lookup Table
	private Map<Integer, BitSet> II;
	
	private String TAG = "CPT13";
	
	private long nodeNumber; //number of node in the Compact tree
	
	public Paramable parameters;
	
	public CPT13Predictor() {
		nodeNumber = 0;
		Root = new PredictionTree();
		LT = new HashMap<Integer, PredictionTree>();
		II = new HashMap<Integer, BitSet>();
		parameters = new Paramable();
	}

	public CPT13Predictor(String tag) {
		this();
		TAG = tag;
	}
	
	public CPT13Predictor(String tag, String params) {
		this(tag);
		parameters.setParameter(params);
	}
	
	/**
	 * Finds all branches that contains this sequences
	 * @param target sequence to find in the tree.
	 * @return List of sequence id ( can be transformed into leafs ) 
	 */
	private List<Integer> getMatchingSequences(Sequence target) {
		
		//find all sequences that have all the target's items
		//for each item in the target sequence
		List<Item> items = target.getItems();
		BitSet intersection = null; //get the bitset from the first item from target
		for(int i = 0 ; i < items.size(); i++) {
			BitSet bitset = II.get(items.get(i).val);
			if(bitset != null){
				if(intersection == null){
					intersection = (BitSet) bitset.clone();
				}else{
					intersection.and(bitset);
				}
			}
		}
		
		//if intersection is empty (no sequences contained all target's item
		if(intersection == null || intersection.cardinality() == 0)
			return new ArrayList<Integer>(); //no match
		
		List<Integer> lastIndexes = new ArrayList<Integer>(intersection.cardinality());
		
		//For each bit set to 1
		for (int i = intersection.nextSetBit(0); i >= 0; i = intersection.nextSetBit(i+1)) {
			lastIndexes.add(i);
		 }
		
		return lastIndexes;
	}


	
	/**
	 * Updates a CountTable based on a given sequence using the compactTree
	 * @param target Sequence to use
	 * @param weight Weight to add for each occurrence of an item in the CountTable
	 * @param CountTable The CountTable to update/fill
	 * @param hashSidVisited Prevent for processing the same branch multiple times
	 */
	private void UpdateCountTable(Sequence target, float weight, Map<Integer, Float> CountTable, HashSet<Integer> hashSidVisited) {

		List<Integer> indexes = getMatchingSequences(target); 
		
		//creating an HashMap of the target's item (for O(1) search time)
		HashSet<Integer> hashTarget = new HashSet<Integer>();
		for(Item it : target.getItems()) {
			hashTarget.add(it.val);
		}
		
		
		//For each branch 
		for(Integer index : indexes) {

			if(parameters.paramBool("useHashSidVisited") && hashSidVisited.contains(index)){
				continue;    
			}   
			
			//Getting the branch's leaf
			PredictionTree curNode = LT.get(index);
			
			//New way, allows duplicate
			if(parameters.paramBool("branchTraversalTopToBottom")) {
				//Transform this branch in a list
				List<Item> branch = new ArrayList<Item>();
				while(curNode.Parent != Root) {
					
					branch.add(curNode.Item);
					
					//Going up the tree
					curNode = curNode.Parent;
				}
				Collections.reverse(branch);
				
				HashSet<Integer> hashTargetTMP = new HashSet<Integer>(hashTarget);
				int i = 0;
				for(i = 0 ; i < branch.size() && hashTargetTMP.size() > 0; i++ ) {
					
					if(hashTargetTMP.contains(branch.get(i).val)== true) {
						hashTargetTMP.remove(branch.get(i).val);
					}	
				}
				
				for(;i < branch.size(); i++) {
					float oldValue = 0;
					if(CountTable.containsKey(branch.get(i).val)) {
						oldValue = CountTable.get(branch.get(i).val);
					}
	
					//Update the countable with the right weight and value
					float curValue = (parameters.paramInt("countTableWeightDivided") == 0) ? 1f : 1f /((float)indexes.size());
					CountTable.put(branch.get(i).val, oldValue + weight /((float)indexes.size()) );
					
					hashSidVisited.add(index); 
				}
			}
			else {
				//Going up the branch until we find a item from the target
				while( (hashTarget.contains(curNode.Item.val) == false)) {
					
					//Getting the current count for this item if it exists or 0
					float oldValue = 0;
					if(CountTable.containsKey(curNode.Item.val)) {
						oldValue = CountTable.get(curNode.Item.val);
					}
	
					//Update the countable with the right weight and value
					CountTable.put(curNode.Item.val, oldValue + weight 
							/((float)indexes.size()) );  
					
					
					//Going up the tree
					curNode = curNode.Parent;
				}
			}
			

		}
	}
	
	/**
	 * Generate the highest rated sequence from a CountTable using the Lift or the Confidence
	 * @param CountTable The CountTable to use, it needs to be filled
	 * @param useLift Whether to use the Lift or the Confidence to calculate the score
	 * @return The highest rated sequence or an empty one if the CountTable is empty
	 */
	private Sequence getBestSequenceFromCountTable(Map<Integer, Float> CountTable, boolean useLift) {
		
		//Looking for the item with the highest count in the CountTable
		double maxValue = -1;
		double secondMaxValue = -1;
		Integer maxItem = -1;
		for(Map.Entry<Integer, Float> it : CountTable.entrySet()) {
			
			double lift = it.getValue() / II.get(it.getKey()).cardinality();
			double support = II.get(it.getKey()).cardinality();
			double confidence = it.getValue();
			
			double score = (parameters.paramInt("firstVote") == 1) ? confidence : lift; //Use confidence or lift, depending on Parameter.firstVote

			if(score > maxValue) {
				secondMaxValue = maxValue; //saving the old value as the second best
				maxItem = it.getKey(); //saving the new best value
				maxValue = score;
			} 
			else if (score > secondMaxValue) {
				secondMaxValue = score; //updating the second best value
			}
		}

		Sequence predicted = new Sequence(-1);
		

		//Calculating the ratio between the best value and the second best value
		double diff = 1 - (secondMaxValue / maxValue);
		
		//No match
		if(maxItem == -1) {
			//Nothing to do
		} 
		//-If the secondVote is set to 0 , and their is a best and a second best, then
		else if (parameters.paramInt("secondVote") == 0 && maxItem != -1 && secondMaxValue != -1 && diff <= parameters.paramDouble("voteTreshold")) {
			
		}
		//-If there is no second best value, then the best one is the winner
		//-If there is a max item (at least one item in the CountTable)
		// and it is better than second best according to the voteTreshold
		else if (secondMaxValue == -1 || diff >= parameters.paramDouble("voteTreshold")) {
			Item predictedItem = new Item(maxItem);
			predicted.addItem(predictedItem);
		}
		//if both the best and the secondBest are "equal"
		else {
			//pick the one with the highest support or lift
			double highestScore = 0;
			int newBestItem = -1;
			for(Map.Entry<Integer, Float> it : CountTable.entrySet()) {
				
				if(maxValue == it.getValue()) {
					if(II.containsKey(it.getKey())) {
						
						double lift = it.getValue() / II.get(it.getKey()).cardinality();
						double support = II.get(it.getKey()).cardinality();
						
						double score = (parameters.paramInt("secondVote") == 1) ? support : lift; //Use confidence or lift, depending on Parameter.secondVote
						
						if(score > highestScore) {
							highestScore = score;
							newBestItem = it.getKey();
						}
					}
				}
			}			
			Item predictedItem = new Item(newBestItem);
			predicted.addItem(predictedItem);
		}
		
			
		return predicted;
	}
	
	/**
	 * Predict the next element in the given sequence
	 * @param sequence to predict
	 */
	public Sequence Predict(Sequence target) {

		//remove items that were never seen before from the Target sequence before LLCT try to make a prediction
		//If set to false, those items will be still ignored later on (in updateCountTable())
		if(parameters.paramBool("removeUnknownItemsForPrediction")){
			Iterator<Item> iter = target.getItems().iterator();
			while (iter.hasNext()) {
				Item item = (Item) iter.next();
				// if there is no bitset for that item (we have never seen it)
				if(II.get(item.val) == null){
					// then remove it from target.
					iter.remove();  
				}
			}
		}
		
		
		Sequence prediction = new Sequence(-1);
		int minRecursion = parameters.paramInt("recursiveDividerMin");
		int maxRecursion = (parameters.paramInt("recursiveDividerMax") > target.size()) ? target.size() : parameters.paramInt("recursiveDividerMax");
		
		for(int i = minRecursion ; i < target.size() && prediction.size() == 0 && i < maxRecursion; i++) {
		
			HashSet<Integer> hashSidVisited = new HashSet<Integer>();  // PFV
			
			//TODO: use those as global parameters
			//TODO: int minSize = (target.size() > 3) ? target.size() - 3 : 1;
			//int minSize = (target.size() > 3) ? target.size() - 3 : 1;
			int minSize = target.size() - i;
			boolean useLift = false;
			
			//Dividing the target sequence into sub sequences
			List<Sequence> subSequences = new ArrayList<Sequence>();
			LLCTHelper.RecursiveDivider(subSequences, target, minSize);
			
			//For each subsequence, updating the CountTable
			Map<Integer, Float> CountTable = new HashMap<Integer, Float>();
			for(Sequence sequence : subSequences) {
				
				//Setting up the weight multiplier for the countTable
				float weight = 1f;		
				if(parameters.paramInt("countTableWeightMultiplier") == 1)
					weight = 1f  / target.size();
				else if(parameters.paramInt("countTableWeightMultiplier") == 2)
					weight = (float)sequence.size() / target.size();
				
				UpdateCountTable(sequence, weight, CountTable, hashSidVisited);
			}
		
			//Getting the best sequence out of the CountTable
			prediction = getBestSequenceFromCountTable(CountTable, useLift);
		}

		return prediction;
	}
	
	@Override
	public String getTAG() {
		return TAG;
	}
	
	
	/**
	 * Trains this predictor with training data, use "setTrainingSequences()" first
	 * @return true on success
	 */
	@Override
	public Boolean Train(List<Sequence> trainingSequences) {
		
		nodeNumber = 0;
		int seqId = 0; //current sequence from database
		Root = new PredictionTree();
		LT = new HashMap<Integer, PredictionTree>();
		II = new HashMap<Integer, BitSet>();
		
		//Logging memory usage
		MemoryLogger.addUpdate();
			
		//Slicing sequences, so no sequence has a length > maxTreeHeight
		List<Sequence> newTrainingSet = new ArrayList<Sequence>();
		for(Sequence seq : trainingSequences) {
			
			if(seq.size() > parameters.paramInt("splitLength") && parameters.paramInt("splitMethod") > 0) {
				if(parameters.paramInt("splitMethod") == 1)
					newTrainingSet.addAll(LLCTHelper.sliceBasic(seq, parameters.paramInt("splitLength")));
				else
					newTrainingSet.addAll(LLCTHelper.slice(seq, parameters.paramInt("splitLength")));
			}else{
				newTrainingSet.add(seq);
			}		
		}
		
		
		//For each line (sequence) in file
		for(Sequence curSeq : newTrainingSet) {
			
			PredictionTree curNode = Root;
			
			//for each item in this sequence
			for(Item it : curSeq.getItems()) {
				
				//if item is not in Inverted Index then we add it
				if(II.containsKey(it.val) == false) {
					BitSet tmpBitset = new BitSet();
					II.put(it.val, tmpBitset);
				}
				//updating Inverted Index with seqId for this Item
				
				II.get(it.val).set(seqId);
				
				//if item is not in compact tree then we add it
				if(curNode.hasChild(it) == false) {
					curNode.addChild(it);
					nodeNumber++;
				}
				curNode = curNode.getChild(it);
			}
			
			LT.put(seqId, curNode); //adding <sequence id, last node in sequence>
			seqId++; //increment sequence id number
		}

		
		
		/**
		 * OPTIMIZATION:
		 * Removes all the unique items with a really low support from the inverted index.
		 * Should be tested some more, appears to boost the coverage with no significant effect on the precision
		 */
		/*
		int minSup = 0; //should be relative instead of absolute // for bms try: 50
		Iterator it = II.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry<Integer, BitSet> pairs = (Map.Entry)it.next();
	        
	        if(pairs.getValue().cardinality() < minSup) {
	        	it.remove();
	        }
	    }
	    */
	    /*****************END OF OPTIMIZATION***********************/
		
		
		//Logging memory usage
		MemoryLogger.addUpdate();
		
		return true;
	}
	
	/**
	 * Return the number of node in the compact tree
	 */
	public long size() {
		return nodeNumber;
	}
	
}