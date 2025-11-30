package aichat;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/aichat/ui/main.fxml"));
        
        // Get screen bounds and calculate appropriate window size
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double windowWidth = Math.min(1200, screenBounds.getWidth() * 0.9);
        double windowHeight = Math.min(800, screenBounds.getHeight() * 0.9);
        
        Scene scene = new Scene(root, windowWidth, windowHeight);
        scene.getStylesheets().add(getClass().getResource("/aichat/ui/styles.css").toExternalForm());
        
        stage.setTitle("AICHAT - Advanced Image Color Harmony Analysis");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        
        // Center on screen
        stage.setX((screenBounds.getWidth() - windowWidth) / 2);
        stage.setY((screenBounds.getHeight() - windowHeight) / 2);
        
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
