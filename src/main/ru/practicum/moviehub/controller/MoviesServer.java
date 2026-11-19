package ru.practicum.moviehub.controller;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.formatter.JsonFormatter;
import ru.practicum.moviehub.storage.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {

    private final HttpServer server;

    public MoviesServer(int port, MoviesStore store, JsonFormatter formatter) {
        try {
            this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);

            server.createContext("/", new MoviesHandler(store, formatter));

        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }
}
