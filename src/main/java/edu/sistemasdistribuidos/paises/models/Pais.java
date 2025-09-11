package edu.sistemasdistribuidos.paises.models;

import java.util.Arrays;
import java.util.Map;

public class Pais {
    private Name name;
    private String region;
    private String subregion;
    private String[] capital; // Adicionado
    private Map<String, String> languages;
    private Map<String, Translation> translations;
    private double area;
    private int population;
    private String[] borders;
    private String[] continents;

    // Getters e Setters

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSubregion() {
        return subregion;
    }

    public void setSubregion(String subregion) {
        this.subregion = subregion;
    }

    public String[] getCapital() {
        return capital;
    }

    public void setCapital(String[] capital) {
        this.capital = capital;
    }

    public Map<String, String> getLanguages() {
        return languages;
    }

    public void setLanguages(Map<String, String> languages) {
        this.languages = languages;
    }

    public Map<String, Translation> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, Translation> translations) {
        this.translations = translations;
    }

    public double getArea() {
        return area;
    }

    public void setArea(double area) {
        this.area = area;
    }

    public int getPopulation() {
        return population;
    }

    public void setPopulation(int population) {
        this.population = population;
    }

    public String[] getBorders() {
        return borders;
    }

    public void setBorders(String[] borders) {
        this.borders = borders;
    }

    public String[] getContinents() {
        return continents;
    }

    public void setContinents(String[] continents) {
        this.continents = continents;
    }

    @Override
    public String toString() {
        return "Pais{" +
                "name=" + name +
                ", region='" + region + '\'' +
                ", subregion='" + subregion + '\'' +
                ", capital=" + Arrays.toString(capital) +
                ", languages=" + languages +
                ", translations=" + translations +
                ", area=" + area +
                ", population=" + population +
                ", borders=" + Arrays.toString(borders) +
                ", continents=" + Arrays.toString(continents) +
                '}';
    }
}