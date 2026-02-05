package knapsack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class AlgoritmoExecutor {
    private static final int N_GENERATIONS = 500;
    private static final int POP_SIZE = 100000;
    private static final double PROB_MUTATION = 0.5;
    private static final int TOURNAMENT_SIZE = 3;
    // Manter sempre um núcleo livre
    private static final int N_THREADS = Runtime.getRuntime().availableProcessors() - 1;

    //Comentar estas variaveis e calculos de tempo quando não se for medir benchmark
    private long populateDuration = 0;
    private long measureDuration = 0;
    private long bestOfDuration = 0;
    private long tournamentDuration = 0;
    private long totalDuration = 0;

    private ExecutorService executor;
    private Individual[] population = new Individual[POP_SIZE];
    private int extraThreads = 0;

    /*
     * Construtor que permite adicionar threads extra para além do número de núcleos disponíveis - 1
     * Isto é útil para simular oversubscription e observar o impacto no desempenho
     * @param extraThreads número de threads adicionais a serem usadas. Pode ser negativo
     */
    public AlgoritmoExecutor(int extraThreads) {
        if (N_THREADS + extraThreads < 2) {
            throw new IllegalArgumentException("Não é admitido ter menos de 2 threads no total");
        }
        this.extraThreads = extraThreads;
        executor = Executors.newFixedThreadPool(N_THREADS + extraThreads);

        long populateStartTime = System.currentTimeMillis();

        populateInitialPopulationRandomly();

        long populateEndTime = System.currentTimeMillis();
        populateDuration += (populateEndTime - populateStartTime);
    }

    private void populateInitialPopulationRandomly() {
        try {
            int chunkSize = POP_SIZE / N_THREADS;
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < N_THREADS; t++) {
                final int start = t * chunkSize;
                final int end = (t == N_THREADS - 1) ? POP_SIZE : (t + 1) * chunkSize;

                futures.add(executor.submit(() -> {
                    Random localRandom = ThreadLocalRandom.current();
                    for (int i = start; i < end; i++) {
                        population[i] = Individual.createRandom(localRandom);
                    }
                }));
            }

            //Esperar que todas as tarefas terminem
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error during population initialization", e);
        }
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        try {
            for (int generation = 0; generation < N_GENERATIONS; generation++) {

                // Step1 - Calculate Fitness
                long measureStartTime = System.currentTimeMillis();

                calculateFitness();

                long measureEndTime = System.currentTimeMillis();
                measureDuration += (measureEndTime - measureStartTime);

                // Step2 - Print the best individual so far
                long bestOfStartTime = System.currentTimeMillis();
                
                Individual best = bestOfPopulation();

                long bestOfEndTime = System.currentTimeMillis();
			    bestOfDuration += (bestOfEndTime - bestOfStartTime);

                System.out.println("Best at generation " + generation + " is " + best + " with "
                        + best.fitness);


                // Step3 - Find parents to mate (cross-over)
                long tournamentStartTime = System.currentTimeMillis();

                Individual[] newPopulation = createNewPopulationParallel(best);

                long tournamentEndTime = System.currentTimeMillis();
                tournamentDuration += (tournamentEndTime - tournamentStartTime);
                
                population = newPopulation;
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error during fitness evaluation", e);
        } finally {

            long endTime = System.currentTimeMillis(); // Fim da contagem de tempo
            totalDuration = endTime - startTime; // Duração em milissegundos

            
            Util.writeResultsCsv("Results.csv", "ExecutorService", 
            totalDuration,
            populateDuration,
            measureDuration,
            bestOfDuration,
            tournamentDuration);
            

            /* 
            Util.writeResultsCsv("Executor_Results.csv", "ExecutorService " + (N_THREADS + extraThreads) + " Threads", 
            totalDuration,
            populateDuration,
            measureDuration,
            bestOfDuration,
            tournamentDuration);
            */
            shutdown();
        }
    }

    private void calculateFitness() throws InterruptedException, ExecutionException {
        int chunkSize = POP_SIZE / N_THREADS;
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < N_THREADS; t++) {
            final int start = t * chunkSize;
            final int end = (t == N_THREADS - 1) ? POP_SIZE : (t + 1) * chunkSize;

            futures.add(executor.submit(() -> {
                for (int i = start; i < end; i++) {
                    population[i].measureFitness();
                }
            }));
        }

        // Esperar que todas as tarefas terminem
        for (Future<?> future : futures) {
            future.get();
        }
    }

    private Individual[] createNewPopulationParallel(Individual best) throws InterruptedException, ExecutionException {
        Individual[] newPopulation = new Individual[POP_SIZE];
        newPopulation[0] = best; // The best individual remains

        int chunkSize = (POP_SIZE - 1) / N_THREADS;
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < N_THREADS; t++) {
            final int start = 1 + t * chunkSize;
            final int end = (t == N_THREADS - 1) ? POP_SIZE : 1 + (t + 1) * chunkSize;

            futures.add(executor.submit(() -> {
                Random localRandom = ThreadLocalRandom.current();
                
                for (int i = start; i < end; i++) {
                    // We select two parents, using a tournament.
                    Individual parent1 = tournament(TOURNAMENT_SIZE, localRandom);
                    Individual parent2 = tournament(TOURNAMENT_SIZE, localRandom);

                    Individual child = parent1.crossoverWith(parent2, localRandom);

                    // Step4 - Mutate
                    if (localRandom.nextDouble() < PROB_MUTATION) {
                        child.mutate(localRandom);
                    }

                    newPopulation[i] = child;
                }
            }));
        }

        // Esperar que todas as tarefas terminem
        for (Future<?> future : futures) {
            future.get();
        }

        return newPopulation;
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
        //Sem necessidade de paralelismo, benchmarks feitos (não presentes na entrega final) mostram que não apresenta
        //diferenças significativas de desempenho
        //Apesar de ter mantido a versão paralela na classe AlgoritmoStream, optei por uma versão sequencial aqui
        //para simplificar a leitura
        Individual best = population[0];
        for (Individual other : population) {
            if (other.fitness > best.fitness) {
                best = other;
            }
        }
        return best;
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
