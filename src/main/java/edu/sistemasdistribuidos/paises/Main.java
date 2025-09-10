package edu.sistemasdistribuidos.paises;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import edu.sistemasdistribuidos.paises.models.Pais;

public class Main {
    public static void main(String[] args) throws Exception, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("https://restcountries.com/v3.1/all?fields=area,borders,continents,demonym,name,languages,population,region,subregion,translations")).build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            Gson gson = new Gson();
            Type paisListType = new TypeToken<List<Pais>>(){}.getType();
            List<Pais> paises = gson.fromJson(response.body(), paisListType);
            paises.forEach(pais -> System.out.println(pais.getName().getCommon()));
        }
    }
}