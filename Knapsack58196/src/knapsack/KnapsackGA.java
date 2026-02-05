package knapsack;

import java.util.Random;

public class KnapsackGA {
	private static final int N_GENERATIONS = 500;
	private static final int POP_SIZE = 100000;
	private static final double PROB_MUTATION = 0.5;
	private static final int TOURNAMENT_SIZE = 3;

	private Random r = new Random();

	private Individual[] population = new Individual[POP_SIZE];

	public KnapsackGA() {
		populateInitialPopulationRandomly();
	}

	private void populateInitialPopulationRandomly() {
		/* Creates a new population, made of random individuals */
		for (int i = 0; i < POP_SIZE; i++) {
			population[i] = Individual.createRandom(r);
		}
	}

	public void run() {
		for (int generation = 0; generation < N_GENERATIONS; generation++) {

			// Step1 - Calculate Fitness
			for (int i = 0; i < POP_SIZE; i++) {
				population[i].measureFitness();
			}

			// Step2 - Print the best individual so far.
			Individual best = bestOfPopulation();
			System.out.println("Best at generation " + generation + " is " + best + " with "
					+ best.fitness);

			// Step3 - Find parents to mate (cross-over)
			Individual[] newPopulation = new Individual[POP_SIZE];
			newPopulation[0] = best; // The best individual remains

			for (int i = 1; i < POP_SIZE; i++) {
				// We select two parents, using a tournament.
				Individual parent1 = tournament(TOURNAMENT_SIZE, r);
				Individual parent2 = tournament(TOURNAMENT_SIZE, r);

				newPopulation[i] = parent1.crossoverWith(parent2, r);
			}

			// Step4 - Mutate
			for (int i = 1; i < POP_SIZE; i++) {
				if (r.nextDouble() < PROB_MUTATION) {
					newPopulation[i].mutate(r);
				}
			}
			population = newPopulation;
		}
	}

	private Individual tournament(int tournamentSize, Random r) {
		/*
		 * In each tournament, we select tournamentSize individuals at random, and we
		 * keep the best of those.
		 */
		Individual best = population[r.nextInt(POP_SIZE)];
		for (int i = 0; i < tournamentSize; i++) {
			Individual other = population[r.nextInt(POP_SIZE)];
			if (other.fitness > best.fitness) {
				best = other;
			}
		}
		return best;
	}

	private Individual bestOfPopulation() {
		/*
		 * Returns the best individual of the population.
		 */
		Individual best = population[0];
		for (Individual other : population) {
			if (other.fitness > best.fitness) {
				best = other;
			}
		}
		return best;
	}

}
