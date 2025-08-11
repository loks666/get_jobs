package boss.fxml;

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
        
        // 设置窗口大小，避免被底部导航栏遮挡
        primaryStage.setWidth(1400);
        primaryStage.setHeight(900);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(800);
        
        // 设置窗口居中显示
        primaryStage.centerOnScreen();
        
        primaryStage.show();
    }

    public static void launch() {
        Application.launch(BossConfigApplication.class);
    }
} 