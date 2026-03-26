import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ConnectFourFX extends Application {
    private static final int ROWS = 6;
    private static final int COLUMNS = 7;
    private static final int WIN_LENGTH = 4;
    private static final int MAX_SCORE = 1_000_000;
    private static final int[] COLUMN_ORDER = {3, 2, 4, 1, 5, 0, 6};
    private static final Interpolator OUTLOOK_METER_INTERPOLATOR = Interpolator.SPLINE(0.42, 0.0, 0.18, 1.0);

    private static final char EMPTY = ' ';
    private static final char HUMAN_MARK = 'X';
    private static final char AI_MARK = 'O';

    private static final double CELL_SIZE = 94.0;
    private static final double DISC_RADIUS = 31.0;
    private static final double BOARD_PADDING = 22.0;
    private static final double BOARD_TOP_LANE = 92.0;
    private static final double BOARD_WIDTH = COLUMNS * CELL_SIZE;
    private static final double BOARD_HEIGHT = ROWS * CELL_SIZE;
    private static final double BOARD_X = BOARD_PADDING;
    private static final double BOARD_Y = BOARD_TOP_LANE;
    private static final double SURFACE_WIDTH = BOARD_WIDTH + BOARD_PADDING * 2.0;
    private static final double SURFACE_HEIGHT = BOARD_Y + BOARD_HEIGHT + BOARD_PADDING;

    private final Random random = new Random();
    private final char[][] board = new char[ROWS][COLUMNS];
    private final int[] heights = new int[COLUMNS];
    private final DoubleProperty favorBalance = new SimpleDoubleProperty(0.5);
    private final List<Button> difficultyButtons = new ArrayList<Button>();

    private final Circle[][] discViews = new Circle[ROWS][COLUMNS];
    private final Rectangle[] hoverHighlights = new Rectangle[COLUMNS];

    private Pane boardSurface;
    private Pane animationLayer;
    private Circle previewDisc;
    private Label statusLabel;
    private Label detailLabel;
    private Label favorLabel;
    private Button restartButton;
    private MovePopup pendingMovePopup;
    private Animation activeMoveFeedbackAnimation;
    private Label activeMoveFeedbackLabel;
    private Animation activeFavorMeterAnimation;
    private AiDifficulty selectedDifficulty = AiDifficulty.IMPOSSIBLE;

    private boolean humanTurn;
    private boolean busy;
    private boolean gameOver;
    private char winner = EMPTY;
    private int hoveredColumn = -1;
    private long gameToken;

    private PauseTransition pendingAiDelay;
    private Animation activeDropAnimation;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        initializeBoardState();

        StackPane sceneRoot = new StackPane();
        sceneRoot.getStyleClass().add("root-pane");

        Pane atmosphere = buildAtmosphere();
        VBox layout = new VBox(20);
        layout.getStyleClass().add("app-shell");
        layout.setPadding(new Insets(28, 34, 32, 34));
        layout.setFillWidth(true);

        VBox topSection = new VBox(18, buildHeader(), buildStatusCard());
        topSection.getStyleClass().add("hero-stack");
        topSection.setAlignment(Pos.CENTER);
        topSection.setMaxWidth(Double.MAX_VALUE);

        StackPane boardCard = new StackPane(buildBoardView());
        boardCard.getStyleClass().addAll("glass-card", "board-card");
        boardCard.setPadding(new Insets(28, 30, 30, 30));
        boardCard.setAlignment(Pos.CENTER);
        boardCard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        FlowPane controls = buildControls();

        layout.getChildren().addAll(topSection, boardCard, controls);
        VBox.setVgrow(boardCard, Priority.ALWAYS);
        VBox.setMargin(boardCard, new Insets(8, 0, 6, 0));

        sceneRoot.getChildren().addAll(atmosphere, layout);

        Scene scene = new Scene(sceneRoot, 980, 900);
        loadStylesheet(scene);

        stage.setTitle("Connect Four FX");
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(680);
        stage.show();

        startNewGame();
    }

    private Pane buildAtmosphere() {
        Pane atmosphere = new Pane();
        atmosphere.setMouseTransparent(true);

        Circle leftGlow = new Circle(230, Color.rgb(83, 214, 255, 0.16));
        leftGlow.setTranslateX(-360);
        leftGlow.setTranslateY(-230);
        leftGlow.setEffect(new GaussianBlur(150));

        Circle upperGlow = new Circle(180, Color.rgb(93, 108, 255, 0.10));
        upperGlow.setTranslateX(40);
        upperGlow.setTranslateY(-310);
        upperGlow.setEffect(new GaussianBlur(130));

        Circle rightGlow = new Circle(250, Color.rgb(255, 160, 94, 0.14));
        rightGlow.setTranslateX(380);
        rightGlow.setTranslateY(-170);
        rightGlow.setEffect(new GaussianBlur(170));

        Circle lowerGlow = new Circle(310, Color.rgb(44, 116, 255, 0.14));
        lowerGlow.setTranslateX(-150);
        lowerGlow.setTranslateY(300);
        lowerGlow.setEffect(new GaussianBlur(190));

        Circle lowerRightGlow = new Circle(220, Color.rgb(56, 214, 255, 0.10));
        lowerRightGlow.setTranslateX(280);
        lowerRightGlow.setTranslateY(320);
        lowerRightGlow.setEffect(new GaussianBlur(150));

        atmosphere.getChildren().addAll(leftGlow, upperGlow, rightGlow, lowerGlow, lowerRightGlow);
        return atmosphere;
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("ARCADE DESKTOP EDITION");
        eyebrow.getStyleClass().add("eyebrow-label");

        Label title = new Label("Connect Four");
        title.setFont(Font.font("Trebuchet MS", FontWeight.EXTRA_BOLD, 48));
        title.getStyleClass().add("title-label");

        Label subtitle = new Label("Click a column, watch the disc fall, and try to outplay the AI.");
        subtitle.getStyleClass().add("subtitle-label");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(680);

        VBox header = new VBox(10, eyebrow, title, subtitle);
        header.getStyleClass().add("hero-block");
        header.setAlignment(Pos.CENTER);
        return header;
    }

    private VBox buildStatusCard() {
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        detailLabel = new Label();
        detailLabel.getStyleClass().add("detail-label");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(560);
        detailLabel.setAlignment(Pos.CENTER);

        Label favorHeading = new Label("Position Outlook");
        favorHeading.getStyleClass().add("meter-heading");

        favorLabel = new Label();
        favorLabel.getStyleClass().add("meter-label");

        Rectangle meterTrack = new Rectangle();
        meterTrack.setHeight(18.0);
        meterTrack.setArcWidth(18.0);
        meterTrack.setArcHeight(18.0);
        meterTrack.setFill(new LinearGradient(
            0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#ff6c58")),
            new Stop(0.18, Color.web("#cb4b3b")),
            new Stop(0.48, Color.web("#22314b")),
            new Stop(0.52, Color.web("#1a263c")),
            new Stop(0.82, Color.web("#b98727")),
            new Stop(1.0, Color.web("#ffcf66"))
        ));
        meterTrack.setStroke(Color.rgb(225, 242, 255, 0.18));
        meterTrack.setStrokeWidth(1.0);

        Rectangle meterGloss = new Rectangle();
        meterGloss.setHeight(18.0);
        meterGloss.setArcWidth(18.0);
        meterGloss.setArcHeight(18.0);
        meterGloss.setFill(new LinearGradient(
            0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.26)),
            new Stop(1.0, Color.rgb(255, 255, 255, 0.02))
        ));
        meterGloss.setMouseTransparent(true);

        Rectangle centerMarker = new Rectangle(2.5, 26.0);
        centerMarker.setArcWidth(2.5);
        centerMarker.setArcHeight(2.5);
        centerMarker.setFill(Color.rgb(255, 255, 255, 0.40));
        centerMarker.setMouseTransparent(true);

        Rectangle favorIndicator = new Rectangle(9.0, 32.0);
        favorIndicator.setArcWidth(9.0);
        favorIndicator.setArcHeight(9.0);
        favorIndicator.setFill(Color.rgb(244, 248, 255, 0.92));
        favorIndicator.setStroke(Color.rgb(17, 34, 72, 0.38));
        favorIndicator.setStrokeWidth(1.0);
        favorIndicator.setEffect(new DropShadow(12.0, Color.rgb(8, 12, 22, 0.34)));
        favorIndicator.setMouseTransparent(true);

        StackPane meterPane = new StackPane(meterTrack, meterGloss, centerMarker, favorIndicator);
        meterPane.getStyleClass().add("meter-pane");
        meterPane.setMinHeight(36.0);
        meterPane.setMaxWidth(Double.MAX_VALUE);

        Label humanSideLabel = new Label("You");
        humanSideLabel.getStyleClass().add("meter-side-label");
        Label aiSideLabel = new Label("AI");
        aiSideLabel.getStyleClass().add("meter-side-label");
        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
        HBox legend = new HBox(10, humanSideLabel, leftSpacer, favorLabel, rightSpacer, aiSideLabel);
        legend.getStyleClass().add("meter-legend");
        legend.setAlignment(Pos.CENTER);
        legend.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(
            8,
            statusLabel,
            detailLabel,
            favorHeading,
            meterPane,
            legend
        );
        card.getStyleClass().addAll("glass-card", "status-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(22, 28, 24, 28));
        card.setMaxWidth(680);

        meterTrack.widthProperty().bind(card.widthProperty().subtract(72.0));
        meterGloss.widthProperty().bind(meterTrack.widthProperty());
        favorIndicator.translateXProperty().bind(
            favorBalance.multiply(2.0)
                .subtract(1.0)
                .multiply(meterTrack.widthProperty().subtract(18.0).divide(2.0))
        );
        return card;
    }

    private FlowPane buildControls() {
        VBox difficultyPanel = buildDifficultySelector();

        restartButton = new Button();
        restartButton.getStyleClass().add("primary-button");
        restartButton.setOnAction(event -> startNewGame());
        refreshRestartButton();

        FlowPane controls = new FlowPane(18, 14, difficultyPanel, restartButton);
        controls.getStyleClass().add("controls-row");
        controls.setAlignment(Pos.CENTER);
        controls.setRowValignment(javafx.geometry.VPos.CENTER);
        controls.setMaxWidth(Double.MAX_VALUE);
        controls.setPadding(new Insets(4, 0, 0, 0));
        return controls;
    }

    private VBox buildDifficultySelector() {
        Label difficultyHeading = new Label("AI Difficulty");
        difficultyHeading.getStyleClass().add("difficulty-heading");

        HBox buttonsRow = new HBox(10);
        buttonsRow.getStyleClass().add("difficulty-button-row");
        buttonsRow.setAlignment(Pos.CENTER);

        difficultyButtons.clear();
        for (AiDifficulty difficulty : AiDifficulty.values()) {
            Button button = new Button(difficulty.label);
            button.getStyleClass().addAll("secondary-button", "difficulty-button");
            button.setOnAction(event -> setSelectedDifficulty(difficulty));
            difficultyButtons.add(button);
            buttonsRow.getChildren().add(button);
        }

        refreshDifficultyButtons();

        VBox panel = new VBox(10, difficultyHeading, buttonsRow);
        panel.getStyleClass().add("difficulty-panel");
        panel.setAlignment(Pos.CENTER);
        return panel;
    }

    private void setSelectedDifficulty(AiDifficulty difficulty) {
        selectedDifficulty = difficulty;
        refreshDifficultyButtons();
    }

    private void refreshDifficultyButtons() {
        for (int index = 0; index < difficultyButtons.size(); index++) {
            Button button = difficultyButtons.get(index);
            button.getStyleClass().remove("difficulty-button-selected");
            if (AiDifficulty.values()[index] == selectedDifficulty) {
                button.getStyleClass().add("difficulty-button-selected");
            }
        }
    }

    private StackPane buildBoardView() {
        boardSurface = new Pane();
        boardSurface.setPrefSize(SURFACE_WIDTH, SURFACE_HEIGHT);

        Rectangle boardAura = new Rectangle(BOARD_X - 28, BOARD_Y - 28, BOARD_WIDTH + 56, BOARD_HEIGHT + 56);
        boardAura.setArcWidth(84);
        boardAura.setArcHeight(84);
        boardAura.setFill(Color.rgb(78, 204, 255, 0.14));
        boardAura.setEffect(new GaussianBlur(42));

        Rectangle boardShell = new Rectangle(BOARD_X - 12, BOARD_Y - 12, BOARD_WIDTH + 24, BOARD_HEIGHT + 24);
        boardShell.setArcWidth(56);
        boardShell.setArcHeight(56);
        boardShell.setFill(new LinearGradient(
            0.0, 0.0, 1.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#1f2f4f")),
            new Stop(0.48, Color.web("#111c33")),
            new Stop(1.0, Color.web("#070d18"))
        ));
        boardShell.setStroke(Color.rgb(224, 243, 255, 0.12));
        boardShell.setStrokeWidth(1.4);
        boardShell.setEffect(new DropShadow(34, Color.rgb(3, 6, 14, 0.52)));

        Rectangle boardBody = new Rectangle(BOARD_X, BOARD_Y, BOARD_WIDTH, BOARD_HEIGHT);
        boardBody.setArcWidth(46);
        boardBody.setArcHeight(46);
        boardBody.setFill(new LinearGradient(
            0.0, 0.0, 1.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#264fdb")),
            new Stop(0.30, Color.web("#1a40b8")),
            new Stop(0.68, Color.web("#132c7c")),
            new Stop(1.0, Color.web("#0b1636"))
        ));
        boardBody.setStroke(Color.rgb(188, 229, 255, 0.22));
        boardBody.setStrokeWidth(2.0);

        Rectangle boardGloss = new Rectangle(BOARD_X + 10, BOARD_Y + 10, BOARD_WIDTH - 20, 98);
        boardGloss.setArcWidth(34);
        boardGloss.setArcHeight(34);
        boardGloss.setFill(new LinearGradient(
            0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.28)),
            new Stop(0.35, Color.rgb(189, 226, 255, 0.12)),
            new Stop(1.0, Color.rgb(255, 255, 255, 0.0))
        ));

        Rectangle boardLowerSheen = new Rectangle(BOARD_X + 20, BOARD_Y + BOARD_HEIGHT - 128, BOARD_WIDTH - 40, 96);
        boardLowerSheen.setArcWidth(32);
        boardLowerSheen.setArcHeight(32);
        boardLowerSheen.setFill(new LinearGradient(
            0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.0)),
            new Stop(1.0, Color.rgb(108, 198, 255, 0.10))
        ));

        boardSurface.getChildren().addAll(boardAura, boardShell, boardBody, boardGloss, boardLowerSheen);

        buildColumnChrome();
        buildSlots();

        previewDisc = createDiscNode(HUMAN_MARK, true);
        previewDisc.setOpacity(0.92);
        previewDisc.setVisible(false);
        boardSurface.getChildren().add(previewDisc);

        animationLayer = new Pane();
        animationLayer.setMouseTransparent(true);
        animationLayer.setPrefSize(SURFACE_WIDTH, SURFACE_HEIGHT);

        Pane inputLayer = new Pane();
        inputLayer.setPickOnBounds(false);
        inputLayer.setPrefSize(SURFACE_WIDTH, SURFACE_HEIGHT);
        buildInputTargets(inputLayer);

        boardSurface.getChildren().addAll(animationLayer, inputLayer);

        Group boardGroup = new Group(boardSurface);

        StackPane wrapper = new StackPane(boardGroup);
        wrapper.getStyleClass().add("board-wrapper");
        wrapper.setPadding(new Insets(8));
        wrapper.setMinSize(0.0, 0.0);
        wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        wrapper.widthProperty().addListener((observable, oldValue, newValue) -> updateBoardScale(wrapper, boardGroup));
        wrapper.heightProperty().addListener((observable, oldValue, newValue) -> updateBoardScale(wrapper, boardGroup));
        return wrapper;
    }

    private void updateBoardScale(StackPane wrapper, Group boardGroup) {
        double availableWidth = wrapper.getWidth() - 16.0;
        double availableHeight = wrapper.getHeight() - 16.0;

        if (availableWidth <= 0.0 || availableHeight <= 0.0) {
            return;
        }

        double scale = Math.min(availableWidth / SURFACE_WIDTH, availableHeight / SURFACE_HEIGHT);
        scale = clamp(scale, 0.50, 1.18);
        boardGroup.setScaleX(scale);
        boardGroup.setScaleY(scale);
    }

    private void buildColumnChrome() {
        for (int column = 0; column < COLUMNS; column++) {
            double x = columnCenterX(column);

            Label label = new Label(String.valueOf(column + 1));
            label.getStyleClass().add("column-label");
            label.setAlignment(Pos.CENTER);
            label.setMinSize(32.0, 32.0);
            label.setPrefSize(32.0, 32.0);
            label.setLayoutX(x - 16.0);
            label.setLayoutY(18.0);

            Rectangle glow = new Rectangle(
                BOARD_X + column * CELL_SIZE + 8,
                BOARD_Y - 48,
                CELL_SIZE - 16,
                BOARD_HEIGHT + 74
            );
            glow.setArcWidth(28);
            glow.setArcHeight(28);
            glow.setFill(new LinearGradient(
                0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(138, 224, 255, 0.34)),
                new Stop(0.28, Color.rgb(83, 182, 255, 0.16)),
                new Stop(1.0, Color.rgb(40, 68, 128, 0.02))
            ));
            glow.setOpacity(0.0);

            hoverHighlights[column] = glow;
            boardSurface.getChildren().addAll(glow, label);
        }
    }

    private void buildSlots() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                double centerX = columnCenterX(column);
                double centerY = rowCenterY(row);

                Circle bezel = new Circle(DISC_RADIUS + 6.0);
                bezel.setCenterX(centerX);
                bezel.setCenterY(centerY);
                bezel.setFill(Color.rgb(4, 10, 27, 0.62));
                bezel.setStroke(Color.rgb(181, 229, 255, 0.09));
                bezel.setStrokeWidth(1.0);
                bezel.setEffect(new DropShadow(12, Color.rgb(0, 0, 0, 0.34)));

                Circle disc = new Circle(DISC_RADIUS);
                disc.setCenterX(centerX);
                disc.setCenterY(centerY);
                discViews[row][column] = disc;
                applyDiscStyle(disc, EMPTY, false, false);

                Circle sparkle = new Circle(DISC_RADIUS - 18.0);
                sparkle.setCenterX(centerX - 10.0);
                sparkle.setCenterY(centerY - 10.0);
                sparkle.setFill(Color.rgb(255, 255, 255, 0.06));
                sparkle.setMouseTransparent(true);

                boardSurface.getChildren().addAll(bezel, disc, sparkle);
            }
        }
    }

    private void buildInputTargets(Pane inputLayer) {
        for (int column = 0; column < COLUMNS; column++) {
            final int targetColumn = column;

            Rectangle hitBox = new Rectangle(
                BOARD_X + column * CELL_SIZE,
                BOARD_Y - 54,
                CELL_SIZE,
                BOARD_HEIGHT + 84
            );
            hitBox.setArcWidth(28);
            hitBox.setArcHeight(28);
            hitBox.setFill(Color.TRANSPARENT);

            hitBox.setOnMouseEntered(event -> {
                hoveredColumn = targetColumn;
                updateHoverState();
            });
            hitBox.setOnMouseExited(event -> {
                hoveredColumn = -1;
                updateHoverState();
            });
            hitBox.setOnMouseClicked(event -> handleHumanMove(targetColumn));

            inputLayer.getChildren().add(hitBox);
        }
    }

    private void startNewGame() {
        cancelPendingWork();
        gameToken++;
        busy = false;
        gameOver = false;
        winner = EMPTY;
        hoveredColumn = -1;
        refreshRestartButton();

        initializeBoardState();
        refreshBoardVisuals();
        updateWinOutlook();

        humanTurn = random.nextBoolean();
        if (humanTurn) {
            setStatus("Your move", "Click a column to drop your coral disc.");
            updateHoverState();
        } else {
            setStatus("AI opens the game", "It will make the first move after a short beat.");
            updateHoverState();
            queueAiTurn(gameToken);
        }
    }

    private void initializeBoardState() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                board[row][column] = EMPTY;
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            heights[column] = 0;
        }
    }

    private void refreshBoardVisuals() {
        animationLayer.getChildren().clear();
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                applyDiscStyle(discViews[row][column], EMPTY, false, false);
            }
        }
    }

    private void handleHumanMove(int column) {
        if (!isHumanInputAllowed()) {
            return;
        }
        if (isColumnFull(heights, column)) {
            setStatus("That lane is full", "Try one of the glowing columns instead.");
            updateHoverState();
            return;
        }

        final long token = gameToken;
        final char[][] boardSnapshot = cloneBoard(board);
        final int[] heightsSnapshot = heights.clone();
        final int reviewDepth = chooseSearchDepth(heightsSnapshot);
        final int reviewRow = ROWS - 1 - heightsSnapshot[column];

        busy = true;
        hoveredColumn = column;
        pendingMovePopup = new MovePopup(token, reviewRow, column);
        updateHoverState();
        setStatus("You played column " + (column + 1), "Dropping your disc into the board.");
        queueHumanMoveReview(token, boardSnapshot, heightsSnapshot, column, reviewDepth);

        animateMove(column, HUMAN_MARK, token, new Runnable() {
            @Override
            public void run() {
                afterMoveResolved(HUMAN_MARK);
            }
        });
    }

    private void afterMoveResolved(char markPlayed) {
        if (gameOver) {
            return;
        }

        humanTurn = markPlayed != HUMAN_MARK;
        if (humanTurn) {
            busy = false;
            setStatus("Your move", "The board is live. Hover a column and click to play.");
            updateHoverState();
        } else {
            queueAiTurn(gameToken);
        }
    }

    private void queueAiTurn(final long token) {
        busy = true;
        updateHoverState();
        setStatus("AI is thinking...", "Scanning threats, traps, and center control.");

        pendingAiDelay = new PauseTransition(Duration.millis(650));
        pendingAiDelay.setOnFinished(event -> {
            if (token != gameToken || gameOver) {
                return;
            }

            final char[][] boardCopy = cloneBoard(board);
            final int[] heightsCopy = heights.clone();
            final AiDifficulty aiDifficulty = selectedDifficulty;
            final int searchDepth = chooseAiSearchDepth(heightsCopy, aiDifficulty);

            Task<AiChoice> aiTask = new Task<AiChoice>() {
                @Override
                protected AiChoice call() {
                    return chooseAiMove(boardCopy, heightsCopy, searchDepth, aiDifficulty);
                }
            };

            aiTask.setOnSucceeded(workerStateEvent -> {
                if (token != gameToken || gameOver) {
                    return;
                }

                AiChoice choice = aiTask.getValue();
                detailLabel.setText(
                    aiDifficulty.label + " searched " + searchDepth + " plies and lined up column " + (choice.column + 1) + "."
                );
                animateMove(choice.column, AI_MARK, token, new Runnable() {
                    @Override
                    public void run() {
                        afterMoveResolved(AI_MARK);
                    }
                });
            });

            aiTask.setOnFailed(workerStateEvent -> {
                if (token != gameToken) {
                    return;
                }
                busy = false;
                setStatus("AI move failed", "The search hit an unexpected issue. Start a new round and try again.");
                updateHoverState();
            });

            Thread aiThread = new Thread(aiTask, "connect-four-ai");
            aiThread.setDaemon(true);
            aiThread.start();
        });
        pendingAiDelay.play();
    }

    private void queueHumanMoveReview(
        final long token,
        final char[][] boardSnapshot,
        final int[] heightsSnapshot,
        final int chosenColumn,
        final int searchDepth
    ) {
        Task<MoveFeedback> reviewTask = new Task<MoveFeedback>() {
            @Override
            protected MoveFeedback call() {
                return analyzeHumanMove(boardSnapshot, heightsSnapshot, chosenColumn, searchDepth);
            }
        };

        reviewTask.setOnSucceeded(workerStateEvent -> {
            if (token != gameToken) {
                return;
            }
            recordMoveFeedback(token, chosenColumn, reviewTask.getValue());
        });

        reviewTask.setOnFailed(workerStateEvent -> {
            if (token != gameToken) {
                return;
            }
            clearPendingMovePopup(token, chosenColumn);
        });

        Thread reviewThread = new Thread(reviewTask, "connect-four-move-review");
        reviewThread.setDaemon(true);
        reviewThread.start();
    }

    private void animateMove(final int column, final char mark, final long token, final Runnable onFinished) {
        final int row = dropPiece(board, heights, column, mark);
        final Circle flyingDisc = createDiscNode(mark, false);
        final double startX = columnCenterX(column);
        final double startY = BOARD_Y - 42.0;
        final double endY = rowCenterY(row);

        flyingDisc.setCenterX(startX);
        flyingDisc.setCenterY(startY);
        animationLayer.getChildren().add(flyingDisc);

        Timeline dropAnimation = new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(flyingDisc.centerYProperty(), startY),
                new KeyValue(flyingDisc.scaleXProperty(), 0.94),
                new KeyValue(flyingDisc.scaleYProperty(), 0.94)
            ),
            new KeyFrame(
                Duration.millis(360 + row * 32L),
                new KeyValue(flyingDisc.centerYProperty(), endY, Interpolator.SPLINE(0.2, 0.95, 0.25, 1.0)),
                new KeyValue(flyingDisc.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(flyingDisc.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
            )
        );

        activeDropAnimation = dropAnimation;
        dropAnimation.setOnFinished(event -> {
            animationLayer.getChildren().remove(flyingDisc);
            activeDropAnimation = null;

            if (token != gameToken) {
                return;
            }

            applyDiscStyle(discViews[row][column], mark, false, false);
            updateWinOutlook();
            if (mark == HUMAN_MARK) {
                markMovePopupSettled(token, row, column);
            }
            if (isWinningMove(board, row, column, mark)) {
                gameOver = true;
                busy = false;
                winner = mark;
                highlightWinningCells(findWinningLine(board, row, column, mark), mark);
                if (mark == HUMAN_MARK) {
                    setStatus("You win!", "You connected four. Hit Play Again whenever you want another round.");
                } else {
                    setStatus("AI wins!", "It connected four cleanly. Hit Play Again to run it back.");
                }
                refreshRestartButton();
                updateWinOutlook();
                updateHoverState();
                return;
            }

            if (isBoardFull(heights)) {
                gameOver = true;
                busy = false;
                winner = EMPTY;
                setStatus("It's a draw", "The grid is packed. Hit Play Again to start a fresh game.");
                refreshRestartButton();
                updateWinOutlook();
                updateHoverState();
                return;
            }

            onFinished.run();
        });
        dropAnimation.play();
    }

    private void highlightWinningCells(List<CellRef> winningLine, char mark) {
        for (CellRef cell : winningLine) {
            applyDiscStyle(discViews[cell.row][cell.column], mark, false, true);
        }
    }

    private void updateHoverState() {
        boolean inputEnabled = isHumanInputAllowed();

        for (int column = 0; column < COLUMNS; column++) {
            boolean playable = !isColumnFull(heights, column);
            boolean active = inputEnabled && playable && hoveredColumn == column;
            hoverHighlights[column].setOpacity(active ? 0.92 : (playable && inputEnabled ? 0.08 : 0.02));
        }

        previewDisc.setVisible(inputEnabled && hoveredColumn >= 0 && !isColumnFull(heights, hoveredColumn));
        if (previewDisc.isVisible()) {
            previewDisc.setCenterX(columnCenterX(hoveredColumn));
            previewDisc.setCenterY(BOARD_Y - 34.0);
        }
    }

    private boolean isHumanInputAllowed() {
        return !busy && !gameOver && humanTurn;
    }

    private void refreshRestartButton() {
        if (restartButton != null) {
            restartButton.setText(gameOver ? "Play Again" : "New Game");
        }
    }

    private void updateWinOutlook() {
        double balance;

        if (gameOver) {
            if (winner == HUMAN_MARK) {
                balance = 0.0;
                favorLabel.setText("Final: You won");
            } else if (winner == AI_MARK) {
                balance = 1.0;
                favorLabel.setText("Final: AI won");
            } else {
                balance = 0.5;
                favorLabel.setText("Final: Drawn game");
            }
        } else {
            int score = evaluateBoard(board, heights);
            double normalizedScore = Math.tanh(score / 1700.0);
            balance = clamp(0.5 + normalizedScore * 0.5, 0.0, 1.0);
            favorLabel.setText(describeOutlook(balance));
        }

        animateFavorBalance(balance);
    }

    private void animateFavorBalance(double targetBalance) {
        double clampedTarget = clamp(targetBalance, 0.0, 1.0);

        if (activeFavorMeterAnimation != null) {
            activeFavorMeterAnimation.stop();
            activeFavorMeterAnimation = null;
        }

        if (Math.abs(favorBalance.get() - clampedTarget) < 0.0001) {
            favorBalance.set(clampedTarget);
            return;
        }

        Timeline meterAnimation = new Timeline(
            new KeyFrame(
                Duration.millis(560),
                new KeyValue(favorBalance, clampedTarget, OUTLOOK_METER_INTERPOLATOR)
            )
        );

        activeFavorMeterAnimation = meterAnimation;
        meterAnimation.setOnFinished(event -> {
            favorBalance.set(clampedTarget);
            if (activeFavorMeterAnimation == meterAnimation) {
                activeFavorMeterAnimation = null;
            }
        });
        meterAnimation.play();
    }

    private String describeOutlook(double balance) {
        double edge = Math.abs(balance - 0.5) * 2.0;

        if (edge < 0.08) {
            return "Dead even";
        }
        if (balance < 0.5) {
            if (edge < 0.28) {
                return "You have a slight edge";
            }
            if (edge < 0.58) {
                return "You are favored";
            }
            return "You are strongly favored";
        }
        if (edge < 0.28) {
            return "AI has a slight edge";
        }
        if (edge < 0.58) {
            return "AI is favored";
        }
        return "AI is strongly favored";
    }

    private void cancelPendingWork() {
        if (pendingAiDelay != null) {
            pendingAiDelay.stop();
            pendingAiDelay = null;
        }
        if (activeDropAnimation != null) {
            activeDropAnimation.stop();
            activeDropAnimation = null;
        }
        if (activeFavorMeterAnimation != null) {
            activeFavorMeterAnimation.stop();
            activeFavorMeterAnimation = null;
        }
        clearActiveMoveFeedbackPopup();
        pendingMovePopup = null;
        if (animationLayer != null) {
            animationLayer.getChildren().clear();
        }
    }

    private void loadStylesheet(Scene scene) {
        try {
            if (ConnectFourFX.class.getResource("connect-four.css") != null) {
                scene.getStylesheets().add(ConnectFourFX.class.getResource("connect-four.css").toExternalForm());
            } else if (Files.exists(Paths.get("connect-four.css"))) {
                scene.getStylesheets().add(Paths.get("connect-four.css").toUri().toString());
            }
        } catch (Exception ignored) {
            // The app still works without the stylesheet, but it looks better with it.
        }
    }

    private void setStatus(String headline, String details) {
        statusLabel.setText(headline);
        detailLabel.setText(details);
    }

    private void recordMoveFeedback(long token, int column, MoveFeedback feedback) {
        if (pendingMovePopup == null || pendingMovePopup.token != token || pendingMovePopup.column != column) {
            return;
        }

        pendingMovePopup.feedback = feedback;
        maybeShowPendingMovePopup();
    }

    private void clearPendingMovePopup(long token, int column) {
        if (pendingMovePopup == null || pendingMovePopup.token != token || pendingMovePopup.column != column) {
            return;
        }

        pendingMovePopup = null;
    }

    private void markMovePopupSettled(long token, int row, int column) {
        if (pendingMovePopup == null
            || pendingMovePopup.token != token
            || pendingMovePopup.row != row
            || pendingMovePopup.column != column) {
            return;
        }

        pendingMovePopup.discSettled = true;
        maybeShowPendingMovePopup();
    }

    private void maybeShowPendingMovePopup() {
        if (pendingMovePopup == null || pendingMovePopup.feedback == null || !pendingMovePopup.discSettled) {
            return;
        }

        MovePopup popup = pendingMovePopup;
        pendingMovePopup = null;
        showMoveFeedbackPopup(popup.feedback, popup.row, popup.column);
    }

    private void showMoveFeedbackPopup(MoveFeedback feedback, int row, int column) {
        if (animationLayer == null) {
            return;
        }

        clearActiveMoveFeedbackPopup();

        Label popup = new Label(feedback.headline);
        popup.getStyleClass().addAll("move-feedback-popup", feedback.styleClass);
        popup.setManaged(false);
        popup.setMouseTransparent(true);
        popup.setRotate(-35.0 + random.nextDouble() * 70.0);

        animationLayer.getChildren().add(popup);
        popup.applyCss();
        popup.autosize();

        double popupWidth = popup.prefWidth(-1);
        double popupHeight = popup.prefHeight(-1);
        double[] popupPosition = chooseMoveFeedbackPopupPosition(row, column, popupWidth, popupHeight);
        double x = popupPosition[0];
        double y = popupPosition[1];
        popup.relocate(x, y);
        popup.setOpacity(0.0);

        Timeline popupAnimation = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(popup.opacityProperty(), 0.0)),
            new KeyFrame(Duration.seconds(0.2), new KeyValue(popup.opacityProperty(), 1.0, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.seconds(1.7), new KeyValue(popup.opacityProperty(), 1.0)),
            new KeyFrame(Duration.seconds(2.2), new KeyValue(popup.opacityProperty(), 0.0, Interpolator.EASE_IN))
        );

        activeMoveFeedbackLabel = popup;
        activeMoveFeedbackAnimation = popupAnimation;
        popupAnimation.setOnFinished(event -> {
            if (animationLayer != null) {
                animationLayer.getChildren().remove(popup);
            }
            if (activeMoveFeedbackLabel == popup) {
                activeMoveFeedbackLabel = null;
            }
            if (activeMoveFeedbackAnimation == popupAnimation) {
                activeMoveFeedbackAnimation = null;
            }
        });
        popupAnimation.play();
    }

    private double[] chooseMoveFeedbackPopupPosition(int row, int column, double popupWidth, double popupHeight) {
        double centerX = columnCenterX(column);
        double centerY = rowCenterY(row);
        double minX = BOARD_X + 8.0;
        double maxX = BOARD_X + BOARD_WIDTH - popupWidth - 8.0;
        double minY = BOARD_Y + 8.0;
        double maxY = BOARD_Y + BOARD_HEIGHT - popupHeight - 8.0;
        double safetyRadius = DISC_RADIUS + 10.0;

        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = DISC_RADIUS + 26.0 + random.nextDouble() * 44.0;
            double candidateX = centerX + Math.cos(angle) * radius - popupWidth / 2.0;
            double candidateY = centerY + Math.sin(angle) * radius - popupHeight / 2.0;
            double x = clamp(candidateX, minX, maxX);
            double y = clamp(candidateY, minY, maxY);

            if (!doesPopupOverlapDisc(x, y, popupWidth, popupHeight, centerX, centerY, safetyRadius)) {
                return new double[] {x, y};
            }
        }

        double fallbackX = clamp(centerX + DISC_RADIUS + 28.0, minX, maxX);
        double fallbackY = clamp(centerY - popupHeight - DISC_RADIUS + 6.0, minY, maxY);
        return new double[] {fallbackX, fallbackY};
    }

    private boolean doesPopupOverlapDisc(
        double popupX,
        double popupY,
        double popupWidth,
        double popupHeight,
        double discCenterX,
        double discCenterY,
        double discRadius
    ) {
        double nearestX = clamp(discCenterX, popupX, popupX + popupWidth);
        double nearestY = clamp(discCenterY, popupY, popupY + popupHeight);
        double deltaX = discCenterX - nearestX;
        double deltaY = discCenterY - nearestY;
        return deltaX * deltaX + deltaY * deltaY < discRadius * discRadius;
    }

    private void clearActiveMoveFeedbackPopup() {
        if (activeMoveFeedbackAnimation != null) {
            activeMoveFeedbackAnimation.stop();
            activeMoveFeedbackAnimation = null;
        }

        if (activeMoveFeedbackLabel != null && animationLayer != null) {
            animationLayer.getChildren().remove(activeMoveFeedbackLabel);
        }
        activeMoveFeedbackLabel = null;
    }

    private double columnCenterX(int column) {
        return BOARD_X + column * CELL_SIZE + CELL_SIZE / 2.0;
    }

    private double rowCenterY(int row) {
        return BOARD_Y + row * CELL_SIZE + CELL_SIZE / 2.0;
    }

    private Circle createDiscNode(char mark, boolean preview) {
        Circle disc = new Circle(DISC_RADIUS);
        applyDiscStyle(disc, mark, preview, false);
        return disc;
    }

    private void applyDiscStyle(Circle disc, char mark, boolean preview, boolean winning) {
        if (mark == HUMAN_MARK) {
            disc.setFill(buildDiscGradient(
                preview ? 0.42 : 1.0,
                Color.web("#ffe9d7"),
                Color.web("#ff9d80"),
                Color.web("#ff6146"),
                Color.web("#8b1622")
            ));
            DropShadow shadow = new DropShadow(preview ? 18.0 : 24.0, Color.rgb(255, 98, 77, preview ? 0.30 : 0.52));
            shadow.setOffsetY(8.0);
            shadow.setSpread(0.20);
            disc.setEffect(winning
                ? buildWinnerEffect(
                    shadow,
                    Color.rgb(255, 246, 228, 0.94),
                    Color.rgb(255, 145, 102, 0.58)
                )
                : shadow
            );
            disc.setStroke(winning
                ? Color.rgb(255, 252, 243, 0.96)
                : Color.rgb(255, 244, 230, preview ? 0.34 : 0.76)
            );
            disc.setStrokeWidth(winning ? 4.4 : (preview ? 2.0 : 2.6));
        } else if (mark == AI_MARK) {
            disc.setFill(buildDiscGradient(
                preview ? 0.42 : 1.0,
                Color.web("#fff6cf"),
                Color.web("#ffdb7b"),
                Color.web("#f6b93a"),
                Color.web("#8c4f0c")
            ));
            DropShadow shadow = new DropShadow(preview ? 18.0 : 24.0, Color.rgb(255, 198, 72, preview ? 0.28 : 0.48));
            shadow.setOffsetY(8.0);
            shadow.setSpread(0.20);
            disc.setEffect(winning
                ? buildWinnerEffect(
                    shadow,
                    Color.rgb(255, 247, 216, 0.94),
                    Color.rgb(255, 209, 88, 0.60)
                )
                : shadow
            );
            disc.setStroke(winning
                ? Color.rgb(255, 252, 234, 0.96)
                : Color.rgb(255, 248, 220, preview ? 0.28 : 0.72)
            );
            disc.setStrokeWidth(winning ? 4.4 : (preview ? 2.0 : 2.6));
        } else {
            disc.setFill(new RadialGradient(
                0.0, 0.0, 0.35, 0.35, 0.8, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(59, 86, 166, 0.26)),
                new Stop(0.72, Color.rgb(11, 22, 61, 0.92)),
                new Stop(1.0, Color.rgb(4, 11, 29, 0.98))
            ));
            disc.setStroke(Color.rgb(143, 202, 255, 0.12));
            disc.setStrokeWidth(1.3);
            disc.setEffect(new DropShadow(12.0, Color.rgb(0, 0, 0, 0.32)));
        }
    }

    private Paint buildDiscGradient(double opacity, Color highlight, Color inner, Color mid, Color edge) {
        return new RadialGradient(
            0.0, 0.0, 0.34, 0.28, 0.86, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, highlight.deriveColor(0.0, 1.0, 1.0, opacity)),
            new Stop(0.16, inner.deriveColor(0.0, 1.0, 1.0, opacity)),
            new Stop(0.60, mid.deriveColor(0.0, 1.0, 1.0, opacity)),
            new Stop(1.0, edge.deriveColor(0.0, 1.0, 1.0, opacity))
        );
    }

    private DropShadow buildWinnerEffect(DropShadow baseShadow, Color ringColor, Color auraColor) {
        DropShadow ring = new DropShadow(18.0, ringColor);
        ring.setOffsetX(0.0);
        ring.setOffsetY(0.0);
        ring.setSpread(0.78);
        ring.setInput(baseShadow);

        DropShadow aura = new DropShadow(32.0, auraColor);
        aura.setOffsetX(0.0);
        aura.setOffsetY(0.0);
        aura.setSpread(0.34);
        aura.setInput(ring);
        return aura;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private MoveFeedback analyzeHumanMove(char[][] boardState, int[] columnHeights, int chosenColumn, int searchDepth) {
        List<Integer> playableColumns = getPlayableColumns(columnHeights);
        List<MoveOption> moveOptions = new ArrayList<>();

        for (int column : playableColumns) {
            moveOptions.add(analyzeHumanCandidateMove(boardState, columnHeights, column, searchDepth));
        }

        MoveOption bestMove = moveOptions.get(0);
        MoveOption worstMove = moveOptions.get(0);
        MoveOption chosenMove = moveOptions.get(0);
        int betterMoveCount = 0;

        for (MoveOption option : moveOptions) {
            if (isMoveBetter(option, bestMove)) {
                bestMove = option;
            }
            if (isMoveBetter(worstMove, option)) {
                worstMove = option;
            }
            if (option.column == chosenColumn) {
                chosenMove = option;
            }
        }

        for (MoveOption option : moveOptions) {
            if (isMoveBetter(option, chosenMove)) {
                betterMoveCount++;
            }
        }

        return describeHumanMove(
            chosenMove.score,
            bestMove.score,
            worstMove.score,
            betterMoveCount,
            playableColumns.size(),
            chosenMove.aiImmediateWinningReplies,
            bestMove.aiImmediateWinningReplies
        );
    }

    private MoveOption analyzeHumanCandidateMove(char[][] boardState, int[] columnHeights, int column, int searchDepth) {
        int row = dropPiece(boardState, columnHeights, column, HUMAN_MARK);
        int aiImmediateWinningReplies = getImmediateWinningMoves(boardState, columnHeights, AI_MARK).size();
        int score;

        if (isWinningMove(boardState, row, column, HUMAN_MARK)) {
            score = -MAX_SCORE - searchDepth;
        } else if (isBoardFull(columnHeights)) {
            score = 0;
        } else {
            score = minimax(boardState, columnHeights, searchDepth - 1, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        undoMove(boardState, columnHeights, column);
        return new MoveOption(column, score, aiImmediateWinningReplies);
    }

    private boolean isMoveBetter(MoveOption candidate, MoveOption reference) {
        if (candidate.score != reference.score) {
            return candidate.score < reference.score;
        }
        if (candidate.aiImmediateWinningReplies != reference.aiImmediateWinningReplies) {
            return candidate.aiImmediateWinningReplies < reference.aiImmediateWinningReplies;
        }
        return candidate.column < reference.column;
    }

    private MoveFeedback describeHumanMove(
        int chosenScore,
        int bestScore,
        int worstScore,
        int betterMoveCount,
        int legalMoveCount,
        int chosenAiImmediateWinningReplies,
        int bestAiImmediateWinningReplies
    ) {
        int rank = betterMoveCount + 1;
        int scoreLoss = Math.max(0, chosenScore - bestScore);
        int scoreRange = Math.max(0, worstScore - bestScore);
        double lossRatio = scoreRange == 0 ? 0.0 : (double) scoreLoss / (double) scoreRange;
        int nearWorstRank = Math.max(legalMoveCount - 1, 2);
        boolean nearBestMove = scoreLoss <= 180
            || lossRatio <= 0.20
            || (rank <= 2 && scoreLoss <= 320 && lossRatio <= 0.35);
        boolean nearWorstMove = rank >= nearWorstRank
            && (scoreLoss >= 1_200 || (lossRatio >= 0.88 && scoreRange >= 250));
        boolean hurtsPosition = lossRatio >= 0.60
            || scoreLoss >= 650
            || (rank >= nearWorstRank && scoreLoss >= 350);
        boolean allowsImmediateAiWin = chosenAiImmediateWinningReplies > 0;
        boolean bestMoveAvoidsImmediateAiWin = bestAiImmediateWinningReplies == 0;

        if (allowsImmediateAiWin) {
            if (bestMoveAvoidsImmediateAiWin || chosenAiImmediateWinningReplies > bestAiImmediateWinningReplies) {
                return new MoveFeedback(
                    "Blunder",
                    "grade-blunder"
                );
            }

            if (chosenScore == bestScore && chosenAiImmediateWinningReplies == bestAiImmediateWinningReplies) {
                return new MoveFeedback(
                    "Average",
                    "grade-average"
                );
            }

            if (nearWorstMove || hurtsPosition) {
                return new MoveFeedback(
                    "Blunder",
                    "grade-blunder"
                );
            }

            return new MoveFeedback(
                "Bad",
                "grade-bad"
            );
        }

        if (chosenScore == bestScore) {
            if (chosenScore <= -MAX_SCORE / 2) {
                return new MoveFeedback(
                    "Perfect",
                    "grade-perfect"
                );
            }
            return new MoveFeedback(
                "Perfect",
                "grade-perfect"
            );
        }

        if (chosenScore >= MAX_SCORE / 2 && bestScore < MAX_SCORE / 2) {
            return new MoveFeedback(
                "Blunder",
                "grade-blunder"
            );
        }

        if (bestScore <= -MAX_SCORE / 2 && chosenScore > -MAX_SCORE / 2) {
            return new MoveFeedback(
                "Blunder",
                "grade-blunder"
            );
        }

        if (nearBestMove) {
            return new MoveFeedback(
                "Amazing",
                "grade-amazing"
            );
        }

        if (nearWorstMove) {
            return new MoveFeedback(
                "Blunder",
                "grade-blunder"
            );
        }

        if (hurtsPosition) {
            return new MoveFeedback(
                "Bad",
                "grade-bad"
            );
        }

        return new MoveFeedback(
            "Average",
            "grade-average"
        );
    }

    private int chooseAiSearchDepth(int[] columnHeights, AiDifficulty difficulty) {
        int baseDepth = chooseSearchDepth(columnHeights);
        return Math.max(difficulty.minimumSearchDepth, baseDepth + difficulty.depthOffset);
    }

    private AiChoice chooseAiMove(char[][] boardState, int[] columnHeights, int searchDepth, AiDifficulty difficulty) {
        List<Integer> aiWinningMoves = getImmediateWinningMoves(boardState, columnHeights, AI_MARK);
        if (!aiWinningMoves.isEmpty()) {
            return new AiChoice(aiWinningMoves.get(0).intValue(), MAX_SCORE);
        }

        List<Integer> humanWinningMoves = getImmediateWinningMoves(boardState, columnHeights, HUMAN_MARK);
        if (humanWinningMoves.size() == 1) {
            int blockingColumn = humanWinningMoves.get(0).intValue();
            if (difficulty.blockChance >= 1.0 || random.nextDouble() < difficulty.blockChance) {
                return new AiChoice(blockingColumn, 0);
            }
            return chooseImperfectAiMove(boardState, columnHeights, searchDepth, difficulty, blockingColumn);
        }

        if (difficulty == AiDifficulty.IMPOSSIBLE || difficulty == AiDifficulty.HARD) {
            return chooseBestMove(boardState, columnHeights, searchDepth);
        }

        return chooseImperfectAiMove(boardState, columnHeights, searchDepth, difficulty, -1);
    }

    private AiChoice chooseImperfectAiMove(
        char[][] boardState,
        int[] columnHeights,
        int searchDepth,
        AiDifficulty difficulty,
        int excludedColumn
    ) {
        List<AiChoice> rankedMoves = evaluateAiMoves(boardState, columnHeights, searchDepth, excludedColumn);
        if (rankedMoves.isEmpty()) {
            rankedMoves = evaluateAiMoves(boardState, columnHeights, searchDepth, -1);
        }
        if (rankedMoves.isEmpty() || rankedMoves.size() == 1 || difficulty.bestMoveChance >= 1.0) {
            return rankedMoves.get(0);
        }

        int shortlistSize = Math.min(difficulty.candidateWindow, rankedMoves.size());
        if (random.nextDouble() < difficulty.bestMoveChance) {
            return rankedMoves.get(0);
        }

        return rankedMoves.get(1 + random.nextInt(shortlistSize - 1));
    }

    private List<AiChoice> evaluateAiMoves(
        char[][] boardState,
        int[] columnHeights,
        int searchDepth,
        int excludedColumn
    ) {
        List<AiChoice> choices = new ArrayList<AiChoice>();

        for (int column : getPlayableColumns(columnHeights)) {
            if (column == excludedColumn) {
                continue;
            }

            int row = dropPiece(boardState, columnHeights, column, AI_MARK);
            int score;

            if (isWinningMove(boardState, row, column, AI_MARK)) {
                score = MAX_SCORE;
            } else if (isBoardFull(columnHeights)) {
                score = 0;
            } else {
                score = minimax(boardState, columnHeights, searchDepth - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
            }

            undoMove(boardState, columnHeights, column);
            choices.add(new AiChoice(column, score));
        }

        choices.sort((left, right) -> {
            if (left.score != right.score) {
                return Integer.compare(right.score, left.score);
            }
            return Integer.compare(columnPreferenceIndex(left.column), columnPreferenceIndex(right.column));
        });
        return choices;
    }

    private int columnPreferenceIndex(int column) {
        for (int index = 0; index < COLUMN_ORDER.length; index++) {
            if (COLUMN_ORDER[index] == column) {
                return index;
            }
        }
        return COLUMN_ORDER.length;
    }

    private AiChoice chooseBestMove(char[][] boardState, int[] columnHeights, int searchDepth) {
        List<Integer> aiWinningMoves = getImmediateWinningMoves(boardState, columnHeights, AI_MARK);
        if (!aiWinningMoves.isEmpty()) {
            return new AiChoice(aiWinningMoves.get(0).intValue(), MAX_SCORE);
        }

        List<Integer> humanWinningMoves = getImmediateWinningMoves(boardState, columnHeights, HUMAN_MARK);
        if (humanWinningMoves.size() == 1) {
            return new AiChoice(humanWinningMoves.get(0), 0);
        }

        int bestColumn = -1;
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (int column : getPlayableColumns(columnHeights)) {
            int row = dropPiece(boardState, columnHeights, column, AI_MARK);
            int score;

            if (isWinningMove(boardState, row, column, AI_MARK)) {
                score = MAX_SCORE;
            } else if (isBoardFull(columnHeights)) {
                score = 0;
            } else {
                score = minimax(boardState, columnHeights, searchDepth - 1, false, alpha, beta);
            }

            undoMove(boardState, columnHeights, column);

            if (bestColumn == -1 || score > bestScore) {
                bestColumn = column;
                bestScore = score;
            }

            if (score > alpha) {
                alpha = score;
            }
        }

        return new AiChoice(bestColumn, bestScore);
    }

    private int minimax(char[][] boardState, int[] columnHeights, int depth, boolean aiTurn, int alpha, int beta) {
        if (depth == 0 || isBoardFull(columnHeights)) {
            return evaluateBoard(boardState, columnHeights);
        }

        if (aiTurn) {
            int bestScore = Integer.MIN_VALUE;

            for (int column : getPlayableColumns(columnHeights)) {
                int row = dropPiece(boardState, columnHeights, column, AI_MARK);
                int score;

                if (isWinningMove(boardState, row, column, AI_MARK)) {
                    score = MAX_SCORE + depth;
                } else if (isBoardFull(columnHeights)) {
                    score = 0;
                } else {
                    score = minimax(boardState, columnHeights, depth - 1, false, alpha, beta);
                }

                undoMove(boardState, columnHeights, column);

                if (score > bestScore) {
                    bestScore = score;
                }
                if (score > alpha) {
                    alpha = score;
                }
                if (alpha >= beta) {
                    break;
                }
            }

            return bestScore;
        }

        int bestScore = Integer.MAX_VALUE;

        for (int column : getPlayableColumns(columnHeights)) {
            int row = dropPiece(boardState, columnHeights, column, HUMAN_MARK);
            int score;

            if (isWinningMove(boardState, row, column, HUMAN_MARK)) {
                score = -MAX_SCORE - depth;
            } else if (isBoardFull(columnHeights)) {
                score = 0;
            } else {
                score = minimax(boardState, columnHeights, depth - 1, true, alpha, beta);
            }

            undoMove(boardState, columnHeights, column);

            if (score < bestScore) {
                bestScore = score;
            }
            if (score < beta) {
                beta = score;
            }
            if (alpha >= beta) {
                break;
            }
        }

        return bestScore;
    }

    private int evaluateBoard(char[][] boardState, int[] columnHeights) {
        int score = 0;

        for (int row = 0; row < ROWS; row++) {
            if (boardState[row][COLUMNS / 2] == AI_MARK) {
                score += 6;
            } else if (boardState[row][COLUMNS / 2] == HUMAN_MARK) {
                score -= 6;
            }
        }

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column <= COLUMNS - WIN_LENGTH; column++) {
                score += evaluateWindow(
                    boardState[row][column],
                    boardState[row][column + 1],
                    boardState[row][column + 2],
                    boardState[row][column + 3]
                );
            }
        }

        for (int column = 0; column < COLUMNS; column++) {
            for (int row = 0; row <= ROWS - WIN_LENGTH; row++) {
                score += evaluateWindow(
                    boardState[row][column],
                    boardState[row + 1][column],
                    boardState[row + 2][column],
                    boardState[row + 3][column]
                );
            }
        }

        for (int row = 0; row <= ROWS - WIN_LENGTH; row++) {
            for (int column = 0; column <= COLUMNS - WIN_LENGTH; column++) {
                score += evaluateWindow(
                    boardState[row][column],
                    boardState[row + 1][column + 1],
                    boardState[row + 2][column + 2],
                    boardState[row + 3][column + 3]
                );
            }
        }

        for (int row = 0; row <= ROWS - WIN_LENGTH; row++) {
            for (int column = WIN_LENGTH - 1; column < COLUMNS; column++) {
                score += evaluateWindow(
                    boardState[row][column],
                    boardState[row + 1][column - 1],
                    boardState[row + 2][column - 2],
                    boardState[row + 3][column - 3]
                );
            }
        }

        score += getImmediateWinningMoves(boardState, columnHeights, AI_MARK).size() * 900;
        score -= getImmediateWinningMoves(boardState, columnHeights, HUMAN_MARK).size() * 1_000;

        return score;
    }

    private int evaluateWindow(char a, char b, char c, char d) {
        int aiCount = 0;
        int humanCount = 0;
        int emptyCount = 0;

        char[] window = {a, b, c, d};
        for (char cell : window) {
            if (cell == AI_MARK) {
                aiCount++;
            } else if (cell == HUMAN_MARK) {
                humanCount++;
            } else {
                emptyCount++;
            }
        }

        if (aiCount > 0 && humanCount > 0) {
            return 0;
        }
        if (aiCount == 4) {
            return 100_000;
        }
        if (humanCount == 4) {
            return -100_000;
        }
        if (aiCount == 3 && emptyCount == 1) {
            return 500;
        }
        if (humanCount == 3 && emptyCount == 1) {
            return -650;
        }
        if (aiCount == 2 && emptyCount == 2) {
            return 50;
        }
        if (humanCount == 2 && emptyCount == 2) {
            return -60;
        }
        if (aiCount == 1 && emptyCount == 3) {
            return 5;
        }
        if (humanCount == 1 && emptyCount == 3) {
            return -5;
        }

        return 0;
    }

    private List<Integer> getImmediateWinningMoves(char[][] boardState, int[] columnHeights, char mark) {
        List<Integer> winningMoves = new ArrayList<Integer>();

        for (int column : getPlayableColumns(columnHeights)) {
            int row = dropPiece(boardState, columnHeights, column, mark);
            if (isWinningMove(boardState, row, column, mark)) {
                winningMoves.add(Integer.valueOf(column));
            }
            undoMove(boardState, columnHeights, column);
        }

        return winningMoves;
    }

    private List<Integer> getPlayableColumns(int[] columnHeights) {
        List<Integer> columns = new ArrayList<Integer>();
        for (int column : COLUMN_ORDER) {
            if (!isColumnFull(columnHeights, column)) {
                columns.add(Integer.valueOf(column));
            }
        }
        return columns;
    }

    private int chooseSearchDepth(int[] columnHeights) {
        int remainingMoves = ROWS * COLUMNS;
        for (int height : columnHeights) {
            remainingMoves -= height;
        }

        if (remainingMoves >= 30) {
            return 6;
        }
        if (remainingMoves >= 22) {
            return 7;
        }
        if (remainingMoves >= 14) {
            return 8;
        }
        return 9;
    }

    private int dropPiece(char[][] boardState, int[] columnHeights, int column, char mark) {
        int row = ROWS - 1 - columnHeights[column];
        boardState[row][column] = mark;
        columnHeights[column]++;
        return row;
    }

    private void undoMove(char[][] boardState, int[] columnHeights, int column) {
        columnHeights[column]--;
        int row = ROWS - 1 - columnHeights[column];
        boardState[row][column] = EMPTY;
    }

    private boolean isColumnFull(int[] columnHeights, int column) {
        return columnHeights[column] >= ROWS;
    }

    private boolean isBoardFull(int[] columnHeights) {
        for (int column = 0; column < COLUMNS; column++) {
            if (!isColumnFull(columnHeights, column)) {
                return false;
            }
        }
        return true;
    }

    private boolean isWinningMove(char[][] boardState, int row, int column, char mark) {
        return countConnected(boardState, row, column, 0, 1, mark) >= WIN_LENGTH
            || countConnected(boardState, row, column, 1, 0, mark) >= WIN_LENGTH
            || countConnected(boardState, row, column, 1, 1, mark) >= WIN_LENGTH
            || countConnected(boardState, row, column, 1, -1, mark) >= WIN_LENGTH;
    }

    private List<CellRef> findWinningLine(char[][] boardState, int row, int column, char mark) {
        List<CellRef> line = collectWinningLine(boardState, row, column, 0, 1, mark);
        if (!line.isEmpty()) {
            return line;
        }

        line = collectWinningLine(boardState, row, column, 1, 0, mark);
        if (!line.isEmpty()) {
            return line;
        }

        line = collectWinningLine(boardState, row, column, 1, 1, mark);
        if (!line.isEmpty()) {
            return line;
        }

        return collectWinningLine(boardState, row, column, 1, -1, mark);
    }

    private List<CellRef> collectWinningLine(char[][] boardState, int row, int column, int rowStep, int columnStep, char mark) {
        List<CellRef> cells = new ArrayList<CellRef>();
        collectDirection(boardState, row, column, -rowStep, -columnStep, mark, cells, true);
        collectDirection(boardState, row, column, rowStep, columnStep, mark, cells, false);

        if (cells.size() >= WIN_LENGTH) {
            return cells;
        }
        return new ArrayList<CellRef>();
    }

    private void collectDirection(
        char[][] boardState,
        int row,
        int column,
        int rowStep,
        int columnStep,
        char mark,
        List<CellRef> cells,
        boolean prepend
    ) {
        int currentRow = row;
        int currentColumn = column;

        if (!prepend) {
            currentRow += rowStep;
            currentColumn += columnStep;
        }

        while (currentRow >= 0 && currentRow < ROWS && currentColumn >= 0 && currentColumn < COLUMNS) {
            if (boardState[currentRow][currentColumn] != mark) {
                break;
            }

            CellRef cell = new CellRef(currentRow, currentColumn);
            if (prepend) {
                cells.add(0, cell);
            } else {
                cells.add(cell);
            }
            currentRow += rowStep;
            currentColumn += columnStep;
        }
    }

    private int countConnected(char[][] boardState, int row, int column, int rowStep, int columnStep, char mark) {
        int total = 1;
        total += countOneDirection(boardState, row, column, rowStep, columnStep, mark);
        total += countOneDirection(boardState, row, column, -rowStep, -columnStep, mark);
        return total;
    }

    private int countOneDirection(char[][] boardState, int row, int column, int rowStep, int columnStep, char mark) {
        int count = 0;
        int currentRow = row + rowStep;
        int currentColumn = column + columnStep;

        while (currentRow >= 0 && currentRow < ROWS && currentColumn >= 0 && currentColumn < COLUMNS) {
            if (boardState[currentRow][currentColumn] != mark) {
                break;
            }
            count++;
            currentRow += rowStep;
            currentColumn += columnStep;
        }

        return count;
    }

    private char[][] cloneBoard(char[][] source) {
        char[][] copy = new char[ROWS][COLUMNS];
        for (int row = 0; row < ROWS; row++) {
            System.arraycopy(source[row], 0, copy[row], 0, COLUMNS);
        }
        return copy;
    }

    private enum AiDifficulty {
        EASY("Easy", 0.75, -3, 3, 0.52, 4),
        MEDIUM("Medium", 0.90, -2, 4, 0.78, 3),
        HARD("Hard", 1.00, -1, 5, 1.00, 1),
        IMPOSSIBLE("Impossible", 1.00, 0, 6, 1.00, 1);

        private final String label;
        private final double blockChance;
        private final int depthOffset;
        private final int minimumSearchDepth;
        private final double bestMoveChance;
        private final int candidateWindow;

        private AiDifficulty(
            String label,
            double blockChance,
            int depthOffset,
            int minimumSearchDepth,
            double bestMoveChance,
            int candidateWindow
        ) {
            this.label = label;
            this.blockChance = blockChance;
            this.depthOffset = depthOffset;
            this.minimumSearchDepth = minimumSearchDepth;
            this.bestMoveChance = bestMoveChance;
            this.candidateWindow = candidateWindow;
        }
    }

    private static class AiChoice {
        private final int column;
        private final int score;

        private AiChoice(int column, int score) {
            this.column = column;
            this.score = score;
        }
    }

    private static class MoveOption {
        private final int column;
        private final int score;
        private final int aiImmediateWinningReplies;

        private MoveOption(int column, int score, int aiImmediateWinningReplies) {
            this.column = column;
            this.score = score;
            this.aiImmediateWinningReplies = aiImmediateWinningReplies;
        }
    }

    private static class MovePopup {
        private final long token;
        private final int row;
        private final int column;
        private boolean discSettled;
        private MoveFeedback feedback;

        private MovePopup(long token, int row, int column) {
            this.token = token;
            this.row = row;
            this.column = column;
        }
    }

    private static class MoveFeedback {
        private final String headline;
        private final String styleClass;

        private MoveFeedback(String headline, String styleClass) {
            this.headline = headline;
            this.styleClass = styleClass;
        }
    }

    private static class CellRef {
        private final int row;
        private final int column;

        private CellRef(int row, int column) {
            this.row = row;
            this.column = column;
        }
    }
}
