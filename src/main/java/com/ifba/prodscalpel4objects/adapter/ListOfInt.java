package com.ifba.prodscalpel4objects.adapter;

/**
 * Classe que representa um linha de código
 *
 * @author Giovane Neves
 */
public class ListOfInt {

    private int value;
    private ListOfInt next;

    /**
     * Construtor da classe ListOfInt
     *
     * @param value O valor da linha de código
     */
    public ListOfInt(final int value) {
        this.value = value;
    }

    // Getters & Setters

    public int getValue() {
        return value;
    }

    public void setValue(final int value) {
        this.value = value;
    }

    public ListOfInt getNext() {
        return next;
    }

    public void setNext(ListOfInt next) {
        this.next = next;
    }

}
