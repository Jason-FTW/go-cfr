package jasonftw.cfr;

import java.util.Arrays;
import java.util.Collections;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

public class Cambia {
	// Cambia definitions
	public static final int
	STOCKPILE_SWAP = 0,		// pick i card from the stockpile and swap it with one of the cards in hand						- code x
	STOCKPILE_DISCARD = 1,	// pick i card from the stockpile and discard it (using its action or lack thereof)				- code y
	CAMBIA = 2;				// call cambia; endgame; end your turn and allow all players to go once before counting cards 	- code c

	public static final int NUM_ACTIONS = 3;
	public TreeMap<String, Node> nodeMap = new TreeMap<String, Node>();
	
	class Node {
		String infoSet;
		double[] regretSum = new double[NUM_ACTIONS], 
				 strategy = new double[NUM_ACTIONS], 
				 strategySum = new double[NUM_ACTIONS];

		private double[] getStrategy(double realizationWeight) {
			double normalizingSum = 0;
			for (int i = 0; i < NUM_ACTIONS; i++) {
				strategy[i] = regretSum[i] > 0 ? regretSum[i] : 0;
				normalizingSum += strategy[i];
			}
			for (int i = 0; i < NUM_ACTIONS; i++) {
				if (normalizingSum > 0)
					strategy[i] /= normalizingSum;
				else
					strategy[i] = 1.0 / NUM_ACTIONS;
				strategySum[i] += realizationWeight * strategy[i];
			}
			return strategy;
		}


		public double[] getAverageStrategy() {
			double[] avgStrategy = new double[NUM_ACTIONS];
			double normalizingSum = 0;
			for (int i = 0; i < NUM_ACTIONS; i++)
				normalizingSum += strategySum[i];
			for (int i = 0; i < NUM_ACTIONS; i++) 
				if (normalizingSum > 0)
					avgStrategy[i] = strategySum[i] / normalizingSum;
				else
					avgStrategy[i] = 1.0 / NUM_ACTIONS;
			return avgStrategy;
		}

		@Override
		public String toString() {
			return String.format("%4s: %s", infoSet, Arrays.toString(getAverageStrategy()));
		}
	}

	/**
	 * Cambia CFR training runner
	 * @param iterations
	 */
	public void train(int iterations) {
		// build our standard 54-card deck including jokers
		// 1 = ace, 13 = king, 0 = joker
		int[] arr = {
				1, 1, 1, 1,		// Ace
				2, 2, 2, 2,		// 2
				3, 3, 3, 3,		// 3
				4, 4, 4, 4,		// 4
				5, 5, 5, 5,		// 5
				6, 6, 6, 6,		// 6
				7, 7, 7, 7,		// 7
				8, 8, 8, 8,		// 8
				9, 9, 9, 9,		// 9
				10, 10, 10, 10,	// 10
				11, 11, 11, 11,	// Jack
				12, 12, 12, 12,	// Queen
				13, 13, 13, 13,	// King
				0, 0			// joker
		};
		
		Stack<Integer> cards = new Stack<Integer>();

		double util = 0;

		// train x iterations
		for (int i = 0; i < iterations; i++) {
			for (int c1 = arr.length - 1; c1 > 0; c1--) { 
				// fisher-yates shuffle 
				int c2 = ThreadLocalRandom.current().nextInt(c1 + 1);
				int tmp = arr[c1];
				arr[c1] = arr[c2];
				arr[c2] = tmp;
			}
			for (int card : arr) {
				cards.push(card);
			}
			// run counterfactual regret minimization algorithm
			
			int[] p0Cards = new int[4];
			int[] p1Cards = new int[4];
			
			// deal to two bots
			for (int c = 0; c < 4; c++) {
				p0Cards[c] = cards.pop();
			}
			for (int c = 0; c < 4; c++) {
				p1Cards[c] = cards.pop();
			}
			
			
			Player p0 = new Player(p0Cards, 1.0);
			Player p1 = new Player(p1Cards, 1.0);
			util += cfr(cards, new Stack<Integer>(), "", p0, p1);
		}
		System.out.println("Average game value: " + util / iterations);
		for (Node n : nodeMap.values())
			System.out.println(n);
	}


	private double cfr(Stack<Integer> cards, Stack<Integer> discard, String history, Player p0, Player p1) {
		int plays = history.length();
		int player = plays % 2;		// whose turn is it
		int opponent = 1 - player;	// opponent move turn
		
		// if the game is in play (i.e. not move 1)
		if (plays > 1) {
			// cambiaCalled terminates the game if previous player has called Cambia
			boolean cambiaCalled = history.charAt(plays - 1) == 'c';

			if (cambiaCalled || cards.size() == 0) {
				// game over; count card values
				boolean isPlayerCardHigher = p0.computeScore() > p1.computeScore();
				
				// reshuffle
				Collections.shuffle(discard);
				for (int i : discard) {
					cards.push(i);
				}
				
				// if your score is higher than the opponent, you win!
				// otherwise you lose.
				return isPlayerCardHigher ? 2 : -2;
			}
		}

		// retrive node associated with the current information set or create if null
		// take larger card
		String infoSet;
		System.out.println("History: " + history);
		System.out.println("Player zero cards: " + Arrays.toString(p0.cards));
		System.out.println("Player one cards: " + Arrays.toString(p1.cards));

		if (player == 0) {
			infoSet = Math.max(p0.cards[0], p0.cards[1]) + history;
		} else {
			infoSet = Math.max(p1.cards[0], p1.cards[1]) + history;
		}

		Node node = nodeMap.get(infoSet);
		if (node == null) {
			node = new Node();
			node.infoSet = infoSet;
			nodeMap.put(infoSet, node);
		}

		double[] strategy = node.getStrategy(player == 0 ? p0.score : p1.score);
		double[] util = new double[NUM_ACTIONS];
		double nodeUtil = 0;
		
		Stack<Integer> preserve = (Stack<Integer>) cards.clone();
		
		// calculate moves
		for (int i = 0; i < NUM_ACTIONS; i++) {
			cards = (Stack<Integer>) preserve.clone();
			if (cards.size() == 0) {
				System.out.println("empty stack");
			}
			// determine move
			String nextHistory;
			if (i == 0) {
				nextHistory = history + "x";
			} else if (i == 1) {
				nextHistory = history + "y";
			} else {
				nextHistory = history + "c";
			}
			// nextHistory = history + (i == 0 ? "x" : "y");
			if (player == 0) {
				System.out.println("Player move");
				// if player's move
				if (i == 0) {
					int draw = cards.pop();				// pop the next card from stockpile
					int discardIndex = (p0.cards[0] > p0.cards[1] ? 0 : 1);		// find the index of the largest card
					int discarded = Math.max(p0.cards[0], p0.cards[1]);			// find the largest value card that we discard
					discard.push(discarded);				// place card in discard pile
					p1.cards[discardIndex] = draw;		// add the new card to hand
				} else if (i == 1) {
					int discarded = cards.pop();
					discard.push(discarded);
				}
				p0.score *= strategy[i];
				util[i] = -cfr(cards, discard, nextHistory, p0, p1);
			} else {
				System.out.println("Opponent move");
				if (i == 0) {
					int draw = cards.pop();				// pop the next card from stockpile
					int discardIndex = (p1.cards[0] > p1.cards[1] ? 0 : 1);		// find the index of the largest card
					int discarded = Math.max(p1.cards[0], p1.cards[1]);			// find the largest value card that we discard
					discard.push(discarded);				// place card in discard pile
					p1.cards[discardIndex] = draw;		// add the new card to hand
				} else if (i == 1) {
					int discarded = cards.pop();
					discard.push(discarded);
				}
				p1.score *= strategy[i];
				util[i] = -cfr(cards, discard, nextHistory, p0, p1);
			}
			nodeUtil += strategy[i] * util[i];
		}

		for (int i = 0; i < NUM_ACTIONS; i++) {
			double regret = util[i] - nodeUtil;
			node.regretSum[i] += (player == 0 ? p1.score : p0.score) * regret;
		}

		return nodeUtil;
	}
	

	public static void main(String[] args) {
		int iterations = 1;
		new Cambia().train(iterations);
	}

}