package com.poli.chat;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends JFrame {
    private static final Map<String, ClientHandler> CLIENTS = new ConcurrentHashMap<>();

    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Servidor detenido");
    private final JTextField portField = new JTextField("5000", 7);
    private volatile boolean running;
    private ServerSocket serverSocket;

    public ChatServer() {
        super("Servidor de Chat - Semana 3");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(650, 430);
        setLocationByPlatform(true);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JButton startButton = new JButton("Iniciar servidor");
        JButton stopButton = new JButton("Detener servidor");
        stopButton.setEnabled(false);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Puerto:"));
        top.add(portField);
        top.add(startButton);
        top.add(stopButton);
        top.add(statusLabel);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(new JLabel("  Registro de conexiones y actividad del servidor", SwingConstants.LEFT), BorderLayout.SOUTH);

        startButton.addActionListener(e -> startServer(startButton, stopButton));
        stopButton.addActionListener(e -> stopServer(startButton, stopButton));
        log("Aplicación de servidor lista. Pulsa 'Iniciar servidor'.");
    }

    private void startServer(JButton startButton, JButton stopButton) {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Ingresa un puerto válido entre 1 y 65535.",
                    "Puerto inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            portField.setEnabled(false);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            statusLabel.setText("Servidor activo en puerto " + port);
            log("Servidor iniciado correctamente en el puerto " + port + ".");
            log("Esperando conexiones de clientes...");
            Thread acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        log("Nueva conexión desde " + socket.getInetAddress().getHostAddress());
                        new Thread(new ClientHandler(socket, this)).start();
                    } catch (IOException ex) {
                        if (running) log("Error aceptando conexión: " + ex.getMessage());
                    }
                }
            }, "server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo iniciar el servidor: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer(JButton startButton, JButton stopButton) {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        for (ClientHandler handler : CLIENTS.values()) handler.closeConnection();
        CLIENTS.clear();
        portField.setEnabled(true);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusLabel.setText("Servidor detenido");
        log("Servidor detenido.");
    }

    void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    static boolean register(String username, ClientHandler handler) {
        return CLIENTS.putIfAbsent(username.toLowerCase(), handler) == null;
    }

    static void unregister(ClientHandler handler) {
        if (handler.getUsername() != null) {
            CLIENTS.remove(handler.getUsername().toLowerCase(), handler);
            handler.getServer().log("Usuario desconectado: " + handler.getUsername());
            broadcastUsers();
        }
    }

    static String connectedUsers() {
        return String.join(",", CLIENTS.values().stream()
                .map(ClientHandler::getUsername).sorted(String.CASE_INSENSITIVE_ORDER)
                .toArray(String[]::new));
    }

    static void broadcastUsers() {
        String users = connectedUsers();
        for (ClientHandler client : CLIENTS.values()) client.sendUsers(users);
    }

    static ClientHandler findClient(String username) {
        return username == null ? null : CLIENTS.get(username.toLowerCase());
    }

    static void notifyChatRequest(ClientHandler sender, String targetName) {
        ClientHandler target = findClient(targetName);
        if (target == null) {
            sender.sendSystem("El usuario seleccionado no está conectado.");
            sender.sendUsers(connectedUsers());
            return;
        }
        if (target == sender) {
            sender.sendSystem("No puedes seleccionarte a ti mismo.");
            return;
        }
        sender.sendSystem("Usuario seleccionado: " + target.getUsername()
                + ". Ya puedes escribirle; puedes cambiar de destinatario cuando quieras.");
        target.sendSystem(sender.getUsername() + " está disponible para conversar contigo.");
        sender.getServer().log(sender.getUsername() + " seleccionó a " + target.getUsername());
    }

    static void routeMessage(ClientHandler sender, String targetName, String message) {
        ClientHandler receiver = findClient(targetName);
        if (receiver == null) {
            sender.sendSystem("No se pudo enviar: el usuario " + targetName + " no está conectado.");
            sender.sendUsers(connectedUsers());
            return;
        }
        if (receiver == sender) {
            sender.sendSystem("No puedes enviarte mensajes a ti mismo.");
            return;
        }
        receiver.sendMessage(sender.getUsername(), message);
        sender.sendDelivery(targetName, message);
        sender.getServer().log(sender.getUsername() + " -> " + receiver.getUsername() + ": " + message);
    }

    static void notifyGoodbye(ClientHandler sender, String targetName) {
        ClientHandler receiver = findClient(targetName);
        if (receiver == null) {
            sender.sendSystem("El usuario seleccionado ya no está conectado.");
            return;
        }
        receiver.sendSystem(sender.getUsername() + " ha finalizado el intercambio contigo.");
        sender.sendSystem("Notificación enviada a " + receiver.getUsername() + ".");
        sender.getServer().log(sender.getUsername() + " envió chao a " + receiver.getUsername());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatServer().setVisible(true));
    }
}
