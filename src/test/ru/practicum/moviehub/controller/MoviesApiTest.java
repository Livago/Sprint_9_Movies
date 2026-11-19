package ru.practicum.moviehub.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.formatter.JsonFormatter;
import ru.practicum.moviehub.model.CreateMovieRequest;
import ru.practicum.moviehub.model.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.storage.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;
    private static JsonFormatter formatter;

    @BeforeEach
    void beforeEach() {
        store = new MoviesStore();
        formatter = new JsonFormatter();
        server = new MoviesServer(8080, store, formatter);

        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovie_whenExist_returnsMovies() throws Exception {
        Movie testMovie = createTestMovie("Titanic", (short) 1997);
        Movie testMovie2 = createTestMovie("The Shawshank Redemption", (short) 1994);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = formatter.fromJsonList(response.body());

        assertEquals(2, movies.size(), "Размер массива объектов не совпадает с целевым");
        assertEquals(testMovie.id(), movies.getFirst().id());
        assertEquals(testMovie.name(), movies.getFirst().name());
        assertEquals(testMovie.year(), movies.getFirst().year());
    }

    @Test
    void getMovie_byId_returnsMovie() throws Exception {
        Movie testMovie = createTestMovie("Nosferatu", (short) 1922);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + testMovie.id()))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie receivedMovie = formatter.fromJson(response.body());

        assertAll(
                () -> assertEquals(200, response.statusCode(), "GET /movies/{id} должен вернуть 200"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertTrue(receivedMovie.id() > 0,
                        "В ответе отсутвует id"),
                () -> assertEquals(testMovie.name(), receivedMovie.name(),
                        "Название фильма в ответе не совпадает с отправленным"),
                () -> assertEquals(testMovie.year(), receivedMovie.year(),
                        "Год фильма в ответе не совпадает с отправленным"),
                () -> assertEquals(receivedMovie, store.getById(testMovie.id()),
                        "Полученные в ответе фильм не совпадает с сохраненным")
        );
    }

    @Test
    void getMovie_byId_notFound_return404() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, response.statusCode(), "POST /movies должен вернуть 404");

        ErrorResponse error = formatter.fromJsonError(response.body());

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Not Found", error.error()),
                () -> assertTrue(error.details().contains("Ресурс не найден"))
        );
    }

    @Test
    void getMovie_byId_whenNotNum_return400() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/qwerty"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(), "GET /movies/abc должен вернуть 400");

        ErrorResponse error = formatter.fromJsonError(response.body());

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Bad Request", error.error()),
                () -> assertTrue(error.details().contains("Некорректный ID"))
        );
    }

    @Test
    void getMovie_byYear_returnsMovies() throws Exception {
        Movie ff = createTestMovie("The Fast and the Furious", (short) 2001);
        Movie parasites = createTestMovie("Parasite", (short) 2019);
        Movie avengers = createTestMovie("Avengers: Endgame", (short) 2019);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "?year=" + parasites.year()))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        List<Movie> movies = formatter.fromJsonList(response.body());

        assertAll(
                () -> assertEquals(200, response.statusCode(), "GET /movies?{year} должен вернуть 200"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertEquals(2, movies.size(), "Размер массива объектов не совпадает с целевым")
        );
    }

    @Test
    void getMovie_byYear_notFound_returnsEmptyArray() throws Exception {
        Movie ff = createTestMovie("The Fast and the Furious", (short) 2001);
        Movie parasites = createTestMovie("Parasite", (short) 2019);
        Movie avengers = createTestMovie("Avengers: Endgame", (short) 2019);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "?year=" + 1899))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovie_byYear_whenNotNum_return400() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies" + "?year=" + "abc"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(), "GET /movies?year=YYYY должен вернуть 400");

        ErrorResponse error = formatter.fromJsonError(response.body());

        assertAll(
                () -> assertEquals(400, response.statusCode(), "GET /movies/abc должен вернуть 400"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Bad Request", error.error()),
                () -> assertTrue(error.details().contains("Некорректный год"))
        );
    }

    @Test
    void postMovie_whenValid_createsMovie() throws Exception {
        CreateMovieRequest request = createTestMovieRequest("Titanic", (short) 1997);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie createdMovie = formatter.fromJson(response.body());

        assertAll(
                () -> assertEquals(201, response.statusCode(), "POST /movies должен вернуть 201"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertTrue(createdMovie.id() > 0,
                        "В ответе отсутвует id"),
                () -> assertEquals(request.name(), createdMovie.name(),
                        "Название фильма в ответе не совпадает с отправленным"),
                () -> assertEquals(request.year(), createdMovie.year(),
                        "Год фильма в ответе не совпадает с отправленным"),
                () -> assertEquals(createdMovie, store.getById(createdMovie.id()),
                        "Полученные в ответе фильм не совпадает с сохраненным")
        );
    }

    @Test
    void postMovie_noTitle_returns422() throws Exception {
        CreateMovieRequest request = createTestMovieRequest(" ", (short) 1999);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode(), "POST /movies должен вернуть 422");

        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject();
        JsonArray details = error.getAsJsonArray("details");

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Ошибка валидации", error.get("error").getAsString()),
                () -> assertEquals(1, details.size(), "Некорректное кол-во сообщений об ошибках в детализации")
        );
    }

    @Test
    void postMovie_longTitle_returns422() throws Exception {
        CreateMovieRequest request = createTestMovieRequest("Невероятно длинное название, сделанное исключительно для проверки валидации длины при создании фильма в апи",
                (short) 1999);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode(), "POST /movies должен вернуть 422");

        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject();
        JsonArray details = error.getAsJsonArray("details");

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Ошибка валидации", error.get("error").getAsString()),
                () -> assertEquals(1, details.size(), "Некорректное кол-во сообщений об ошибках в детализации")
        );
    }

    @Test
    void postMovie_incorrectYear_returns422() throws Exception {
        CreateMovieRequest request = createTestMovieRequest("Test", (short) 2099);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode(), "POST /movies должен вернуть 422");

        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject();
        JsonArray details = error.getAsJsonArray("details");

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Ошибка валидации", error.get("error").getAsString()),
                () -> assertEquals(1, details.size(), "Некорректное кол-во сообщений об ошибках в детализации")
        );
    }

    @Test
    void postMovie_whenInvalidContent_returns415() throws Exception {
        CreateMovieRequest request = createTestMovieRequest("Titanic", (short) 1997);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/html")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, response.statusCode(), "POST /movies должен вернуть 415");

        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject();
        JsonArray details = error.getAsJsonArray("details");

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Неподдерживаемый формат данных", error.get("error").getAsString()),
                () -> assertEquals(1, details.size(), "Некорректное кол-во сообщений об ошибках в детализации")
        );
    }

    @Test
    void postMovie_whenInvalidJson_returns400() throws Exception {
        String invalidJson = """
                {"name": "Titanic", "year": }
                """;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(), "POST /movies должен вернуть 400");

        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject();
        JsonArray details = error.getAsJsonArray("details");

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Некорректный JSON", error.get("error").getAsString()),
                () -> assertEquals(1, details.size(), "Не удалось прочитать тело запроса")
        );
    }

    @Test
    void deleteMovie_byId_removesMovie() throws Exception {
        Movie testMovie = createTestMovie("Nosferatu", (short) 1922);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + 1))
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, response.statusCode(), "DELETE /movies/{id} должен вернуть 204");

        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = response.body();
        assertTrue(body.isEmpty(),
                "Ожидается JSON-массив");
    }

    @Test
    void deleteMovie_byId_notFound_return404() throws Exception {
        Movie testMovie = createTestMovie("Nosferatu", (short) 1922);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + 999))
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, response.statusCode(), "POST /movies должен вернуть 404");

        ErrorResponse error = formatter.fromJsonError(response.body());

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Not Found", error.error()),
                () -> assertTrue(error.details().contains("Ресурс не найден"))
        );
    }

    @Test
    void deleteMovie_byId_notNum_return400() throws Exception {
        Movie testMovie = createTestMovie("Nosferatu", (short) 1922);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + "abc"))
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(), "DELETE /movies/{abc} должен вернуть 400");

        ErrorResponse error = formatter.fromJsonError(response.body());

        assertAll(
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        response.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(response.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Bad Request", error.error()),
                () -> assertTrue(error.details().contains("Некорректный ID"))
        );
    }

    private Movie createTestMovie(String name, short year) {
        return store.add(name, year);
    }

    private CreateMovieRequest createTestMovieRequest(String name, short year) {
        return new CreateMovieRequest(name, year);
    }

}
