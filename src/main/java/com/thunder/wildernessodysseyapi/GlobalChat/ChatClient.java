package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    private String serverIp;
    private int serverPort;
    private Socket socket;
    private PrintWriter out;

    /**
     * Constructor for the ChatClient.
     *
     * @param serverIp   IP address of the chat server
     * @param serverPort Port of the chat server
     * @throws IOException if the connection to the server fails
     */
    public ChatClient(String serverIp, int serverPort) throws IOException {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    /**
     * Connect to the chat server.
     *
     * @throws IOException if the connection to the server fails
     */
    public void connect() throws IOException {
        socket = new Socket(serverIp, serverPort);
        out = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("Connected to chat server at " + serverIp + ":" + serverPort);
    }

    /**
     * Send a message to the chat server.
     *
     * @param message The message to send
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
            System.out.println("Message sent: " + message);
        } else {
            System.err.println("Cannot send message. Not connected to the chat server.");
        }
    }

    /**
     * Close the connection to the chat server.
     */
    public void close() {
        try {
            if (out != null) {
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
            System.out.println("Disconnected from chat server.");
        } catch (IOException e) {
            System.err.println("Error while closing connection: " + e.getMessage());
        }
    }
}
