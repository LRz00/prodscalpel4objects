package com.ifba.prodscalpel4objects.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
* Core GP Algorithm.
*
* @author Giovane Neves
*/
public class GPAlgorithm {

   /**
    * Define o tamanho máximo da população para 100;
    * Foi selecionado o valor 100 para evitar o problema de 'máximo local',
    * onde a solução é melhor que as suas vizinhas, mas não é a melhor globalmente.
    */
   private final static int POPULATION_SIZE = 100;

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
      throw new UnsupportedOperationException("Not supported yet.");
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

      // 3. Simulação de teste de compilação (ex: 1.0 se compila, 0.0 caso contrário)
      boolean compiles = simulateCompilation(gpIndividual, localTransplantResultLocation);
      double compileScore = compiles ? 1.0 : 0.0;

      // Combine os scores (ajuste os pesos conforme necessário)
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
      return true; // Simulação
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
         int subsetSize = minSize + random.nextInt(maxSize - minSize + 1);

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

   // Método para carregar os LOCs (exemplo)
   public void setIceBoxLOCs(List<Integer> iceBoxLOCs) {
      this.iceBoxLOCs = new ArrayList<>(iceBoxLOCs);
   }

   /**
    * Mutates the individual passed by parameter.
    *
    * @param individual The individual to be mutated.
    */
   public void mutateIndividual(GPIndividual individual) {
      throw new UnsupportedOperationException("Not supported yet.");
   }
   /**
   * Executa o GP Algorithm.
   * 
   */
   public void run(){
     // TODO: Adiciona lógica do GP Algorithm 
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
