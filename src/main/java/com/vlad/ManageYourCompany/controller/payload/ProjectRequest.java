package com.vlad.ManageYourCompany.controller.payload;

public class ProjectRequest {

    private String nume;
    private String locatie;
    private Integer numarResurseNecesare;
    private String descriere;

    public String getNume() {
        return nume;
    }

    public void setNume(String nume) {
        this.nume = nume;
    }

    public String getLocatie() {
        return locatie;
    }

    public void setLocatie(String locatie) {
        this.locatie = locatie;
    }

    public Integer getNumarResurseNecesare() {
        return numarResurseNecesare;
    }

    public void setNumarResurseNecesare(Integer numarResurseNecesare) {
        this.numarResurseNecesare = numarResurseNecesare;
    }

    public String getDescriere() {
        return descriere;
    }

    public void setDescriere(String descriere) {
        this.descriere = descriere;
    }
}
