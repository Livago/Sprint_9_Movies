package ru.practicum.moviehub.controller;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.formatter.JsonFormatter;
import ru.practicum.moviehub.model.CreateMovieRequest;
import ru.practicum.moviehub.model.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.storage.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final JsonFormatter formatter;

    public MoviesHandler(MoviesStore store, JsonFormatter formatter) {
        this.store = store;
        this.formatter = formatter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        switch (method) {
            case "GET" -> handleGetRequest(exchange);
            case "POST" -> handlePostRequest(exchange);
            case "DELETE" -> handleDeleteRequest(exchange);
            default -> sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private void handleGetRequest(HttpExchange exchange) throws IOException {
        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();
        String query = requestURI.getQuery();

        if (query != null && query.startsWith("year=")) {
            try {
                short year = Short.parseShort(query.substring(5));

                List<Movie> movies = store.getByYear(year);

                sendJson(exchange, 200, formatter.toJsonList(movies));
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, formatter.toJson(
                        new ErrorResponse(
                                "Bad Request",
                                List.of("Некорректный год")
                        )
                ));
            }

            return;
        }

        if (path.equals("/movies") && query == null) {
            List<Movie> movies = store.getAll();
            sendJson(exchange, 200, formatter.toJsonList(movies));
            return;
        }

        if (path.startsWith("/movies/")) {
            String idString = path.substring("/movies/".length());

            if (idString.isEmpty() || idString.contains("/")) {
                sendNotFound(exchange);
                return;
            }

            try {
                Movie movie = store.getById(Integer.parseInt(idString));

                if (movie == null) {
                    sendNotFound(exchange);
                    return;
                }
                sendJson(exchange, 200, formatter.toJson(movie));
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, formatter.toJson(
                        new ErrorResponse(
                                "Bad Request",
                                List.of("Некорректный ID")
                        )
                ));
            }
        }

        sendNotFound(exchange);
    }

    private void handlePostRequest(HttpExchange exchange) throws IOException {
        // Проверяем Content-Type
        String contentType = exchange.getRequestHeaders()
                .getFirst("Content-Type");

        if (contentType == null
                || !contentType.split(";", 2)[0].trim()
                        .equalsIgnoreCase("application/json")) {
            sendJson(exchange, 415, formatter.toJson(
                    new ErrorResponse(
                            "Неподдерживаемый формат данных",
                            List.of("Ожидается Content-Type: application/json")
                    )
            ));
            return;
        }

        // Десериализуем JSON
        CreateMovieRequest request;

        try {
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            request = formatter.fromJsonRequest(body);

        } catch (JsonParseException e) {
            sendJson(exchange, 400, formatter.toJson(
                    new ErrorResponse(
                            "Некорректный JSON",
                            List.of("Не удалось прочитать тело запроса")
                    )
            ));
            return;
        }

        // Собираем ошибки валидации
        List<String> errors = new ArrayList<>();

        if (request == null
                || request.name() == null
                || request.name().isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (request.name().length() > 100) {
            errors.add("название должно быть менее 100 символов");
        }

        if (request != null
                && (request.year() < 1888 || request.year() > Year.now().getValue() + 1)) {
            errors.add("год должен быть между 1888 и 2027");
        }

        // Возвращаем все обнаруженные ошибки
        if (!errors.isEmpty()) {
            sendJson(exchange, 422, formatter.toJson(
                    new ErrorResponse("Ошибка валидации", errors)
            ));
            return;
        }

        // Сохраняем фильм
        Movie created = store.add(request.name(), request.year());
        sendJson(exchange, 201, formatter.toJson(created));
    }

    private void handleDeleteRequest(HttpExchange exchange) throws IOException {
        URI requestURI = exchange.getRequestURI();

        String path = requestURI.getPath();

        if (path.startsWith("/movies/")) {
            String idString = path.substring("/movies/".length());

            if (idString.isEmpty() || idString.contains("/")) {
                sendNotFound(exchange);
                return;
            }

            try {
                if (store.getById(Integer.parseInt(idString)) == null) {
                    sendNotFound(exchange);
                    return;
                }

                store.deleteById(Integer.parseInt(idString));
                sendNoContent(exchange);
                return;

            } catch (NumberFormatException e) {
                sendJson(exchange, 400, formatter.toJson(
                        new ErrorResponse(
                                "Bad Request",
                                List.of("Некорректный ID")
                        )
                ));
            }
        }
        sendNotFound(exchange);
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        sendJson(exchange, 404, formatter.toJson(
                new ErrorResponse(
                        "Not Found",
                        List.of("Ресурс не найден")
                )
        ));
    }
}
