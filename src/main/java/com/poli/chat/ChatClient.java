package com.poli.chat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClient extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("5000", 5);
    private final JTextField usernameField = new JTextField(10);
    private final JButton connectButton = new JButton("Conectar");
    private final JComboBox<String> usersCombo = new JComboBox<>();
    private final JButton chatButton = new JButton("Seleccionar usuario");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton("Enviar al seleccionado");
    private final JButton byeButton = new JButton("Enviar chao");
    private final JLabel statusLabel = new JLabel("Desconectado");

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private volatile boolean connected;
    private String selectedRecipient;

    public ChatClient() {
        super("Cliente de Chat - Semana 3");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(740, 530);
        setLocationByPlatform(true);
        buildInterface();
        setConnectedState(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                disconnect();
                dispose();
            }
        });
    }

    private void buildInterface() {
        JPanel connection = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connection.setBorder(BorderFactory.createTitledBorder("Datos de conexión"));
        connection.add(new JLabel("IP servidor:"));
        connection.add(hostField);
        connection.add(new JLabel("Puerto:"));
        connection.add(portField);
        connection.add(new JLabel("Usuario:"));
        connection.add(usernameField);
        connection.add(connectButton);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel usersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        usersPanel.setBorder(BorderFactory.createTitledBorder("Destinatario del mensaje"));
        usersPanel.add(new JLabel("Enviar a:"));
        usersPanel.add(usersCombo);
        usersPanel.add(chatButton);
        usersPanel.add(byeButton);

        JPanel messagePanel = new JPanel(new BorderLayout(8, 0));
        messagePanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(usersPanel, BorderLayout.NORTH);
        bottom.add(messagePanel, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        add(connection, BorderLayout.NORTH);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connect());
        chatButton.addActionListener(e -> selectUser());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        byeButton.addActionListener(e -> sendBye());
        usersCombo.addActionListener(e -> {
            if (connected) selectedRecipient = (String) usersCombo.getSelectedItem();
        });
    }

    private void connect() {
        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535 || host.isEmpty() || username.isEmpty()
                    || username.contains(" ") || username.length() > 20) throw new IllegalArgumentException();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Verifica IP, puerto y usuario. El usuario debe tener máximo 20 caracteres y no contener espacios.",
                    "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            socket = new Socket(host, port);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            output.println(username);
            connected = true;
            setConnectedState(true);
            append("Conectado al servidor " + host + ":" + port + " como " + username + ".");
            Thread reader = new Thread(this::listen, "client-reader-" + username);
            reader.setDaemon(true);
            reader.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No fue posible conectar con el servidor.\nVerifica que esté iniciado y que la IP y el puerto sean correctos.",
                    "Error de conexión", JOptionPane.ERROR_MESSAGE);
            disconnect();
        }
    }

    private void listen() {
        try {
            String response;
            while (connected && (response = input.readLine()) != null) {
                final String line = response;
                SwingUtilities.invokeLater(() -> processServerMessage(line));
            }
        } catch (IOException ex) {
            if (connected) SwingUtilities.invokeLater(() -> append("Se perdió la conexión con el servidor."));
        } finally {
            connected = false;
            SwingUtilities.invokeLater(() -> setConnectedState(false));
        }
    }

    private void processServerMessage(String response) {
        if (response.startsWith("SYSTEM|")) {
            append("[Servidor] " + response.substring(7));
        } else if (response.startsWith("USERS|")) {
            String current = selectedRecipient != null ? selectedRecipient : (String) usersCombo.getSelectedItem();
            usersCombo.removeAllItems();
            String users = response.substring(6);
            if (!users.isEmpty()) {
                for (String user : users.split(",")) {
                    if (!user.trim().isEmpty() && !user.equalsIgnoreCase(usernameField.getText().trim())) {
                        usersCombo.addItem(user.trim());
                    }
                }
            }
            if (current != null) usersCombo.setSelectedItem(current);
            selectedRecipient = (String) usersCombo.getSelectedItem();
        } else if (response.startsWith("MSG|")) {
            String[] parts = response.split("\\|", 3);
            if (parts.length == 3) append(parts[1] + ": " + parts[2]);
        } else if (response.startsWith("SENT|")) {
            String[] parts = response.split("\\|", 3);
            if (parts.length == 3) append("Yo -> " + parts[1] + ": " + parts[2]);
        }
    }

    private void selectUser() {
        if (!connected) return;
        selectedRecipient = (String) usersCombo.getSelectedItem();
        if (selectedRecipient == null) {
            JOptionPane.showMessageDialog(this, "No hay otro usuario conectado para seleccionar.",
                    "Sin usuarios", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        output.println("/chat " + selectedRecipient);
        statusLabel.setText("Destinatario seleccionado: " + selectedRecipient);
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!connected || message.isEmpty()) return;

        selectedRecipient = (String) usersCombo.getSelectedItem();
        if (selectedRecipient == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un destinatario en la lista antes de enviar.",
                    "Destinatario requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        output.println("/msg " + selectedRecipient + "|" + message);
        messageField.setText("");
    }

    private void sendBye() {
        if (!connected) return;
        selectedRecipient = (String) usersCombo.getSelectedItem();
        if (selectedRecipient == null) {
            JOptionPane.showMessageDialog(this, "Selecciona a quién deseas enviar el aviso.",
                    "Destinatario requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        output.println("/bye " + selectedRecipient);
    }

    private void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        setConnectedState(false);
    }

    private void setConnectedState(boolean value) {
        hostField.setEnabled(!value);
        portField.setEnabled(!value);
        usernameField.setEnabled(!value);
        connectButton.setEnabled(!value);
        usersCombo.setEnabled(value);
        chatButton.setEnabled(value);
        messageField.setEnabled(value);
        sendButton.setEnabled(value);
        byeButton.setEnabled(value);
        statusLabel.setText(value ? "Conectado al servidor" : "Desconectado");
    }

    private void append(String text) {
        chatArea.append(text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClient().setVisible(true));
    }
}
