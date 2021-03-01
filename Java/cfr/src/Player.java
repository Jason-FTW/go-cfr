package jasonftw.cfr;

public class Player {
	
	double strategy;
	int points;
	int[] cards;
	// player knows cards 0 and 1 but nothing else.
	
	public Player(int[] cards, double strategy) {
		this.cards = cards;
		this.strategy = strategy;
	}
	
	public double computeScore() {
		double score = 0;
		for (int i : cards) {
			score += i;
		}
		
		return score;
	}
}
