package com.ifba.prodscalpel4objects.adapter;

import javassist.compiler.SymbolTable;

import java.util.List;

/**
* Core GP Algorithm.
*
* @author Giovane Neves
*/
public class GPAlgorithm {

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
      throw new UnsupportedOperationException("Not supported yet.");
   }

   /*public void computeFitnessForSubset(List<GPIndividual> individualsSubset, SymbolTable hostSymbolTable,
                                       List<String> skeletonSourceCode, List<String> skeletonLOCsArray, String graftInterfaceTempOutput,
                                       String interfaceHeaderWithGlobalDecl, int totalIdsInHostSymbolTable, List<DependencyList> listOfDependenciesForStatements,
                                       String skeletonInterfaceSourceCodeOutput, List<DependencyListWithID> dependencyListForAbstract,
                                       String finalInterfaceHeaderForCompilation, String txlTemporaryFolder){
      throw new UnsupportedOperationException("Not supported yet.");
   }*/
   /**
    * Creates the intial population
    *
    * @return The initial population
    */
   public List<GPIndividual> generateInitialPopulation() {
      throw new UnsupportedOperationException("Not supported yet.");
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
      throw new UnsupportedOperationException("Not supported yet.");
   }

}
