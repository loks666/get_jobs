package boss;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class BossConfigApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/BossConfig.fxml"));
        Parent root = loader.load();
        
        primaryStage.setTitle("Boss直聘配置");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void launch() {
        Application.launch(BossConfigApplication.class);
    }
} 