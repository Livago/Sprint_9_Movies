package ru.practicum.moviehub.formatter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ru.practicum.moviehub.model.CreateMovieRequest;
import ru.practicum.moviehub.model.ErrorResponse;
import ru.practicum.moviehub.model.Movie;

import java.util.Arrays;
import java.util.List;

public class JsonFormatter {

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public String toJson(CreateMovieRequest movie) {
        return gson.toJson(movie);
    }

    public String toJson(Movie movie) {
        return gson.toJson(movie);
    }

    public String toJson(ErrorResponse error) {
        return gson.toJson(error);
    }

    public String toJsonList(List<Movie> movies) {
        return gson.toJson(movies);
    }

    public Movie fromJson(String json) {
        return gson.fromJson(json, Movie.class);
    }

    public CreateMovieRequest fromJsonRequest(String json) {
        return gson.fromJson(json, CreateMovieRequest.class);
    }

    public List<Movie> fromJsonList(String json) {
        Movie[] movies = gson.fromJson(json, Movie[].class);
        return Arrays.asList(movies);
    }

    public ErrorResponse fromJsonError(String json) {
        return gson.fromJson(json, ErrorResponse.class);
    }

    public String toJson(String invalidJson) {
        return null;
    }
}
