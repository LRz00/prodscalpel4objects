package com.ifba.prodscalpel4objects.implanter;

import java.io.File;
import java.util.List;

public class MainImplanter {

    public static void main(String[] args) {
        String hostPath = "C:\\Users\\Micro\\IdeaProjects\\prodscalpel4objects\\IceBox";
        //String hostPath = "C:\\Users\\Micro\\IdeaProjects\\xp-news-backend\\xp-news-backend\\src\\main";

        String receiverPath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\receiverexample\\src\\main";

            MethodImplanter implementer = new MethodImplanter(hostPath, receiverPath);

            List<File> javaFiles = implementer.findJavaFilesInProject();

            implementer.implant();

        }
}
