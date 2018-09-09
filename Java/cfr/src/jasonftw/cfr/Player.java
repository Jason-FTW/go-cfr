package jasonftw.cfr;

public class Player {
	
	double score;
	int points;
	int[] cards;
	// player knows cards 0 and 1 but nothing else.
	
	public Player(int[] cards, double score) {
		this.cards = cards;
		this.score = score;
	}
	
	public double computeScore() {
		double score = 0;
		for (int i : cards) {
			score += i;
		}
		
		return score;
	}
}
