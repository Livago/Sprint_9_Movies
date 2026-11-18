package ru.practicum.moviehub.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
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

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080"; // !!! добавьте базовую часть URL
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;
    private static JsonFormatter formatter;

    @BeforeEach
    void beforeEach() {

        /// Здесь некоторый магический прием Dependency Injection,
        /// Передаю ссылку на обект MoviesStore серверу.
        /// Делаю это чтобы внутри него была возможность добавлять фильмы через методы.
        /// При этом сама переменная (ссылка) store будет использоваться для вызова add в отдельных случаях в обход сервера, чудеса.

        store = new MoviesStore();
        formatter = new JsonFormatter();
        server = new MoviesServer(8080, store, formatter);

        server.start();

        /// Создаю новый клиент чтобы посылать запросы с его помощью.

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        /// Тесты будут идти в таком порядке:
        /// Post 422, недопустимое тело
        /// Post 415, недопустимый тип
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovie_whenExist_returnsMovie() throws Exception {
        Movie testMovie = createTestMovie("Titanic", (short) 1997);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = formatter.fromJsonList(resp.body());

        assertEquals(1, movies.size(), "Размер массива объектов не совпадает с целевым");
        assertEquals(testMovie.id(), movies.getFirst().id());
        assertEquals(testMovie.name(), movies.getFirst().name());
        assertEquals(testMovie.year(), movies.getFirst().year());
    }

    @Test
    void getMovie_ById_returnsMovie() throws Exception {
        Movie testMovie = createTestMovie("Nosferatu", (short) 1922);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + testMovie.id()))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie recievedMovie = formatter.fromJson(resp.body());

        assertAll(
                () -> assertEquals(200, resp.statusCode(), "GET /movies/{id} должен вернуть 200"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertTrue(recievedMovie.id() > 0,
                        "В ответе отсутвует id"),
                () -> assertEquals(testMovie.name(), recievedMovie.name(),
                        "Название фильма в ответе не совпадает с отправленным"),
                () -> assertEquals(testMovie.year(), recievedMovie.year(),
                        "Год фильма в ответе не совпадает с отправленным"),
                () -> assertEquals(recievedMovie, store.getById(testMovie.id()),
                        "Полученные в ответе фильм не совпадает с сохраненным")
        );
    }

    @Test
    void getMovie_ById_NotFound() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse error = formatter.fromJsonError(resp.body());

        assertAll(
                () -> assertEquals(404, resp.statusCode(), "POST /movies должен вернуть 404"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(resp.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Not Found", error.error()),
                () -> assertTrue(error.details().contains("Ресурс не найден"))
        );
    }

    @Test
    void getMovie_ById_NotNum() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/qwerty"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse error = formatter.fromJsonError(resp.body());

        assertAll(
                () -> assertEquals(400, resp.statusCode(), "GET /movies/abc должен вернуть 400"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(resp.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Bad Request", error.error()),
                () -> assertTrue(error.details().contains("Некорректный ID"))
        );
    }

    @Test
    void postMovie_whenValid_createsMovie() throws Exception {
        CreateMovieRequest request = createTestMovieRequest("Titanic", (short) 1997);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie createdMovie = formatter.fromJson(resp.body());

        assertAll(
                () -> assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
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
    void postMovie_Validation_returnsError() throws Exception {
        CreateMovieRequest request = createTestMovieRequest(" ", (short) 2099);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonObject error = JsonParser.parseString(resp.body())
                .getAsJsonObject();
        JsonArray details = error.getAsJsonArray("details");

        assertAll(
                () -> assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(resp.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Ошибка валидации", error.get("error").getAsString()),
                () -> assertEquals(2, details.size(), "Некорректное кол-во сообщений об ошибках в детализации")
        );
    }

    @Test
    void postMovie_Headers_returnsError() throws Exception {
        CreateMovieRequest request = createTestMovieRequest("Titanic", (short) 1997);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/html")
                .POST(HttpRequest.BodyPublishers.ofString(formatter.toJson(request)))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonObject error = JsonParser.parseString(resp.body())
                .getAsJsonObject();
        JsonArray details = error.getAsJsonArray("details");

        assertAll(
                () -> assertEquals(415, resp.statusCode(), "POST /movies должен вернуть 415"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(resp.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Неподдерживаемый формат данных", error.get("error").getAsString()),
                () -> assertEquals(1, details.size(), "Некорректное кол-во сообщений об ошибках в детализации")
        );
    }

    @Test
    void deleteMovie_ById_removesMovie() throws Exception {
        // Удалить фильм по созданному ранее id
    }

    @Test
    void deleteMovie_ById_NotFound() throws Exception {
        // Удалить фильм по созданному ранее id
    }

    @Test
    void getMovie_ByYear_NotNum() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/qwerty"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse error = formatter.fromJsonError(resp.body());

        assertAll(
                () -> assertEquals(400, resp.statusCode(), "GET /movies/abc должен вернуть 400"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(resp.body().isEmpty(), "В ответе нет описания ошибок"),
                () -> assertEquals("Bad Request", error.error()),
                () -> assertTrue(error.details().contains("Некорректный ID"))
        );
    }

    @Test
    void getMovie_ByYear_Validation() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/qwerty"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse error = formatter.fromJsonError(resp.body());

        assertAll(
                () -> assertEquals(400, resp.statusCode(), "GET /movies/abc должен вернуть 400"),
                () -> assertEquals(
                        "application/json; charset=UTF-8",
                        resp.headers().firstValue("Content-Type").orElse(""),
                        "Content-Type должен содержать формат данных и кодировку"
                ),
                () -> assertFalse(resp.body().isEmpty(), "В ответе нет описания ошибок"),
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
