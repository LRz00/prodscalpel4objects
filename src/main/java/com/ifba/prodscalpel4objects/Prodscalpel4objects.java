/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.ifba.prodscalpel4objects;

import com.ifba.prodscalpel4objects.extractor.MethodExtractorV1;

/**
 *
 * @author lara
 */
public class Prodscalpel4objects {

    public static void main(String[] args) {
        String sourceRootPath = "C:\\Users\\Lara.rodrigues\\Documents\\petresgate-master\\src\\main\\java";

        // Caminho completo para o arquivo-fonte que contém o método processData
        String sourceFilePath = "C:\\Users\\Lara.rodrigues\\Documents\\petresgate-master\\src\\main\\java\\br\\com\\ifba\\petresgate\\service\\AnimalService.java";

        // Nome do método a ser extraído
        String methodName = "saveAnimal";

        // Cria uma instância do extrator de métodos
        MethodExtractorV1 methodExtractor = new MethodExtractorV1(sourceRootPath);

        // Extrai o método especificado para um novo arquivo
        methodExtractor.extract(sourceFilePath, methodName);

        System.out.println("Extração concluída.");    }
}
