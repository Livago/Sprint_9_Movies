package ru.practicum.moviehub;

import ru.practicum.moviehub.controller.MoviesServer;
import ru.practicum.moviehub.formatter.JsonFormatter;
import ru.practicum.moviehub.storage.MoviesStore;

public class MovieHubApp {
    public static void main(String[] args) {
        final MoviesServer server = new MoviesServer(8080, new MoviesStore(), new JsonFormatter());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
