package knapsack;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class AlgoritmoStream {

    private static final int N_GENERATIONS = 500;
	private static final int POP_SIZE = 100000;
	private static final double PROB_MUTATION = 0.5;
	private static final int TOURNAMENT_SIZE = 3;

    //Comentar estas variaveis e calculos de tempo quando não se for medir benchmark
    private long populateDuration = 0;
    private long measureDuration = 0;
    private long bestOfDuration = 0;
    private long tournamentDuration = 0;
    private long totalDuration = 0;

	private Individual[] population = new Individual[POP_SIZE];

	public AlgoritmoStream() {
        long populateStartTime = System.currentTimeMillis();

		populateInitialPopulationRandomly();

        long populateEndTime = System.currentTimeMillis();
        populateDuration += (populateEndTime - populateStartTime);
	}

    private void populateInitialPopulationRandomly() {
        population = IntStream.range(0, POP_SIZE).parallel()
            .mapToObj(i -> Individual.createRandom(ThreadLocalRandom.current()))
            .toArray(Individual[]::new);
    }

    
	public void run() {
        long startTotalTime = System.currentTimeMillis();

		for (int generation = 0; generation < N_GENERATIONS; generation++) {

			// Step1 - Calculate Fitness
            long measureStartTime = System.currentTimeMillis();

			 IntStream.range(0, POP_SIZE)
                .parallel()
                .forEach(i -> population[i].measureFitness());

            long measureEndTime = System.currentTimeMillis();
            measureDuration += (measureEndTime - measureStartTime);



			// Step2 - Print the best individual so far.
            long bestOfStartTime = System.currentTimeMillis();

			Individual best = bestOfPopulation();

			long bestOfEndTime = System.currentTimeMillis();
			bestOfDuration += (bestOfEndTime - bestOfStartTime);

			System.out.println("Best at generation " + generation + " is " + best + " with "
					+ best.fitness);


			// Step3 - Find parents to mate (cross-over)
			Individual[] newPopulation = new Individual[POP_SIZE];
			newPopulation[0] = best; // The best individual remains


            //Implementação antiga tinha a medição de tempo do torneio e mutação dentro do mapToObj
            //O que causava problemas de concorrência na soma dos tempos, já que somavam o tempo de operações realizadas
            //em threads simultâneas
            //Sendo assim, medi o tempo antes e depois do stream paralelo, o que pode não ser tão preciso, mas evita esse problema
            //e permite manter as medições para comparação no benchmark 
            long tournamentStartTime = System.currentTimeMillis();

			newPopulation = IntStream.range(0, POP_SIZE)
                .parallel()
                .mapToObj(i -> {
                    if (i == 0) {
                        return best; // The best individual remains
                    }
                    
                    Random localRandom = ThreadLocalRandom.current();
                    
                    // We select two parents, using a tournament.
                    Individual parent1 = tournament(TOURNAMENT_SIZE, localRandom);
                    Individual parent2 = tournament(TOURNAMENT_SIZE, localRandom);

                    Individual child = parent1.crossoverWith(parent2, localRandom);
                    
                    // Step4 - Mutate

                    if (localRandom.nextDouble() < PROB_MUTATION) {
                        child.mutate(localRandom);
                    }
                    
                    return child;
                })
                .toArray(Individual[]::new);

            long tournamentEndTime = System.currentTimeMillis();
            tournamentDuration += (tournamentEndTime - tournamentStartTime);


            population = newPopulation;
        }

        long endTotalTime = System.currentTimeMillis();
        totalDuration = endTotalTime - startTotalTime;

        //Comentar as linhas seguintes quando não se for medir benchmark
        Util.writeResultsCsv("Results.csv", "JavaParallelStream", 
        totalDuration,
        populateDuration,
        measureDuration,
        bestOfDuration,
        tournamentDuration);
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
        //Apesar de estar aqui escrita como uma finção paralela, as benchmarks indicam que esta implementação
        //não apresenta diferenças significativas de performance em relação à implementação sequencial
        //Mantive a versão paralela para manter a coerência com o resto do código
        return Arrays.stream(population)
            .parallel()
            .reduce((a, b) -> a.fitness > b.fitness ? a : b)
            .orElse(population[0]);
    }
    
}
