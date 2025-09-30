package com.ifba.prodscalpel4objects.implanter;

import com.ifba.prodscalpel4objects.implanter.services.MethodImplanter;
import com.ifba.prodscalpel4objects.implanter.services.MethodImplanterDeprecated;

public class MainImplanter {

    public static void main(String[] args) {
        //String hostPath = "C:\\Users\\Micro\\IdeaProjects\\prodscalpel4objects\\IceBox";
        String hostPath = "C:\\Users\\Micro\\IdeaProjects\\xp-news-backend\\xp-news-backend\\src\\main";
        String hostPomPath = "C:\\Users\\Micro\\IdeaProjects\\xp-news-backend\\xp-news-backend\\pom.xml";

        String receiverPath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\receiverexample\\src\\main\\java\\org\\exemple\\receiverexample\\";
        String receiverPomPath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\receiverexample\\pom.xml";

        MethodImplanter implanter = new MethodImplanter(hostPath);
        implanter.addReceiverPath(receiverPath);

        MethodImplanterDeprecated methodImplanterDeprecated = new MethodImplanterDeprecated(hostPath);
        methodImplanterDeprecated.addReceiverPath(receiverPath);

        implanter.implant();
        //methodImplanterDepre.implant();

        }
}
