package ru.practicum.moviehub.controller;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.formatter.JsonFormatter;
import ru.practicum.moviehub.model.CreateMovieRequest;
import ru.practicum.moviehub.model.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.storage.MoviesStore;

import java.io.IOException;
import java.net.URLDecoder;
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
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/movies")) {
                switch (method) {
                    case "GET" -> handleGetCollection(exchange);
                    case "POST" -> handlePostRequest(exchange);
                    default -> sendMethodNotAllowed(exchange, "GET, POST");
                }
                return;
            }
            if (!path.startsWith("/movies/") || path.substring(8).isEmpty()
                    || path.substring(8).contains("/")) {
                sendNotFound(exchange);
                return;
            }
            if (!method.equals("GET") && !method.equals("DELETE")) {
                sendMethodNotAllowed(exchange, "GET, DELETE");
                return;
            }
            int id;
            try {
                id = Integer.parseInt(path.substring(8));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Bad Request", "Некорректный ID");
                return;
            }
            if (exchange.getRequestURI().getRawQuery() != null) {
                sendError(exchange, 400, "Bad Request", "Параметры запроса допустимы только для GET /movies");
                return;
            }
            if (method.equals("DELETE")) {
                if (store.deleteById(id) == null) {
                    sendNotFound(exchange);
                } else {
                    sendNoContent(exchange);
                }
            } else {
                Movie movie = store.getById(id);
                if (movie == null) {
                    sendNotFound(exchange);
                } else {
                    sendJson(exchange, 200, formatter.toJson(movie));
                }
            }
        }
    }

    private void handleGetCollection(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            sendJson(exchange, 200, formatter.toJsonList(store.getAll()));
            return;
        }
        int year;
        try {
            String[] parameters = query.split("&", -1);
            String[] parameter = parameters[0].split("=", -1);
            if (parameters.length != 1 || parameter.length != 2
                    || !URLDecoder.decode(parameter[0], StandardCharsets.UTF_8).equals("year")) {
                throw new IllegalArgumentException("Ожидается один параметр year");
            }
            year = Integer.parseInt(URLDecoder.decode(parameter[1], StandardCharsets.UTF_8));
            if (year < 1888 || year > Year.now().getValue() + 1) {
                throw new IllegalArgumentException("Год вне допустимого диапазона");
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Bad Request", "Некорректный год");
            return;
        }
        sendJson(exchange, 200, formatter.toJsonList(store.getByYear(year)));
    }

    private void handlePostRequest(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getRawQuery() != null) {
            sendError(exchange, 400, "Bad Request", "POST /movies не поддерживает параметры запроса");
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null
                || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            sendError(exchange, 415, "Неподдерживаемый формат данных",
                    "Ожидается Content-Type: application/json");
            return;
        }
        CreateMovieRequest request;
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request = formatter.fromJsonRequest(body);
        } catch (JsonParseException e) {
            sendError(exchange, 400, "Некорректный JSON", "Не удалось прочитать тело запроса");
            return;
        }
        List<String> errors = new ArrayList<>();
        if (request.title() == null || request.title().isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (request.title().length() > 100) {
            errors.add("название должно быть не более 100 символов");
        }
        int maximumYear = Year.now().getValue() + 1;
        if (request.year() == null || request.year() < 1888 || request.year() > maximumYear) {
            errors.add("год должен быть между 1888 и " + maximumYear);
        }
        if (!errors.isEmpty()) {
            sendJson(exchange, 422, formatter.toJson(new ErrorResponse("Ошибка валидации", errors)));
            return;
        }
        Movie created = store.add(request.title(), request.year());
        sendJson(exchange, 201, formatter.toJson(created));
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allowedMethods) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethods);
        sendError(exchange, 405, "Method not allowed", "Метод не поддерживается для этого ресурса");
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        sendError(exchange, 404, "Not Found", "Ресурс не найден");
    }

    private void sendError(HttpExchange exchange, int status, String error, String detail) throws IOException {
        sendJson(exchange, status, formatter.toJson(new ErrorResponse(error, List.of(detail))));
    }
}
