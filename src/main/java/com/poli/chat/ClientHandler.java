package com.poli.chat;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private BufferedReader input;
    private PrintWriter output;
    private String username;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            sendSystem("Conexión establecida. Envía tu nombre de usuario.");
            username = input.readLine();

            if (username == null || username.trim().isEmpty() || username.length() > 20 || username.contains(" ")) {
                sendSystem("Nombre inválido. Usa máximo 20 caracteres y no incluyas espacios.");
                return;
            }
            username = username.trim();
            if (!ChatServer.register(username, this)) {
                sendSystem("Ese nombre ya está conectado. Cierra esta ventana e intenta con otro.");
                return;
            }

            server.log("Usuario conectado: " + username + " (" + socket.getInetAddress().getHostAddress() + ")");
            sendSystem("Bienvenido, " + username + ". Ya estás conectado al servidor.");
            ChatServer.broadcastUsers();

            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("/chat ")) {
                    ChatServer.notifyChatRequest(this, line.substring(6).trim());
                } else if (line.startsWith("/msg ")) {
                    // Formato: /msg destinatario|contenido del mensaje
                    String payload = line.substring(5);
                    int separator = payload.indexOf('|');
                    if (separator <= 0 || separator == payload.length() - 1) {
                        sendSystem("Mensaje inválido. Selecciona un usuario y escribe un mensaje.");
                    } else {
                        String recipient = payload.substring(0, separator).trim();
                        String message = payload.substring(separator + 1);
                        ChatServer.routeMessage(this, recipient, message);
                    }
                } else if (line.startsWith("/bye ")) {
                    ChatServer.notifyGoodbye(this, line.substring(5).trim());
                } else if (line.equalsIgnoreCase("/usuarios")) {
                    sendUsers(ChatServer.connectedUsers());
                } else if (line.equalsIgnoreCase("chao")) {
                    sendSystem("Selecciona un destinatario para enviarle la notificación de cierre.");
                } else if (line.startsWith("/")) {
                    sendSystem("Comando no reconocido.");
                } else if (!line.trim().isEmpty()) {
                    sendSystem("Selecciona un destinatario en la lista antes de enviar el mensaje.");
                }
            }
        } catch (IOException ex) {
            server.log("Conexión interrumpida" + (username == null ? "" : " con " + username) + ".");
        } finally {
            if (username != null) ChatServer.unregister(this);
            closeConnection();
        }
    }

    void sendSystem(String message) {
        if (output != null) output.println("SYSTEM|" + message);
    }
    void sendUsers(String users) {
        if (output != null) output.println("USERS|" + users);
    }
    void sendMessage(String sender, String message) {
        if (output != null) output.println("MSG|" + sender + "|" + message);
    }
    void sendDelivery(String recipient, String message) {
        if (output != null) output.println("SENT|" + recipient + "|" + message);
    }
    String getUsername() { return username; }
    ChatServer getServer() { return server; }

    void closeConnection() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
