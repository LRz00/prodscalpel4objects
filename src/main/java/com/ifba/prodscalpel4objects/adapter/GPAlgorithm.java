package com.ifba.prodscalpel4objects.adapter;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
* Core GP Algorithm, responsável por reduzir o código do over-organ
 * e adaptá-lo ao ambiente do host.
 * <p>
 * Características do GP Algorithm:
 * 1. Busca Heurística: A GP explora o espaço de possíveis modificações no código do órgão para encontrar uma versão compatível com o hospedeiro.
 * 2. Otimização: Reduz o tamanho do órgão removendo código irrelevante (ex: dead code) e ajustando chamadas de função.
 * 3. Resolução de Conflitos: Adapta nomes de variáveis/funções e estruturas de dados para evitar colisões no hospedeiro.
 * 4. Eficiência: O processo é repetido por múltiplas gerações até que um indivíduo passe em todos os testes.
 * <p>
* @author Giovane Neves
*/
public class GPAlgorithm {

   /**
    * Define o tamanho máximo da população para 100;
    * Foi selecionado o valor 100 para evitar o problema de 'máximo local',
    * onde a solução é melhor que as suas vizinhas, mas não é a melhor globalmente.
    */
   private final static int POPULATION_SIZE = 100;

   /**
    * Limita o número máximo de gerações para 50
    */
   private final static int MAX_GENERATIONS = 50;


   // Lista de LOCs do IceBox
   private List<Integer> iceBoxLOCs;

   /**
    * Crossover between two individuals.
    *
    * @param parent1 The first Inidividual.
    * @param parent2 The second Individual.
    * @return A new individual with the genes of the two individuals passed by parameter.
    */
   public GPIndividual crossover(GPIndividual parent1, GPIndividual parent2) {

      if (parent1.getSelectedLOCs().isEmpty() || parent2.getSelectedLOCs().isEmpty()) {
         return new GPIndividual(); // Retorna indivíduo vazio
      }

      GPIndividual child = new GPIndividual();
      List<Integer> childLOCs = new ArrayList<>();

      Random random = new Random();

      // Escolhe um ponto de corte aleatório para o crossover
      int minSize = Math.min(parent1.getSelectedLOCs().size(), parent2.getSelectedLOCs().size());
      int crossoverPoint = random.nextInt(minSize);

      // Combina LOCs dos pais:
      // - Primeira parte do parent1
      // - Segunda parte do parent2
      childLOCs.addAll(parent1.getSelectedLOCs().subList(0, crossoverPoint));
      childLOCs.addAll(parent2.getSelectedLOCs().subList(crossoverPoint, parent2.getSelectedLOCs().size()));

      // Remove LOCs duplicados (garante unicidade)
      childLOCs = new ArrayList<>(new LinkedHashSet<>(childLOCs));

      child.setSelectedLOCs(childLOCs);
      return child;
   }

   /**
    * Calculates an individual's fitness.
    *
    * @param gpIndividual The individual to be evaluated.
    * @param totalIdsInHostSymbolTable Total number of IDs in the host symbol table.
    * @param mappedIdsInHostIdsInIndividual number of IDs mapped in the individual.
    * @param locsInCurrentIndividual Total LOCs in the individual.
    * @param localTransplantResultLocation Location of the transplant result.
    * @return The individual's fitness.
    */
   public double computeFitness(GPIndividual gpIndividual, int totalIdsInHostSymbolTable,
                                int mappedIdsInHostIdsInIndividual, int locsInCurrentIndividual,
                                String localTransplantResultLocation) {

      // 1. Penaliza indivíduos com muitos LOCs (objetivo: minimizar código)
      double locsScore = 1.0 - ((double) locsInCurrentIndividual / iceBoxLOCs.size());

      // 2. Taxa de mapeamento de IDs (ex: variáveis/funções compatíveis com o hospedeiro)
      double mappingScore = (double) mappedIdsInHostIdsInIndividual / totalIdsInHostSymbolTable;

      // 3. Simulação de teste de compilação (1 se compila, 0 caso contrário)
      boolean compiles = simulateCompilation(gpIndividual, localTransplantResultLocation);
      double compileScore = compiles ? 1.0 : 0.0;

      // Combina os scores
      return (0.4 * locsScore) + (0.3 * mappingScore) + (0.3 * compileScore);
   }

   /**
    * Simula a compilação do individuo
    * @param individual O individuo a ser testado
    * @param outputPath O output da simulação de compilação
    * @return
    */
   private boolean simulateCompilation(GPIndividual individual, String outputPath) {

      // TODO: Implementar lógica para simular a compilação
      // OBS: É aqui onde será implementado o 'ice-box tests'?

      // ** -------------------> CRIANDO ARQUIVO TEMPORÁRIO <-------------------------- //
      /**
      new File(outputPath).mkdirs();

      File dummyFile = new File(outputPath + "/Dummy.java");
      try {
         Files.write(dummyFile.toPath(), "public class Dummy {}".getBytes());
      } catch (IOException e) {
         return false;
      }
       */
      // ** -------------------> || <-------------------------- //

      try {
         // 1. Cria o diretório de saída
         new File(outputPath).mkdirs();

         // 2. Carrega o código original do IceBox
         Path iceBoxPath = Paths.get("IceBox/UtilityClass_reverseString.java");
         List<String> allLines = Files.readAllLines(iceBoxPath);

         // 3. Filtra as linhas selecionadas pelo indivíduo
         List<String> selectedLines = individual.getSelectedLOCs().stream()
                 .map(lineNumber -> allLines.get(lineNumber - 1)) // -1 pois LOCs começam em 1
                 .collect(Collectors.toList());

         // 4. Escreve o código filtrado em um arquivo temporário
         File tempFile = new File(outputPath + "/TransplantedUtilityClass.java");
         Files.write(tempFile.toPath(), selectedLines);

         // 5. Compila o arquivo
         JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
         int compilationResult = compiler.run(null, null, null, tempFile.getAbsolutePath());

         if (selectedLines.stream().noneMatch(line -> line.contains("class UtilityClass"))) {
            return false; // Rejeita código sem a declaração da classe
         }

         return (compilationResult == 0);

      } catch (IOException e) {
         return false;
      }

   }

   /*public void computeFitnessForSubset(List<GPIndividual> individualsSubset, SymbolTable hostSymbolTable,
                                       List<String> skeletonSourceCode, List<String> skeletonLOCsArray, String graftInterfaceTempOutput,
                                       String interfaceHeaderWithGlobalDecl, int totalIdsInHostSymbolTable, List<DependencyList> listOfDependenciesForStatements,
                                       String skeletonInterfaceSourceCodeOutput, List<DependencyListWithID> dependencyListForAbstract,
                                       String finalInterfaceHeaderForCompilation, String txlTemporaryFolder){
      throw new UnsupportedOperationException("Not supported yet.");
   }*/

   /**
    * Gera a população inicial a partir do código extraído para a IceBox.
    *
    * @return Lista de indivíduos da população inicial.
    */
   public List<GPIndividual> generateInitialPopulation() {

      // Verificando se há conteúdo  na lista 'IceBoxLOCs'.
      if (iceBoxLOCs == null || iceBoxLOCs.isEmpty()) {
         throw new IllegalStateException("IceBox LOCs não foram carregados.");
      }

      List<GPIndividual> population = new ArrayList<>();
      Random random = new Random();

      // Indivíduo 0: Over-organ original (todos os LOCs)
      GPIndividual original = new GPIndividual();
      original.setId(0);
      original.setSelectedLOCs(new ArrayList<>(iceBoxLOCs));
      population.add(original);

      // Gera os demais indivíduos com subconjuntos aleatórios
      for (int i = 1; i < POPULATION_SIZE; i++) {
         GPIndividual individual = new GPIndividual();
         individual.setId(i);

         // Tamanho do subconjunto: 50% a 100% dos LOCs
         int minSize = Math.max(1, (int) (iceBoxLOCs.size() * 0.5));
         int maxSize = iceBoxLOCs.size();
         int subsetSize = minSize + random.nextInt(1, maxSize - minSize + 1);

         // Embaralha e seleciona LOCs únicos
         List<Integer> shuffledLOCs = new ArrayList<>(iceBoxLOCs);
         Collections.shuffle(shuffledLOCs);
         List<Integer> selectedLOCs = new ArrayList<>(
                 new LinkedHashSet<>(shuffledLOCs.subList(0, subsetSize))
         );

         individual.setSelectedLOCs(selectedLOCs);
         population.add(individual);
      }

      return population;
   }

   public void loadIceBoxClass(String iceBoxPath) {
      try {
         Path path = Paths.get(iceBoxPath);
         List<String> lines = Files.readAllLines(path);

         // Corrigindo o mapeamento de linhas
         this.iceBoxLOCs = new ArrayList<>();
         for (int i = 0; i < lines.size(); i++) {
            this.iceBoxLOCs.add(i + 1); // Linhas começam em 1
         }

      } catch (IOException e) {
         throw new RuntimeException("Falha ao carregar IceBox: " + e.getMessage());
      }
   }

   // Método para carregar os LOCs
   public void setIceBoxLOCs(List<Integer> iceBoxLOCs) {
      // TODO: Implementar essa lógica
      this.iceBoxLOCs = new ArrayList<>(iceBoxLOCs);
   }

   /**
    * Mutates the individual passed by parameter.
    * Adiciona ou remove LOCs aleatoriamente para manter diversidade genética.
    *
    * @param individual The individual to be mutated.
    */
   public void mutateIndividual(GPIndividual individual) {
      Random random = new Random();
      List<Integer> locs = individual.getSelectedLOCs();

      // 20% de chance de adicionar um LOC ausente do IceBox
      if (random.nextDouble() < 0.2 && !iceBoxLOCs.isEmpty()) {
         // Escolhe um LOC aleatório do IceBox
         Integer newLoc = iceBoxLOCs.get(random.nextInt(iceBoxLOCs.size()));

         // Adiciona apenas se não existir no indivíduo
         if (!locs.contains(newLoc)) {
            locs.add(newLoc);
         }
      }

      // 20% de chance de remover um LOC (exceto se o indivíduo estiver vazio)
      if (random.nextDouble() < 0.2 && !locs.isEmpty()) {
         // Remove um índice aleatório da lista de LOCs
         int indexToRemove = random.nextInt(locs.size());
         locs.remove(indexToRemove);
      }
   }

   /**
    * Executa o GP Algorithm.
    * Controla o loop principal de evolução: avaliação, seleção, crossover e mutação.
    */
   public void run() {

      // Gera a população inicial
      List<GPIndividual> population = generateInitialPopulation();

      for (int gen = 0; gen < MAX_GENERATIONS; gen++) {
         // Passo 1: Avalia o fitness de cada indivíduo
         for (GPIndividual individual : population) {

            // TODO: Avaliar essa parte aqui do código para adaptar para a realidade do ProdScalpel
            // Parâmetros de exemplo (ajuste conforme sua lógica real):
            //int totalIdsInHost = 100; // Total de IDs (variáveis, funções, classes) no código hospedeiro
            int totalIdsInHost = iceBoxLOCs.size();
           // int mappedIds = individual.getSelectedLOCs().size(); // Simplificação: mapeia todos os LOCs
            int mappedIds = (int) individual.getSelectedLOCs().stream()
                    .filter(line -> line <= totalIdsInHost)
                    .count();
            int locs = individual.getSelectedLOCs().size(); // Quantidade de LOCs do indivíduo
            String outputPath = "outputs/simulations"; // Caminho para salvar o output das simulações

            // Calcula o fitness
            double fitness = computeFitness(individual, totalIdsInHost, mappedIds, locs, outputPath);
            individual.setFitness(fitness);
         }

         // Passo 2: Seleciona a próxima geração (elitismo + torneio)
         List<GPIndividual> nextGen = selectNextGeneration(population, POPULATION_SIZE);

         // Passo 3: Gera a nova população via crossover e mutação
         List<GPIndividual> offspring = new ArrayList<>();
         Random random = new Random();

         for (int i = 0; i < POPULATION_SIZE; i++) {
            // Seleciona dois pais aleatórios da próxima geração
            GPIndividual parent1 = nextGen.get(random.nextInt(nextGen.size()));
            GPIndividual parent2 = nextGen.get(random.nextInt(nextGen.size()));

            // Realiza crossover
            GPIndividual child = crossover(parent1, parent2);

            // Aplica mutação
            mutateIndividual(child);

            offspring.add(child);
         }

         // Passo 4: Substitui a população antiga pela nova
         population = offspring;

         // Log do melhor fitness da geração
         GPIndividual best = Collections.max(population, Comparator.comparingDouble(GPIndividual::getFitness));
         System.out.println("Geração " + gen + " | Melhor Fitness: " + best.getFitness());
      }
   }


   /**
    * Selects individuals for the next generation, based on fitness.
    *
    * @param population The current population.
    * @param targetPopulationSize Desired size of the next generation
    * @return The new generation
    */
   public List<GPIndividual> selectNextGeneration(List<GPIndividual> population, int targetPopulationSize) {

      List<GPIndividual> nextGeneration = new ArrayList<>();
      Random random = new Random();

      // Elitismo: Mantém o melhor indivíduo
      GPIndividual best = Collections.max(population, Comparator.comparingDouble(GPIndividual::getFitness));
      nextGeneration.add(best);

      // Preenche o restante com seleção por torneio
      while (nextGeneration.size() < targetPopulationSize) {
         // Seleciona 3 indivíduos aleatórios
         List<GPIndividual> candidates = new ArrayList<>();
         for (int i = 0; i < 3; i++) {
            candidates.add(population.get(random.nextInt(population.size())));
         }
         // Escolhe o melhor do torneio
         GPIndividual winner = Collections.max(candidates, Comparator.comparingDouble(GPIndividual::getFitness));
         nextGeneration.add(winner);
      }

      return nextGeneration;
   }


}
