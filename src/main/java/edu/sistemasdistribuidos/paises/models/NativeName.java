package edu.sistemasdistribuidos.paises.models;

public class NativeName {
    private String official;
    private String common;

    // Getters e Setters

    public String getOfficial() {
        return official;
    }

    public void setOfficial(String official) {
        this.official = official;
    }

    public String getCommon() {
        return common;
    }

    public void setCommon(String common) {
        this.common = common;
    }

    @Override
    public String toString() {
        return "NativeName{" +
                "official='" + official + '\'' +
                ", common='" + common + '\'' +
                '}';
    }
}
