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

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private String base;
    private MoviesServer server;
    private HttpClient client;
    private MoviesStore store;
    private JsonFormatter formatter;

    @BeforeEach
    void beforeEach() {
        store = new MoviesStore();
        formatter = new JsonFormatter();
        server = new MoviesServer(0, store, formatter);

        server.start();
        base = "http://localhost:" + server.getPort();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies"))
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
        assertEquals(List.of(), formatter.fromJsonList(body));
    }

    @Test
    void getMovie_whenExist_returnsMovies() throws Exception {
        Movie testMovie = createTestMovie("Titanic", (short) 1997);
        Movie testMovie2 = createTestMovie("The Shawshank Redemption", (short) 1994);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies"))
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
        assertTrue(movies.containsAll(List.of(testMovie, testMovie2)));
    }

    @Test
    void getMovie_byId_returnsMovie() throws Exception {
        Movie testMovie = createTestMovie("Nosferatu", (short) 1922);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies/" + testMovie.id()))
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
                () -> assertEquals(testMovie.title(), receivedMovie.title(),
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
                .uri(URI.create(base + "/movies/9999"))
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
                .uri(URI.create(base + "/movies/qwerty"))
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
        createTestMovie("The Fast and the Furious", (short) 2001);
        Movie parasites = createTestMovie("Parasite", (short) 2019);
        Movie avengers = createTestMovie("Avengers: Endgame", (short) 2019);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies" + "?year=" + parasites.year()))
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
                () -> assertEquals(2, movies.size(), "Размер массива объектов не совпадает с целевым"),
                () -> assertTrue(movies.containsAll(List.of(parasites, avengers)))
        );
    }

    @Test
    void getMovie_byYear_notFound_returnsEmptyArray() throws Exception {
        createTestMovie("The Fast and the Furious", (short) 2001);
        createTestMovie("Parasite", (short) 2019);
        createTestMovie("Avengers: Endgame", (short) 2019);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies" + "?year=" + 1899))
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
        assertEquals(List.of(), formatter.fromJsonList(body));
    }

    @Test
    void getMovie_byYear_whenNotNum_return400() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies" + "?year=" + "abc"))
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
                .uri(URI.create(base + "/movies"))
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
                () -> assertEquals(request.title(), createdMovie.title(),
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
                .uri(URI.create(base + "/movies"))
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
                .uri(URI.create(base + "/movies"))
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
                .uri(URI.create(base + "/movies"))
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
                .uri(URI.create(base + "/movies"))
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
                .uri(URI.create(base + "/movies"))
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
                .uri(URI.create(base + "/movies/" + testMovie.id()))
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
                "Ответ 204 должен быть пустым");
        assertNull(store.getById(testMovie.id()));
    }

    @Test
    void deleteMovie_byId_notFound_return404() throws Exception {
        createTestMovie("Nosferatu", (short) 1922);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies/" + 999))
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
        createTestMovie("Nosferatu", (short) 1922);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/movies/" + "abc"))
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

    @Test
    void postMovie_usesTitleInRequestAndResponse() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "{\"title\":\"Титаник\",\"year\":1997}");
        assertEquals(201, response.statusCode());
        JsonObject movie = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("Титаник", movie.get("title").getAsString());
        assertFalse(movie.has("name"));
    }

    @Test
    void unsupportedMethods_return405WithAllowHeader() throws Exception {
        for (String method : List.of("PUT", "PATCH", "DELETE")) {
            HttpResponse<String> response = send(method, "/movies", "");
            assertEquals(405, response.statusCode());
            assertEquals("GET, POST", response.headers().firstValue("Allow").orElse(""));
            assertTrue(JsonParser.parseString(response.body()).getAsJsonObject().has("error"));
        }
        assertEquals(405, send("POST", "/movies/1", "{}").statusCode());
    }

    @Test
    void unknownPaths_return404AndDoNotCreateMovies() throws Exception {
        for (String path : List.of("/moviesXYZ", "/movies/", "/movies/1/extra", "/unknown")) {
            assertEquals(404, send("POST", path, "{\"title\":\"Test\",\"year\":1997}").statusCode());
            assertEquals(404, send("GET", path + "?year=1997", "").statusCode());
        }
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void invalidQuery_returns400() throws Exception {
        for (String query : List.of("foo=1", "year=", "year=abc", "year=1997&year=1998",
                "year=1997&foo=1", "year=1887", "year=" + (java.time.Year.now().getValue() + 2))) {
            assertEquals(400, send("GET", "/movies?" + query, "").statusCode(), query);
        }
        Movie movie = store.add("Test", 1997);
        assertEquals(400, send("GET", "/movies/" + movie.id() + "?year=1997", "").statusCode());
        assertEquals(200, send("GET", "/movies?%79ear=1997", "").statusCode());
    }

    @Test
    void invalidJsonAndFieldTypes_return400() throws Exception {
        for (String body : List.of("", "null", "[]", "{title:'Test',year:1997}",
                "{\"title\":42,\"year\":1997}", "{\"title\":true,\"year\":1997}",
                "{\"title\":\"Test\",\"year\":\"1997\"}", "{\"title\":\"Test\",\"year\":1997.5}",
                "{\"title\":\"Test\",\"year\":1997} {}", "{\"title\":\"A\",\"title\":\"B\",\"year\":1997}",
                "{\"title\":\"Test\",\"year\":1997,}", "{/*comment*/\"title\":\"Test\",\"year\":1997}")) {
            assertEquals(400, send("POST", "/movies", body).statusCode(), body);
        }
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void missingFieldsAndNullFields_return422() throws Exception {
        for (String body : List.of("{}", "{\"title\":\"Test\"}", "{\"year\":1997}",
                "{\"title\":null,\"year\":null}", "{\"name\":\"Test\",\"year\":1997}")) {
            assertEquals(422, send("POST", "/movies", body).statusCode(), body);
        }
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void titleAndYearBoundaries_areValidated() throws Exception {
        int maximumYear = java.time.Year.now().getValue() + 1;
        assertEquals(201, send("POST", "/movies",
                formatter.toJson(new CreateMovieRequest("A".repeat(100), 1888))).statusCode());
        assertEquals(201, send("POST", "/movies",
                formatter.toJson(new CreateMovieRequest("Test", maximumYear))).statusCode());
        assertEquals(422, send("POST", "/movies",
                formatter.toJson(new CreateMovieRequest("A".repeat(101), 1888))).statusCode());
        for (int year : List.of(1887, maximumYear + 1, 67533)) {
            HttpResponse<String> response = send("POST", "/movies",
                    formatter.toJson(new CreateMovieRequest("Test", year)));
            assertEquals(422, response.statusCode());
            assertTrue(formatter.fromJsonError(response.body()).details()
                    .contains("год должен быть между 1888 и " + maximumYear));
        }
    }

    @Test
    void deleteMovie_isVisibleThroughApi() throws Exception {
        Movie movie = store.add("Test", 1997);
        assertEquals(200, send("GET", "/movies/" + movie.id(), "").statusCode());
        assertEquals(204, send("DELETE", "/movies/" + movie.id(), "").statusCode());
        assertEquals(404, send("GET", "/movies/" + movie.id(), "").statusCode());
        assertEquals(404, send("DELETE", "/movies/" + movie.id(), "").statusCode());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=UTF-8")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Movie createTestMovie(String title, int year) {
        return store.add(title, year);
    }

    private CreateMovieRequest createTestMovieRequest(String title, int year) {
        return new CreateMovieRequest(title, year);
    }

}
