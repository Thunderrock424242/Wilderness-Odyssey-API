package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private int port;
    private ServerSocket serverSocket;
    private boolean running;
    private ExecutorService clientHandlerPool;

    /**
     * Constructor for the ChatServer.
     *
     * @param port The port to run the server on
     */
    public ChatServer(int port) {
        this.port = port;
        this.running = false;
    }

    /**
     * Starts the chat server.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            clientHandlerPool = Executors.newCachedThreadPool();
            running = true;
            System.out.println("Chat server started on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                clientHandlerPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Error starting chat server: " + e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Stops the chat server.
     */
    public void stop() {
        running = false;
        if (clientHandlerPool != null) {
            clientHandlerPool.shutdown();
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
                System.out.println("Chat server stopped.");
            } catch (IOException e) {
                System.err.println("Error stopping server: " + e.getMessage());
            }
        }
    }

    /**
     * Inner class to handle client communication.
     */
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                // Placeholder for handling client communication
                System.out.println("Handling client: " + clientSocket.getRemoteSocketAddress());
                // Example: echo server logic or broadcast to all clients
            } catch (Exception e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }
    }
}
