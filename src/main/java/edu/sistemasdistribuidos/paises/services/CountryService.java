package edu.sistemasdistribuidos.paises.services;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLContext;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import edu.sistemasdistribuidos.paises.models.Pais;

public class CountryService {

    private final HttpClient http;
    private final Gson gson = new Gson();
    private static final String BASE_URL = "https://restcountries.com/v3.1/";
    private static final String FIELDS = "?fields=name,region,capital,area,population,languages,translations";

    // Construtor que inicializa o HttpClient com configuração SSL
    public CountryService() {
        HttpClient client;
        try {
            client = HttpClient.newBuilder()
                    .sslContext(SSLContext.getDefault())
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[SERVICE_WARNING] Não foi possível obter o contexto SSL padrão. Usando o HttpClient padrão.");
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
        }
        this.http = client;
    }

    // Método principal para encontrar um país por nome ou tradução
    public Pais findCountry(String name) {
        Pais country = fetchByTranslation(name);
        if (country != null) return country;

        country = fetchByName(name, true);
        if (country != null) return country;

        return fetchByName(name, false);
    }

    // Busca país pelo nome, com opção de busca exata ou parcial
    private Pais fetchByName(String name, boolean fullText) {
        String endpoint = "name/" + encode(name) + FIELDS + (fullText ? "&fullText=true" : "");
        return fetchFromApi(endpoint);
    }

    // Busca país pela tradução do nome
    private Pais fetchByTranslation(String name) {
        String endpoint = "translation/" + encode(name) + FIELDS;
        return fetchFromApi(endpoint);
    }

    // Método auxiliar para fazer a requisição HTTP e processar a resposta
    private Pais fetchFromApi(String endpoint) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            // Envia a requisição e obtém a resposta
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            // Verifica se a resposta foi bem-sucedida
            if (resp.statusCode() != 200) {
                return null;
            }

            // Desserializa a resposta JSON em uma lista de países
            Type paisListType = new TypeToken<List<Pais>>(){}.getType();
            List<Pais> paises = gson.fromJson(resp.body(), paisListType);

            return paises.isEmpty() ? null : paises.get(0);
        } catch (Exception e) {
            System.err.println("[SERVICE_ERROR] Falha ao buscar dados para o endpoint '" + endpoint + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    // Método auxiliar para codificar parâmetros de URL
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}