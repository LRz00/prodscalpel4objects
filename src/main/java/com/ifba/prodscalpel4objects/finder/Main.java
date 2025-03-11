package com.ifba.prodscalpel4objects.finder;

public class Main {
    public static void main(String[] args) {
        Finder find = new Finder();

        var result = find.execute("C:\\Users\\kaioe\\OneDrive\\Área de Trabalho\\soma_multiplicacao\\src\\main\\java\\com\\ifba", "soma");

        System.out.println("Caminho da Classe de origem do método: " + result.classOriginPath());
        System.out.println("Nome da Classe onde o método está localizado: " + result.className());

        System.out.print("Local onde o método foi chamado: ");
        for (String s : result.classCallPaths()) {
            System.out.println(s);
        }

    }
}
