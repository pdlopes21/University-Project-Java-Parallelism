package knapsack;

public class Main {
	public static void main(String[] args) {


		//Executa qualquer numero de algoritmos desejados, um de cada vez, e escreve 
		//os resultados num ficheiro CSV.
		//No final do ficheiro, é colocada uma linha em branco para separar os resultados de cada execução do programa.

		//Algoritmo  que foi usado para testar os bottlenecks do algoritmo original.
		//Versão final serve para fazer benchmark do algoritmo original
		BootleNeckTester tester = new BootleNeckTester();
		tester.run();
			
		//Algoritmo paralelo usando Streams
		AlgoritmoStream as = new AlgoritmoStream();
		as.run();


		//Algoritmo paralelo usando ExecutorService
		AlgoritmoExecutor ae = new AlgoritmoExecutor(0);
		ae.run();

		Util.writeEmptyLine("Results.csv");
	

	}
}
