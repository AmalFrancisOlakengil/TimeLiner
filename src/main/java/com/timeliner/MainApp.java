package com.timeliner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import atlantafx.base.theme.PrimerDark;

import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class MainApp extends Application {

    private static final String DB_URL = "jdbc:sqlite:timeliner.db";

    @Override
    public void start(Stage primaryStage) {
        // 1. Apply AtlantaFX Sleek Theme
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        // 2. Prevent application from exiting when window is hidden
        Platform.setImplicitExit(false);

        // 3. Initialize Database
        initDatabase();

        // 4. Build a basic mock layout for your horizontal timeline
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPannable(true); // Allows click-and-drag scrolling
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        HBox timelineCanvas = new HBox(20); // 20px spacing between cards
        timelineCanvas.setStyle("-fx-padding: 40;");

        // Add dummy bands to test horizontal layout
        for (int i = 1; i <= 10; i++) {
            VBox eventCard = new VBox(10);
            eventCard.setStyle("-fx-background-color: #21262d; -fx-padding: 20; -fx-background-radius: 8; -fx-min-width: 200;");
            eventCard.getChildren().addAll(
                new Label("Event #" + i),
                new Label("Date: 2026-06-18"),
                new Label("Location: Local Canvas")
            );
            timelineCanvas.getChildren().add(eventCard);
        }
        scrollPane.setContent(timelineCanvas);

        Scene scene = new Scene(scrollPane, 900, 400);
        
        // Load custom CSS safely
        var cssResource = getClass().getResource("/com/timeliner/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        primaryStage.setTitle("TimeLiner Management");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 5. Intercept Close Request
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            primaryStage.hide();
            System.out.println("TimeLiner is minimized to the system tray overflow panel.");
        });

        // 6. Spawn background System Tray Icon
        javax.swing.SwingUtilities.invokeLater(() -> createTrayIcon(primaryStage));
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS timeline_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT NOT NULL," +
                    "event_date TEXT," +
                    "event_time TEXT," +
                    "location TEXT," +
                    "reminder INTEGER DEFAULT 0)");
            System.out.println("SQLite database initialized successfully inside project root!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createTrayIcon(Stage primaryStage) {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = Toolkit.getDefaultToolkit().createImage(new byte[0]); // fallback empty pixel image

            PopupMenu popup = new PopupMenu();
            MenuItem openItem = new MenuItem("Open TimeLiner");
            openItem.addActionListener(e -> Platform.runLater(primaryStage::show));
            
            MenuItem exitItem = new MenuItem("Exit Completely");
            exitItem.addActionListener(e -> {
                Platform.exit();
                System.exit(0);
            });

            popup.add(openItem);
            popup.addSeparator();
            popup.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(image, "TimeLiner", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(primaryStage::show));

            tray.add(trayIcon);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}