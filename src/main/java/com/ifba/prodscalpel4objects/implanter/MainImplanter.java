package com.ifba.prodscalpel4objects.implanter;

import com.ifba.prodscalpel4objects.implanter.services.MethodImplanter;

public class MainImplanter {

    public static void main(String[] args) {
        String hostPath = "C:\\Users\\Micro\\IdeaProjects\\xp-news-backend\\xp-news-backend\\src\\main";
        String receiverPath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\receiverexample\\src\\main\\java\\org\\exemple\\receiverexample\\";

        MethodImplanter implanter = new MethodImplanter(hostPath);
        implanter.addReceiverPath(receiverPath);

        // =========================================================================
        // EXEMPLO DE CONFIGURAÇÃO DE HOOKS AUTOMÁTICOS
        // Você informa o nome do arquivo, a assinatura do método alvo, e o código.
        // =========================================================================
        implanter.addHook(
                "UserService.java",
                "User create(User user)",
                "System.out.println(\"[HOOK] Usuário criado de forma injetada via Scalpel!\");"
        );

        // Aciona o transplante que aplicará todas as etapas (cópia, yaml, hooks e dependências)
        implanter.implant();
    }
}