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
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        switch(method) {
            case "GET" -> handleGetRequest(ex);
            case "POST" -> handlePostRequest(ex);
            case "DELETE" -> handleDeleteRequest(ex);
            default -> sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private void handleGetRequest(HttpExchange ex) throws IOException {
        URI requestURI = ex.getRequestURI();
        String path = requestURI.getPath();

        if (path.equals("/movies")) {
            List<Movie> movies = store.getAll();
            sendJson(ex, 200, formatter.toJsonList(movies));
            return;
        }

        if (path.startsWith("/movies/")) {
            String idString = path.substring("/movies/".length());

            if (idString.isEmpty() || idString.contains("/")) {
                sendNotFound(ex);
                return;
            }

            try {
                Movie movie = store.getById(Integer.parseInt(idString));

                if (movie == null) {
                    sendNotFound(ex);
                    return;
                }
                sendJson(ex, 200, formatter.toJson(movie));
            } catch (NumberFormatException e) {
                sendJson(ex, 400, formatter.toJson(
                        new ErrorResponse(
                                "Bad Request",
                                List.of("Некорректный ID")
                        )
                ));
            }
        }
        sendNotFound(ex);
    }

    private void handlePostRequest(HttpExchange ex) throws IOException {

        // Проверяем Content-Type
        String contentType = ex.getRequestHeaders()
                .getFirst("Content-Type");

        if (contentType == null ||
                !contentType.split(";", 2)[0].trim()
                        .equalsIgnoreCase("application/json")) {

            sendJson(ex, 415, formatter.toJson(
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
                    ex.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            request = formatter.fromJsonRequest(body);

        } catch (JsonParseException e) {
            sendJson(ex, 400, formatter.toJson(
                    new ErrorResponse(
                            "Некорректный JSON",
                            List.of("Не удалось прочитать тело запроса")
                    )
            ));
            return;
        }

        // Собираем ошибки валидации
        List<String> errors = new ArrayList<>();

        if (request == null ||
                request.name() == null ||
                request.name().isBlank()) {
            errors.add("название не должно быть пустым");
        }

        if (request != null &&
                (request.year() < 1888 || request.year() > Year.now().getValue() + 1 )) {
            errors.add("год должен быть между 1888 и 2027");
        }

        // Возвращаем все обнаруженные ошибки
        if (!errors.isEmpty()) {
            sendJson(ex, 422, formatter.toJson(
                    new ErrorResponse("Ошибка валидации", errors)
            ));
            return;
        }

        // Сохраняем фильм
        Movie created = store.add(request.name(), request.year());
        sendJson(ex, 201, formatter.toJson(created));
    }

    private void handleDeleteRequest(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        String[] parts = path.split("/");
        String id = parts[2];

        System.out.println("Удаляем заметку с ID: " + id);
        sendNoContent(ex);
    }

    private void sendNotFound(HttpExchange ex) throws IOException {
        sendJson(ex, 404, formatter.toJson(
                new ErrorResponse(
                        "Not Found",
                        List.of("Ресурс не найден")
                )
        ));
    }
}