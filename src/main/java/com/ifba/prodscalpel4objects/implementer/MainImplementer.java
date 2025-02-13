package com.ifba.prodscalpel4objects.implementer;

import java.io.File;
import java.util.List;

public class MainImplementer {

    public static void main(String[] args) {
        //String hostPath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\hostexample\\src\\main";
        String hostPath = "C:\\Users\\Micro\\IdeaProjects\\xp-news-backend\\xp-news-backend\\src\\main";

        String receiverPath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\receiverexample\\src\\main";
            String methodName = "processData";

            Implementer implementer = new Implementer(hostPath, receiverPath);

            List<File> javaFiles = implementer.findJavaFilesInProject();

            implementer.implement();

        }
}
