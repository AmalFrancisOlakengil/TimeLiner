package com.timeliner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import atlantafx.base.theme.PrimerDark;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Image;
import java.awt.Toolkit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {

    private static final String DB_URL = "jdbc:sqlite:timeliner.db";
    
    private BorderPane mainRoot;
    private VBox timelineContainer;
    private ScrollPane scrollPane;

    @Override
    public void start(Stage primaryStage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        Platform.setImplicitExit(false);
        initDatabase();

        mainRoot = new BorderPane();
        mainRoot.setTop(buildNavigationBar());

        scrollPane = new ScrollPane();
        scrollPane.setPannable(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #0d1117;");

        showTimelinePage();

        Scene scene = new Scene(mainRoot, 1000, 650);
        
        var cssResource = getClass().getResource("/com/timeliner/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        primaryStage.setTitle("TimeLiner Management");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            primaryStage.hide();
        });

        javax.swing.SwingUtilities.invokeLater(() -> createTrayIcon(primaryStage));
    }

    private HBox buildNavigationBar() {
        HBox navBar = new HBox(15);
        navBar.setStyle("-fx-background-color: #161b22; -fx-padding: 15; -fx-border-color: #30363d; -fx-border-width: 0 0 1 0;");
        navBar.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("⏱ TimeLiner");
        logo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #f0f6fc; -fx-padding: 0 20 0 0;");

        Button btnTimeline = new Button("View Timeline");
        Button btnAddEvent = new Button("Add Event");
        
        btnTimeline.getStyleClass().add("accent");
        btnAddEvent.getStyleClass().add("success");

        btnTimeline.setOnAction(e -> showTimelinePage());
        btnAddEvent.setOnAction(e -> showAddEventPage());

        navBar.getChildren().addAll(logo, btnTimeline, btnAddEvent);
        return navBar;
    }

    // --- PAGE 1: Vertical Timeline with Edit & Delete Bindings ---
    private void showTimelinePage() {
        StackPane canvasStack = new StackPane();
        canvasStack.setStyle("-fx-background-color: #0d1117; -fx-padding: 50 20 50 20;");

        timelineContainer = new VBox(0);
        timelineContainer.setAlignment(Pos.TOP_CENTER);

        List<TimelineEvent> events = loadEventsFromDb();

        if (events.isEmpty()) {
            Label noEventsLayers = new Label("No timeline data found. Go to 'Add Event' to plot milestones.");
            noEventsLayers.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 15px;");
            canvasStack.getChildren().add(noEventsLayers);
            mainRoot.setCenter(canvasStack);
            return;
        }

        double runningLineHeight = events.size() * 160.0 + 50;
        Line axisSpine = new Line(0, 0, 0, runningLineHeight);
        axisSpine.setStroke(javafx.scene.paint.Color.web("#30363d"));
        axisSpine.setStrokeWidth(4);
        
        StackPane spineWrapper = new StackPane(axisSpine);
        spineWrapper.setAlignment(Pos.TOP_CENTER);
        canvasStack.getChildren().add(spineWrapper);

        for (int i = 0; i < events.size(); i++) {
            TimelineEvent event = events.get(i);
            boolean isLeftBranch = (i % 2 == 0);

            GridPane rowGrid = new GridPane();
            rowGrid.setAlignment(Pos.CENTER);
            rowGrid.setPrefHeight(160);

            ColumnConstraints leftCol = new ColumnConstraints(350);
            leftCol.setHalignment(javafx.geometry.HPos.RIGHT);
            
            ColumnConstraints centerCol = new ColumnConstraints(60);
            centerCol.setHalignment(javafx.geometry.HPos.CENTER);
            
            ColumnConstraints rightCol = new ColumnConstraints(350);
            rightCol.setHalignment(javafx.geometry.HPos.LEFT);
            
            rowGrid.getColumnConstraints().addAll(leftCol, centerCol, rightCol);

            // Construct Card Title
            Label title = new Label(event.title);
            title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #58a6ff;");
            title.setWrapText(true);
            HBox.setHgrow(title, Priority.ALWAYS);

            // Pencil Icon Edit Button
            Button btnEdit = new Button();
            btnEdit.setGraphic(new FontIcon(Material2AL.EDIT));
            btnEdit.getStyleClass().addAll("button-icon", "flat");
            btnEdit.setTooltip(new Tooltip("Edit Event Details"));
            btnEdit.setOnAction(e -> showEditPopup(event));

            // Trash Can Icon Delete Button
            Button btnDelete = new Button();
            FontIcon deleteIcon = new FontIcon(Material2MZ.DELETE_OUTLINE);
            deleteIcon.setIconColor(javafx.scene.paint.Color.web("#f85149")); // Nice clean red color accent
            btnDelete.setGraphic(deleteIcon);
            btnDelete.getStyleClass().addAll("button-icon", "flat");
            btnDelete.setTooltip(new Tooltip("Delete Event"));
            btnDelete.setOnAction(e -> confirmAndExecuteDelete(event));

            // Action Cluster Holder Group
            HBox actionCluster = new HBox(2, btnEdit, btnDelete);
            actionCluster.setAlignment(Pos.TOP_RIGHT);

            HBox cardHeader = new HBox(10, title, actionCluster);
            cardHeader.setAlignment(Pos.TOP_LEFT);

            VBox infoCard = new VBox(6);
            infoCard.setStyle("-fx-background-color: #21262d; -fx-padding: 12 15 15 15; -fx-background-radius: 6; " +
                              "-fx-border-color: #30363d; -fx-border-radius: 6; -fx-max-width: 260; -fx-min-width: 260;");
            
            Label loc = new Label("📍 " + event.location);
            loc.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
            loc.setWrapText(true);

            Label dt = new Label("📅 " + event.date + " " + event.time);
            dt.setStyle("-fx-text-fill: #c9d1d9; -fx-font-size: 12px;");

            infoCard.getChildren().addAll(cardHeader, loc, dt);
            if (event.reminder == 1) {
                Label badge = new Label("🔔 Alert Active");
                badge.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 10px;");
                infoCard.getChildren().add(badge);
            }

            Circle centralDot = new Circle(6);
            centralDot.setFill(javafx.scene.paint.Color.web("#f0f6fc"));
            centralDot.setStroke(javafx.scene.paint.Color.web("#58a6ff"));
            centralDot.setStrokeWidth(2);

            Line connectorLine = new Line(0, 0, 40, 0);
            connectorLine.setStroke(javafx.scene.paint.Color.web("#8b949e"));
            connectorLine.setStrokeWidth(2);

            if (isLeftBranch) {
                HBox leftPackage = new HBox(0, infoCard, connectorLine);
                leftPackage.setAlignment(Pos.CENTER_RIGHT);
                rowGrid.add(leftPackage, 0, 0);
            } else {
                HBox rightPackage = new HBox(0, connectorLine, infoCard);
                rightPackage.setAlignment(Pos.CENTER_LEFT);
                rowGrid.add(rightPackage, 2, 0);
            }
            
            rowGrid.add(centralDot, 1, 0);
            timelineContainer.getChildren().add(rowGrid);
        }

        canvasStack.getChildren().add(timelineContainer);
        scrollPane.setContent(canvasStack);
        mainRoot.setCenter(scrollPane);
    }

    // --- DELETE ROUTINE PROCESSOR ---
    private void confirmAndExecuteDelete(TimelineEvent event) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Delete Event Permanently");
        confirmAlert.setHeaderText("Remove: " + event.title);
        confirmAlert.setContentText("Are you absolutely sure you want to drop this milestone from your track? This action cannot be reversed.");
        
        confirmAlert.getDialogPane().setStyle("-fx-background-color: #161b22;");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String deleteSql = "DELETE FROM timeline_events WHERE id=?";
                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    
                    pstmt.setInt(1, event.id);
                    pstmt.executeUpdate();

                    // Rerender layout canvas context
                    showTimelinePage();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // --- POPUP DIALOG WINDOW MODULE ---
    private void showEditPopup(TimelineEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modify Event Frame");
        dialog.setHeaderText("Edit details for: " + event.title);
        
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: #161b22;");

        VBox contentGrid = new VBox(10);
        contentGrid.setPadding(new Insets(15));
        contentGrid.setPrefWidth(400);

        TextField editName = new TextField(event.title);
        TextField editLocation = new TextField(event.location);
        
        DatePicker editDatePicker = new DatePicker(java.time.LocalDate.parse(event.date));
        editDatePicker.setMaxWidth(Double.MAX_VALUE);

        String[] timeParts = event.time.split("[: ]");
        
        ComboBox<String> comboHour = new ComboBox<>();
        for (int i = 1; i <= 12; i++) comboHour.getItems().add(String.format("%02d", i));
        comboHour.setValue(timeParts[0]);

        ComboBox<String> comboMinute = new ComboBox<>();
        for (int i = 0; i < 60; i++) comboMinute.getItems().add(String.format("%02d", i));
        comboMinute.setValue(timeParts[1]);

        ComboBox<String> comboMarker = new ComboBox<>();
        comboMarker.getItems().addAll("AM", "PM");
        comboMarker.setValue(timeParts[2]);

        HBox timeRow = new HBox(6, comboHour, new Label(":"), comboMinute, comboMarker);
        timeRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox checkReminder = new CheckBox("Enable Active Desktop Alerts");
        checkReminder.setSelected(event.reminder == 1);

        contentGrid.getChildren().addAll(
            new Label("Update Name:"), editName,
            new Label("Update Location:"), editLocation,
            new Label("Change Date:"), editDatePicker,
            new Label("Adjust Time Window:"), timeRow,
            new Label("Notification Actions:"), checkReminder
        );

        dialog.getDialogPane().setContent(contentGrid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.APPLY) {
                String uName = editName.getText().trim();
                String uLoc = editLocation.getText().trim();
                String uDate = (editDatePicker.getValue() != null) ? editDatePicker.getValue().toString() : event.date;
                String uTime = comboHour.getValue() + ":" + comboMinute.getValue() + " " + comboMarker.getValue();
                int uReminder = checkReminder.isSelected() ? 1 : 0;

                if (!uName.isEmpty()) {
                    String updateSql = "UPDATE timeline_events SET title=?, event_date=?, event_time=?, location=?, reminder=? WHERE id=?";
                    try (Connection conn = DriverManager.getConnection(DB_URL);
                         PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                        
                        pstmt.setString(1, uName);
                        pstmt.setString(2, uDate);
                        pstmt.setString(3, uTime);
                        pstmt.setString(4, uLoc);
                        pstmt.setInt(5, uReminder);
                        pstmt.setInt(6, event.id);
                        pstmt.executeUpdate();

                        showTimelinePage();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
    }

    // --- PAGE 2: Add Event Form Context ---
    private void showAddEventPage() {
        VBox formContainer = new VBox(12);
        formContainer.setPadding(new Insets(30));
        formContainer.setStyle("-fx-background-color: #0d1117;");
        formContainer.setMaxWidth(460);

        Label pageTitle = new Label("Add Event Block Context");
        pageTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #f0f6fc; -fx-padding: 0 0 10 0;");

        TextField txtName = new TextField();
        txtName.setPromptText("Enter event title...");

        TextField txtLocation = new TextField();
        txtLocation.setPromptText("Enter location or link...");

        DatePicker datePicker = new DatePicker();
        datePicker.setMaxWidth(Double.MAX_VALUE);

        HBox timeInputRow = new HBox(8);
        timeInputRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> comboHour = new ComboBox<>();
        for (int i = 1; i <= 12; i++) comboHour.getItems().add(String.format("%02d", i));
        comboHour.setValue("12");

        ComboBox<String> comboMinute = new ComboBox<>();
        for (int i = 0; i < 60; i++) comboMinute.getItems().add(String.format("%02d", i));
        comboMinute.setValue("00");

        ComboBox<String> comboMarker = new ComboBox<>();
        comboMarker.getItems().addAll("AM", "PM");
        comboMarker.setValue("PM");

        timeInputRow.getChildren().addAll(
            comboHour, new Label(":"), comboMinute, comboMarker
        );

        ToggleButton toggleReminder = new ToggleButton("Enable App Alerts");
        toggleReminder.setMaxWidth(Double.MAX_VALUE);

        Button btnSubmit = new Button("Commit to Line System Track");
        btnSubmit.getStyleClass().add("success");
        btnSubmit.setMaxWidth(Double.MAX_VALUE);
        btnSubmit.setPadding(new Insets(10));

        formContainer.getChildren().addAll(
            pageTitle,
            new Label("Event Summary Label:"), txtName,
            new Label("Location Context:"), txtLocation,
            new Label("Target Date:"), datePicker,
            new Label("Target Time (HH:MM AM/PM):"), timeInputRow,
            new Label("System Notification Options:"), toggleReminder,
            new BorderPane(null, null, null, null, btnSubmit)
        );

        btnSubmit.setOnAction(e -> {
            String name = txtName.getText().trim();
            String location = txtLocation.getText().trim();
            String date = (datePicker.getValue() != null) ? datePicker.getValue().toString() : "";
            String compositeTime = comboHour.getValue() + ":" + comboMinute.getValue() + " " + comboMarker.getValue();
            int reminder = toggleReminder.isSelected() ? 1 : 0;

            if (name.isEmpty() || date.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Core metadata attributes cannot be empty!", ButtonType.OK);
                alert.showAndWait();
                return;
            }

            String sql = "INSERT INTO timeline_events(title, event_date, event_time, location, reminder) VALUES(?,?,?,?,?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, name);
                pstmt.setString(2, date);
                pstmt.setString(3, compositeTime);
                pstmt.setString(4, location.isEmpty() ? "Remote Context" : location);
                pstmt.setInt(5, reminder);
                pstmt.executeUpdate();

                showTimelinePage();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        StackPane centerWrapper = new StackPane(formContainer);
        centerWrapper.setStyle("-fx-background-color: #0d1117;");
        mainRoot.setCenter(centerWrapper);
    }

    private List<TimelineEvent> loadEventsFromDb() {
        List<TimelineEvent> list = new ArrayList<>();
        String sql = "SELECT * FROM timeline_events ORDER BY event_date ASC, event_time ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                list.add(new TimelineEvent(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("event_date"),
                    rs.getString("event_time"),
                    rs.getString("location"),
                    rs.getInt("reminder")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createTrayIcon(Stage primaryStage) {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = Toolkit.getDefaultToolkit().createImage(new byte[0]);
            java.awt.PopupMenu popup = new java.awt.PopupMenu();
            
            java.awt.MenuItem openItem = new java.awt.MenuItem("Open TimeLiner");
            openItem.addActionListener(e -> Platform.runLater(primaryStage::show));
            
            java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit Completely");
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

    private static class TimelineEvent {
        int id;
        String title;
        String date;
        String time;
        String location;
        int reminder;

        TimelineEvent(int id, String title, String date, String time, String location, int reminder) {
            this.id = id;
            this.title = title;
            this.date = date;
            this.time = time;
            this.location = location;
            this.reminder = reminder;
        }
    }
}