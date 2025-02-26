package com.ifba.prodscalpel4objects.adapter;

import java.io.File;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        String iceBoxPath = "IceBox/UtilityClass_reverseString.java";
        File iceBoxFile = new File(iceBoxPath);

        if (!iceBoxFile.exists()) {
            System.err.println("Arquivo do IceBox não encontrado: " + iceBoxFile.getAbsolutePath());
            return;
        }

        GPAlgorithm gp = new GPAlgorithm();
        gp.loadIceBoxClass(iceBoxPath);
        gp.run();
    }
}