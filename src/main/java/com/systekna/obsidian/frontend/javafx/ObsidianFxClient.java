package com.systekna.obsidian.frontend.javafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class ObsidianFxClient extends Application {

    private VBox chatBox;
    private TextField inputField;
    private Button sendButton;
    private HttpClient httpClient;
    private ScrollPane scrollPane;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        httpClient = HttpClient.newHttpClient();

        primaryStage.setTitle("Obsidian Claude - Desktop Client");
        
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0F172A; -fx-font-family: 'Inter', sans-serif;");

        // Top Header
        Label titleLabel = new Label("Obsidian Bridge UI");
        titleLabel.setStyle("-fx-text-fill: #E2E8F0; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 15px;");
        BorderPane.setAlignment(titleLabel, Pos.CENTER);
        root.setTop(titleLabel);

        // Chat Box
        chatBox = new VBox(15);
        chatBox.setPadding(new Insets(20));
        chatBox.setStyle("-fx-background-color: transparent;");

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #0F172A; -fx-border-color: transparent;");
        // Remove borda de foco do scrollpane padrão
        scrollPane.setFocusTraversable(false);
        root.setCenter(scrollPane);

        // Input Area
        HBox inputArea = new HBox(10);
        inputArea.setPadding(new Insets(20));
        inputArea.setStyle("-fx-background-color: #1E293B;");
        
        inputField = new TextField();
        inputField.setPromptText("Digite sua mensagem para o Vault...");
        inputField.setStyle("-fx-background-color: #0F172A; -fx-text-fill: #E2E8F0; -fx-border-color: #334155; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 12px;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendButton = new Button("Enviar");
        sendButton.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 12px 24px; -fx-cursor: hand;");
        
        inputArea.getChildren().addAll(inputField, sendButton);
        root.setBottom(inputArea);

        // Events
        sendButton.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());

        Scene scene = new Scene(root, 700, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        appendMessage("Assistente", "Olá! Estou conectado ao Backend via JavaFX. O que deseja saber sobre suas notas?", false, true);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        inputField.clear();
        appendMessage("Você", text, true, false);
        
        sendButton.setDisable(true);
        inputField.setDisable(true);

        Label responseLabel = appendStreamingMessage("Assistente");

        // Assíncrono via POST recebendo Server-Sent Events nativo do Java
        new Thread(() -> {
            try {
                String reqBody = String.format("{\"message\": \"%s\", \"history\": []}", text.replace("\"", "\\\"").replace("\n", " "));
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/v1/chat/stream"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                        .thenAccept(response -> {
                            int statusCode = response.statusCode();
                            if (statusCode != 200) {
                                Platform.runLater(() -> responseLabel.setText("Erro HTTP " + statusCode));
                                return;
                            }

                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                                String line;
                                StringBuilder fullText = new StringBuilder();
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ")) {
                                        String chunk = line.substring(6);
                                        fullText.append(chunk);
                                        String currentText = fullText.toString();
                                        Platform.runLater(() -> {
                                            responseLabel.setText(currentText);
                                            scrollPane.setVvalue(1.0);
                                        });
                                    }
                                }
                            } catch (Exception ex) {
                                Platform.runLater(() -> responseLabel.setText(responseLabel.getText() + "\n(Stream encerrado)"));
                            }
                        }).join();
            } catch (Exception e) {
                Platform.runLater(() -> responseLabel.setText("Falha na conexão com http://localhost:8080"));
            } finally {
                Platform.runLater(() -> {
                    sendButton.setDisable(false);
                    inputField.setDisable(false);
                    inputField.requestFocus();
                });
            }
        }).start();
    }

    private void appendMessage(String sender, String text, boolean isUser, boolean isSystem) {
        VBox bubble = new VBox(5);
        bubble.setMaxWidth(550);
        
        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");

        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        textLabel.setStyle(isUser 
            ? "-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-padding: 14px; -fx-background-radius: 16px 16px 4px 16px; -fx-font-size: 14px;"
            : "-fx-background-color: #1E293B; -fx-text-fill: #E2E8F0; -fx-padding: 14px; -fx-background-radius: 16px 16px 16px 4px; -fx-border-color: #334155; -fx-border-radius: 16px 16px 16px 4px; -fx-font-size: 14px;");

        if (isSystem) {
            textLabel.setStyle("-fx-background-color: linear-gradient(to right, #8B5CF6, #3B82F6); -fx-text-fill: white; -fx-padding: 14px; -fx-background-radius: 16px 16px 16px 4px; -fx-font-size: 14px;");
        }

        bubble.getChildren().addAll(senderLabel, textLabel);
        
        HBox row = new HBox(bubble);
        row.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        
        chatBox.getChildren().add(row);
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private Label appendStreamingMessage(String sender) {
        VBox bubble = new VBox(5);
        bubble.setMaxWidth(550);
        
        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");

        Label textLabel = new Label("⏳ Processando via SSE...");
        textLabel.setWrapText(true);
        textLabel.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #E2E8F0; -fx-padding: 14px; -fx-background-radius: 16px 16px 16px 4px; -fx-border-color: #334155; -fx-border-radius: 16px 16px 16px 4px; -fx-font-size: 14px;");

        bubble.getChildren().addAll(senderLabel, textLabel);
        
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        
        chatBox.getChildren().add(row);
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
        return textLabel;
    }
}
