package ru.practicum.moviehub.formatter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
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
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            reader.beginObject();
            String title = null;
            Integer year = null;
            Set<String> fields = new HashSet<>();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) {
                    throw new JsonParseException("Повторяющееся поле: " + field);
                }
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                } else if (field.equals("title")) {
                    if (reader.peek() != JsonToken.STRING) {
                        throw new JsonParseException("title должен быть строкой");
                    }
                    title = reader.nextString();
                } else if (field.equals("year")) {
                    if (reader.peek() != JsonToken.NUMBER) {
                        throw new JsonParseException("year должен быть целым числом");
                    }
                    year = new BigDecimal(reader.nextString()).intValueExact();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("Лишние данные после объекта");
            }
            return new CreateMovieRequest(title, year);
        } catch (IOException | IllegalStateException | ArithmeticException | NumberFormatException e) {
            throw new JsonParseException("Не удалось прочитать тело запроса", e);
        }
    }

    public List<Movie> fromJsonList(String json) {
        Movie[] movies = gson.fromJson(json, Movie[].class);
        return Arrays.asList(movies);
    }

    public ErrorResponse fromJsonError(String json) {
        return gson.fromJson(json, ErrorResponse.class);
    }

}
