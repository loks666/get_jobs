package boss;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import utils.ConfigFileUtil;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.util.Duration;

import java.io.FileWriter;
import java.util.*;
import java.io.IOException;

public class BossConfigController {
    private static final Logger log = LoggerFactory.getLogger(BossConfigController.class);

    @FXML
    private CheckBox debuggerCheckBox;
    @FXML
    private TextArea sayHiTextArea;
    @FXML
    private TextField keywordsField;
    @FXML
    private TextField industryField;
    @FXML
    private TextField cityCodeField;
    @FXML
    private ComboBox<String> experienceComboBox;
    @FXML
    private ComboBox<String> jobTypeComboBox;
    @FXML
    private ComboBox<String> salaryComboBox;
    @FXML
    private ComboBox<String> degreeComboBox;
    @FXML
    private ComboBox<String> scaleComboBox;
    @FXML
    private ComboBox<String> stageComboBox;
    @FXML
    private TextField minSalaryField;
    @FXML
    private TextField maxSalaryField;
    @FXML
    private TextField waitTimeField;
    @FXML
    private CheckBox filterDeadHRCheckBox;
    @FXML
    private CheckBox enableAIJobMatchDetectionCheckBox;
    @FXML
    private CheckBox enableAIGreetingCheckBox;
    @FXML
    private CheckBox sendImgResumeCheckBox;
    @FXML
    private CheckBox keyFilterCheckBox;
    @FXML
    private CheckBox recommendJobsCheckBox;
    @FXML
    private CheckBox h5JobsCheckBox;
    @FXML
    private CheckBox checkStateOwnedCheckBox;
    @FXML
    private TextField vipKeyField;
    @FXML
    private TextField apiDomainField;
    @FXML
    private TextField resumeImagePathField;
    @FXML
    private TextArea resumeContentTextArea;
    @FXML
    private TextArea logTextArea;
    @FXML
    private VBox customCityCodeContainer;
    @FXML
    private VBox deadStatusContainer;
    private List<HBox> customCityCodeRows = new ArrayList<>();
    private List<TextField> deadStatusFields = new ArrayList<>();

    @FXML
    public void initialize() {
        // 设置TextArea到Appender
        TextAreaAppender.setTextArea(logTextArea);

        // 初始化下拉框选项
        experienceComboBox.getItems().addAll("应届毕业生", "1年以下", "1-3年", "3-5年", "5-10年", "10年以上");
        jobTypeComboBox.getItems().addAll("全职", "兼职", "不限");
        salaryComboBox.getItems().addAll("3K以下", "3-5K", "5-10K", "10-20K", "20-50K", "50K以上");
        degreeComboBox.getItems().addAll("初中及以下", "中专/中技", "高中", "大专", "本科", "硕士", "博士");
        scaleComboBox.getItems().addAll("0-20人", "20-99人", "100-499人", "500-999人", "1000-9999人", "10000人以上", "不限");
        stageComboBox.getItems().addAll("未融资", "天使轮", "A轮", "B轮", "C轮", "D轮及以上", "已上市", "不需要融资", "不限");

        // 初始化自定义城市代码容器
        customCityCodeContainer.setSpacing(5);
        customCityCodeContainer.setPadding(new Insets(5));

        // 初始化HR状态容器
        deadStatusContainer.setSpacing(5);
        deadStatusContainer.setPadding(new Insets(5));

        // 加载现有配置
        loadConfig();

        // 重定向日志到TextArea
        redirectLogToTextArea();

        // 优化所有Tooltip的显示速度
        optimizeTooltips();
    }

    private void loadConfig() {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(ConfigFileUtil.getConfigInputStream());
            Map<String, Object> bossConfig = (Map<String, Object>) config.get("boss");

            // 设置表单值
            debuggerCheckBox.setSelected((Boolean) bossConfig.getOrDefault("debugger", false));
            sayHiTextArea.setText((String) bossConfig.get("sayHi"));
            keywordsField.setText(String.join(",", (List<String>) bossConfig.get("keywords")));
            industryField.setText(String.join(",", (List<String>) bossConfig.get("industry")));
            cityCodeField.setText(String.join(",", (List<String>) bossConfig.get("cityCode")));
            experienceComboBox.setValue(((List<String>) bossConfig.get("experience")).get(0));
            jobTypeComboBox.setValue((String) bossConfig.get("jobType"));
            salaryComboBox.setValue((String) bossConfig.get("salary"));
            degreeComboBox.setValue(((List<String>) bossConfig.get("degree")).get(0));
            scaleComboBox.setValue(((List<String>) bossConfig.get("scale")).get(0));
            stageComboBox.setValue(((List<String>) bossConfig.get("stage")).get(0));

            List<Integer> expectedSalary = (List<Integer>) bossConfig.get("expectedSalary");
            if (expectedSalary != null && expectedSalary.size() >= 2) {
                minSalaryField.setText(expectedSalary.get(0).toString());
                maxSalaryField.setText(expectedSalary.get(1).toString());
            }

            waitTimeField.setText(bossConfig.get("waitTime").toString());
            filterDeadHRCheckBox.setSelected((Boolean) bossConfig.getOrDefault("filterDeadHR", false));
            enableAIJobMatchDetectionCheckBox
                    .setSelected((Boolean) bossConfig.getOrDefault("enableAIJobMatchDetection", false));
            enableAIGreetingCheckBox
                    .setSelected((Boolean) bossConfig.getOrDefault("enableAIGreeting", false));
            sendImgResumeCheckBox.setSelected((Boolean) bossConfig.getOrDefault("sendImgResume", false));
            keyFilterCheckBox.setSelected((Boolean) bossConfig.getOrDefault("keyFilter", false));
            recommendJobsCheckBox.setSelected((Boolean) bossConfig.getOrDefault("recommendJobs", false));
            h5JobsCheckBox.setSelected((Boolean) bossConfig.getOrDefault("h5Jobs", false));
            checkStateOwnedCheckBox.setSelected((Boolean) bossConfig.getOrDefault("checkStateOwned", false));
            vipKeyField.setText((String) bossConfig.getOrDefault("vipKey", ""));
            apiDomainField.setText((String) bossConfig.getOrDefault("apiDomain", ""));
            resumeImagePathField.setText((String) bossConfig.getOrDefault("resumeImagePath", ""));
            resumeContentTextArea.setText((String) bossConfig.getOrDefault("resumeContent", ""));

            // 加载自定义城市代码
            Map<String, String> customCityCode = (Map<String, String>) bossConfig.getOrDefault("customCityCode",
                    new HashMap<>());
            customCityCode.forEach((city, code) -> addCustomCityCodeRow(city, code));

            // 添加一个空行用于新增
            addCustomCityCodeRow("", "");

            // 加载HR状态列表
            List<String> deadStatus = (List<String>) bossConfig.getOrDefault("deadStatus",
                    Arrays.asList("2周内活跃", "本月活跃", "3月内活跃", "2月内活跃", "半年前活跃"));

            deadStatus.forEach(this::addDeadStatusField);
            // 添加一个空字段用于新增
            addDeadStatusField("");

        } catch (Exception e) {
            log.error("加载配置文件失败", e);
        }
    }

    private void addCustomCityCodeRow(String city, String code) {
        HBox row = new HBox(5);
        TextField cityField = new TextField(city);
        TextField codeField = new TextField(code);
        Button deleteBtn = new Button("删除");

        cityField.setPromptText("城市名");
        codeField.setPromptText("城市代码");

        deleteBtn.setOnAction(e -> {
            customCityCodeContainer.getChildren().remove(row);
            customCityCodeRows.remove(row);
        });

        row.getChildren().addAll(cityField, codeField, deleteBtn);
        customCityCodeRows.add(row);
        customCityCodeContainer.getChildren().add(row);

        // 如果是最后一个空行被填写，则添加新的空行
        cityField.textProperty().addListener((obs, old, newValue) -> {
            if (!newValue.isEmpty() && row == customCityCodeRows.get(customCityCodeRows.size() - 1)) {
                addCustomCityCodeRow("", "");
            }
        });
    }

    private void addDeadStatusField(String status) {
        HBox row = new HBox(5);
        TextField statusField = new TextField(status);
        Button deleteBtn = new Button("删除");

        statusField.setPromptText("HR状态");
        HBox.setHgrow(statusField, Priority.ALWAYS);

        deleteBtn.setOnAction(e -> {
            deadStatusContainer.getChildren().remove(row);
            deadStatusFields.remove(statusField);
        });

        row.getChildren().addAll(statusField, deleteBtn);
        deadStatusFields.add(statusField);
        deadStatusContainer.getChildren().add(row);

        // 如果是最后一个空字段被填写，则添加新的空字段
        statusField.textProperty().addListener((obs, old, newValue) -> {
            if (!newValue.isEmpty() && statusField == deadStatusFields.get(deadStatusFields.size() - 1)) {
                addDeadStatusField("");
            }
        });
    }

    @FXML
    private void handleSaveAndStart() {
        try {
            // 读取现有配置文件
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(ConfigFileUtil.getConfigInputStream());

            // 获取现有的boss配置，如果不存在则创建新的
            Map<String, Object> existingBossConfig = (Map<String, Object>) config.getOrDefault("boss", new HashMap<>());

            // 更新boss配置中的属性
            existingBossConfig.put("debugger", debuggerCheckBox.isSelected());
            existingBossConfig.put("sayHi", sayHiTextArea.getText());
            existingBossConfig.put("keywords", Arrays.asList(keywordsField.getText().split(",")));
            existingBossConfig.put("industry", Arrays.asList(industryField.getText().split(",")));
            existingBossConfig.put("cityCode", Arrays.asList(cityCodeField.getText().split(",")));
            existingBossConfig.put("experience", Collections.singletonList(experienceComboBox.getValue()));
            existingBossConfig.put("jobType", jobTypeComboBox.getValue());
            existingBossConfig.put("salary", salaryComboBox.getValue());
            existingBossConfig.put("degree", Collections.singletonList(degreeComboBox.getValue()));
            existingBossConfig.put("scale", Collections.singletonList(scaleComboBox.getValue()));
            existingBossConfig.put("stage", Collections.singletonList(stageComboBox.getValue()));

            List<Integer> expectedSalary = new ArrayList<>();
            expectedSalary.add(Integer.parseInt(minSalaryField.getText()));
            expectedSalary.add(Integer.parseInt(maxSalaryField.getText()));
            existingBossConfig.put("expectedSalary", expectedSalary);

            existingBossConfig.put("waitTime", Integer.parseInt(waitTimeField.getText()));
            existingBossConfig.put("filterDeadHR", filterDeadHRCheckBox.isSelected());
            existingBossConfig.put("enableAIJobMatchDetection", enableAIJobMatchDetectionCheckBox.isSelected());
            existingBossConfig.put("enableAIGreeting", enableAIGreetingCheckBox.isSelected());
            existingBossConfig.put("sendImgResume", sendImgResumeCheckBox.isSelected());
            existingBossConfig.put("keyFilter", keyFilterCheckBox.isSelected());
            existingBossConfig.put("recommendJobs", recommendJobsCheckBox.isSelected());
            existingBossConfig.put("h5Jobs", h5JobsCheckBox.isSelected());
            existingBossConfig.put("checkStateOwned", checkStateOwnedCheckBox.isSelected());
            existingBossConfig.put("vipKey", vipKeyField.getText());
            existingBossConfig.put("apiDomain", apiDomainField.getText());
            existingBossConfig.put("resumeImagePath", resumeImagePathField.getText());
            existingBossConfig.put("resumeContent", resumeContentTextArea.getText());

            log.info("是否投递推荐岗位:{}", recommendJobsCheckBox.isSelected());

            // 保存自定义城市代码
            Map<String, String> customCityCode = new HashMap<>();
            for (HBox row : customCityCodeRows) {
                TextField cityField = (TextField) row.getChildren().get(0);
                TextField codeField = (TextField) row.getChildren().get(1);
                if (!cityField.getText().isEmpty() && !codeField.getText().isEmpty()) {
                    customCityCode.put(cityField.getText(), codeField.getText());
                }
            }
            existingBossConfig.put("customCityCode", customCityCode);

            // 保存HR状态列表
            List<String> deadStatus = new ArrayList<>();
            for (TextField field : deadStatusFields) {
                if (!field.getText().isEmpty()) {
                    deadStatus.add(field.getText());
                }
            }
            existingBossConfig.put("deadStatus", deadStatus);

            // 更新boss配置部分
            config.put("boss", existingBossConfig);

            // 保存配置文件
            try (FileWriter writer = new FileWriter(ConfigFileUtil.getConfigWritePath())) {
                yaml.dump(config, writer);
            }

            // 同步配置到编译目录，使其立即生效
            try {
                ConfigFileUtil.syncConfigToClasspath();
                log.info("配置文件已同步到类路径");
            } catch (IOException e) {
                log.warn("无法同步配置文件到类路径，配置将在下次编译后生效: {}", e.getMessage());
            }

            // 重新加载BossConfig单例实例
            // 优先使用内存中的配置直接更新，确保配置立即生效
            BossConfig.reload(existingBossConfig);
            log.info("配置已保存并重新加载");

            // 启动Boss程序
            new Thread(() -> {
                try {
                    // 传入参数以执行原有逻辑
                    Boss.main(new String[] { "start" });
                } catch (Exception e) {
                    log.error("运行Boss程序失败", e);
                }
            }).start();

        } catch (Exception e) {
            log.error("保存配置失败", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText("保存配置失败：" + e.getMessage());
            alert.showAndWait();
        }
    }

    private void redirectLogToTextArea() {
        // 创建自定义的日志追加器
        Logger rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (rootLogger instanceof ch.qos.logback.classic.Logger) {
            ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) rootLogger;

            logbackLogger.addAppender(new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
                @Override
                protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                    String logMessage = event.getFormattedMessage() + "\n";
                    Platform.runLater(() -> {
                        logTextArea.appendText(logMessage);
                        logTextArea.setScrollTop(Double.MAX_VALUE);
                    });
                }
            });
        }
    }

    /**
     * 优化所有Tooltip的显示速度，让它们立即显示
     */
    private void optimizeTooltips() {
        // 等待一帧后执行，确保FXML完全加载
        Platform.runLater(() -> {
            try {
                // 获取根节点并遍历所有子节点
                if (keywordsField.getScene() != null) {
                    Parent root = keywordsField.getScene().getRoot();
                    optimizeTooltipsRecursively(root);
                    log.info("已优化所有Tooltip显示速度");
                }
            } catch (Exception e) {
                log.warn("优化Tooltip时出现错误: {}", e.getMessage());
            }
        });
    }

    /**
     * 递归遍历所有节点并优化其Tooltip
     */
    private void optimizeTooltipsRecursively(Node node) {
        // 优化当前节点的Tooltip
        if (node instanceof Control) {
            Control control = (Control) node;
            Tooltip tooltip = control.getTooltip();
            if (tooltip != null) {
                // 设置Tooltip立即显示（无延迟）
                tooltip.setShowDelay(Duration.millis(0));
                // 设置Tooltip无限显示时间，直到鼠标离开
                tooltip.setShowDuration(Duration.INDEFINITE);
                // 设置较长的隐藏延迟，避免鼠标在控件边缘轻微移动就消失
                tooltip.setHideDelay(Duration.millis(500));

                // 使用更温和的方式：只是重新安装Tooltip来应用新的时间设置
                // 保存原有的Tooltip文本和样式
                String tooltipText = tooltip.getText();

                // 创建新的优化过的Tooltip
                Tooltip newTooltip = new Tooltip(tooltipText);
                newTooltip.setShowDelay(Duration.millis(0));
                newTooltip.setShowDuration(Duration.INDEFINITE);
                newTooltip.setHideDelay(Duration.millis(500));

                // 应用新的Tooltip
                control.setTooltip(newTooltip);
            }
        }

        // 如果是父节点，递归处理所有子节点
        if (node instanceof Parent) {
            Parent parent = (Parent) node;
            for (Node child : parent.getChildrenUnmodifiable()) {
                optimizeTooltipsRecursively(child);
            }
        }
    }
}