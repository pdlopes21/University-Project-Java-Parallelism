package knapsack;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Util {

    private Util() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Cria um CSV com os resultados das soluções
     * @param filename Nome do ficheiro CSV
     * @param algorithmName Nome do algoritmo (ex: "original", "parallel_stream", etc.)
     * @param totalMs Duração total do algoritmo em milissegundos
     * @param populateDuration Duração da criação da população inicial em milissegundos
     * @param measureDuration Duração do cálculo do fitness em milissegundos
     * @param bestOfDuration Duração da seleção do melhor indivíduo em milissegundos
     * @param tournamentDuration Duração dos torneios em milissegundos
     * Se o ficheiro não existir, cria o ficheiro e escreve o header.
     */
    public static void writeResultsCsv(String filename, String algorithmName, 
        long totalMs, 
        long populateDuration, 
        long measureDuration, 
        long bestOfDuration, 
        long tournamentDuration) {

		Path path = Paths.get(filename);
		boolean fileExists = Files.exists(path);

		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String timestamp = LocalDateTime.now().format(fmt);

		try (FileWriter fw = new FileWriter(filename, true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw)) {

			if (!fileExists) {
				out.println("timestamp,algorithm,total_ms,populate_ms,measureFitness_ms,bestOf_ms,tournament_ms");
			}

			// write the full row with per-phase durations too
			out.printf("%s,%s,%d,%d,%d,%d,%d\n", timestamp, algorithmName, totalMs,
					populateDuration, measureDuration, bestOfDuration, tournamentDuration);

		} catch (IOException e) {
			System.err.println("Failed to write benchmarks CSV: " + e.getMessage());
		}
	}

    public static void writeEmptyLine(String filename) {
        try (FileWriter fw = new FileWriter(filename, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw)) {
            out.println();
        } catch (IOException e) {
            System.err.println("Failed to write empty line to CSV: " + e.getMessage());
        }
    }   
}
