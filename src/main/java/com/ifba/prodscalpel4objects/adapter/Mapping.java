package com.ifba.prodscalpel4objects.adapter;

/**
 * Classe que representa um Mapping para a associação de variáveis
 * e elementos do código.
 *
 * @author Giovane Neves
 */
public class Mapping {

    private String source;
    private String destination;
    private Mapping next;

    /**
     * Construtor da classe Mapping.
     *
     * @param source Nome original da variável, função ou outro elemento do cpódigio.
     * @param destination Nome para o qual o elemento foi transformado.
     */
    public Mapping(final String source, final String destination) {
        this.source = source;
        this.destination = destination;
        this.next = null;
    }

    // Getters e Setters

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Mapping getNext() {
        return next;
    }

    public void setNext(Mapping next) {
        this.next = next;
    }
}
