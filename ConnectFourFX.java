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

    private final Circle[][] discViews = new Circle[ROWS][COLUMNS];
    private final Rectangle[] hoverHighlights = new Rectangle[COLUMNS];

    private Pane boardSurface;
    private Pane animationLayer;
    private Circle previewDisc;
    private Label statusLabel;
    private Label detailLabel;
    private Label favorLabel;
    private Button restartButton;

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
        layout.setPadding(new Insets(28, 34, 32, 34));
        layout.setFillWidth(true);

        VBox topSection = new VBox(18, buildHeader(), buildStatusCard());
        topSection.setAlignment(Pos.CENTER);
        topSection.setMaxWidth(Double.MAX_VALUE);

        StackPane boardCard = new StackPane(buildBoardView());
        boardCard.getStyleClass().add("glass-card");
        boardCard.setPadding(new Insets(22, 24, 22, 24));
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

        Circle leftGlow = new Circle(210, Color.rgb(73, 226, 255, 0.18));
        leftGlow.setTranslateX(-340);
        leftGlow.setTranslateY(-220);
        leftGlow.setEffect(new GaussianBlur(140));

        Circle rightGlow = new Circle(240, Color.rgb(255, 151, 79, 0.22));
        rightGlow.setTranslateX(360);
        rightGlow.setTranslateY(-140);
        rightGlow.setEffect(new GaussianBlur(160));

        Circle lowerGlow = new Circle(280, Color.rgb(49, 94, 255, 0.16));
        lowerGlow.setTranslateX(-120);
        lowerGlow.setTranslateY(280);
        lowerGlow.setEffect(new GaussianBlur(180));

        atmosphere.getChildren().addAll(leftGlow, rightGlow, lowerGlow);
        return atmosphere;
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("ARCADE DESKTOP EDITION");
        eyebrow.getStyleClass().add("eyebrow-label");

        Label title = new Label("Connect Four");
        title.setFont(Font.font("Trebuchet MS", FontWeight.EXTRA_BOLD, 40));
        title.getStyleClass().add("title-label");

        Label subtitle = new Label("Click a column, watch the disc fall, and try to outplay the AI.");
        subtitle.getStyleClass().add("subtitle-label");

        VBox header = new VBox(8, eyebrow, title, subtitle);
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
            new Stop(0.0, Color.web("#f4684d")),
            new Stop(0.46, Color.web("#17356f")),
            new Stop(0.54, Color.web("#17356f")),
            new Stop(1.0, Color.web("#f4b327"))
        ));
        meterTrack.setStroke(Color.rgb(210, 236, 255, 0.20));
        meterTrack.setStrokeWidth(1.1);

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
        centerMarker.setFill(Color.rgb(255, 255, 255, 0.56));
        centerMarker.setMouseTransparent(true);

        Rectangle favorIndicator = new Rectangle(9.0, 32.0);
        favorIndicator.setArcWidth(9.0);
        favorIndicator.setArcHeight(9.0);
        favorIndicator.setFill(Color.rgb(245, 250, 255, 0.96));
        favorIndicator.setStroke(Color.rgb(12, 30, 72, 0.46));
        favorIndicator.setStrokeWidth(1.0);
        favorIndicator.setEffect(new DropShadow(10.0, Color.rgb(0, 0, 0, 0.28)));
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
        legend.setAlignment(Pos.CENTER);
        legend.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(8, statusLabel, detailLabel, favorHeading, meterPane, legend);
        card.getStyleClass().add("glass-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(18, 24, 18, 24));
        card.setMaxWidth(620);

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
        restartButton = new Button();
        restartButton.getStyleClass().add("primary-button");
        restartButton.setOnAction(event -> startNewGame());
        refreshRestartButton();

        FlowPane controls = new FlowPane(14, 12, restartButton);
        controls.setAlignment(Pos.CENTER);
        controls.setRowValignment(javafx.geometry.VPos.CENTER);
        controls.setMaxWidth(Double.MAX_VALUE);
        return controls;
    }

    private StackPane buildBoardView() {
        boardSurface = new Pane();
        boardSurface.setPrefSize(SURFACE_WIDTH, SURFACE_HEIGHT);

        Rectangle boardAura = new Rectangle(BOARD_X - 14, BOARD_Y - 14, BOARD_WIDTH + 28, BOARD_HEIGHT + 28);
        boardAura.setArcWidth(54);
        boardAura.setArcHeight(54);
        boardAura.setFill(Color.rgb(61, 168, 255, 0.16));
        boardAura.setEffect(new GaussianBlur(28));

        Rectangle boardBody = new Rectangle(BOARD_X, BOARD_Y, BOARD_WIDTH, BOARD_HEIGHT);
        boardBody.setArcWidth(44);
        boardBody.setArcHeight(44);
        boardBody.setFill(new LinearGradient(
            0.0, 0.0, 1.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#2f6fff")),
            new Stop(0.55, Color.web("#1744b3")),
            new Stop(1.0, Color.web("#102971"))
        ));
        boardBody.setStroke(Color.rgb(194, 233, 255, 0.32));
        boardBody.setStrokeWidth(2.0);
        boardBody.setEffect(new DropShadow(26, Color.rgb(7, 12, 34, 0.42)));

        Rectangle boardGloss = new Rectangle(BOARD_X + 10, BOARD_Y + 10, BOARD_WIDTH - 20, 98);
        boardGloss.setArcWidth(34);
        boardGloss.setArcHeight(34);
        boardGloss.setFill(new LinearGradient(
            0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.22)),
            new Stop(1.0, Color.rgb(255, 255, 255, 0.0))
        ));

        boardSurface.getChildren().addAll(boardAura, boardBody, boardGloss);

        buildColumnChrome();
        buildSlots();

        previewDisc = createDiscNode(HUMAN_MARK, true);
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
            label.setLayoutX(x - 6);
            label.setLayoutY(22);

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
                new Stop(0.0, Color.rgb(255, 255, 255, 0.18)),
                new Stop(1.0, Color.rgb(255, 255, 255, 0.03))
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
                bezel.setFill(Color.rgb(7, 18, 56, 0.38));
                bezel.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.28)));

                Circle disc = new Circle(DISC_RADIUS);
                disc.setCenterX(centerX);
                disc.setCenterY(centerY);
                discViews[row][column] = disc;
                applyDiscStyle(disc, EMPTY, false, false);

                Circle sparkle = new Circle(DISC_RADIUS - 18.0);
                sparkle.setCenterX(centerX - 10.0);
                sparkle.setCenterY(centerY - 10.0);
                sparkle.setFill(Color.rgb(255, 255, 255, 0.09));
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

        busy = true;
        hoveredColumn = column;
        updateHoverState();
        setStatus("You played column " + (column + 1), "Dropping your disc into the board.");

        animateMove(column, HUMAN_MARK, gameToken, new Runnable() {
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
            final int searchDepth = chooseSearchDepth(heightsCopy);

            Task<AiChoice> aiTask = new Task<AiChoice>() {
                @Override
                protected AiChoice call() {
                    return chooseBestMove(boardCopy, heightsCopy, searchDepth);
                }
            };

            aiTask.setOnSucceeded(workerStateEvent -> {
                if (token != gameToken || gameOver) {
                    return;
                }

                AiChoice choice = aiTask.getValue();
                detailLabel.setText(
                    "AI searched " + searchDepth + " plies and lined up column " + (choice.column + 1) + "."
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
            hoverHighlights[column].setOpacity(active ? 1.0 : (playable && inputEnabled ? 0.10 : 0.03));
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

        favorBalance.set(balance);
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
                Color.web("#fff2cb"),
                Color.web("#ff9570"),
                Color.web("#ed4f39"),
                Color.web("#9b1e22")
            ));
            DropShadow shadow = new DropShadow(preview ? 18.0 : 24.0, Color.rgb(255, 88, 68, preview ? 0.28 : 0.48));
            shadow.setOffsetY(8.0);
            shadow.setSpread(0.18);
            disc.setEffect(winning
                ? buildWinnerEffect(
                    shadow,
                    Color.rgb(255, 247, 221, 0.92),
                    Color.rgb(255, 171, 119, 0.56)
                )
                : shadow
            );
            disc.setStroke(winning
                ? Color.rgb(255, 252, 241, 0.96)
                : Color.rgb(255, 248, 231, preview ? 0.30 : 0.72)
            );
            disc.setStrokeWidth(winning ? 4.4 : (preview ? 2.0 : 2.6));
        } else if (mark == AI_MARK) {
            disc.setFill(buildDiscGradient(
                preview ? 0.42 : 1.0,
                Color.web("#fff7b5"),
                Color.web("#ffd766"),
                Color.web("#f8b21d"),
                Color.web("#a95f09")
            ));
            DropShadow shadow = new DropShadow(preview ? 18.0 : 24.0, Color.rgb(255, 196, 56, preview ? 0.26 : 0.44));
            shadow.setOffsetY(8.0);
            shadow.setSpread(0.18);
            disc.setEffect(winning
                ? buildWinnerEffect(
                    shadow,
                    Color.rgb(255, 248, 210, 0.92),
                    Color.rgb(255, 220, 94, 0.58)
                )
                : shadow
            );
            disc.setStroke(winning
                ? Color.rgb(255, 252, 232, 0.96)
                : Color.rgb(255, 252, 232, preview ? 0.24 : 0.68)
            );
            disc.setStrokeWidth(winning ? 4.4 : (preview ? 2.0 : 2.6));
        } else {
            disc.setFill(new RadialGradient(
                0.0, 0.0, 0.35, 0.35, 0.8, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(36, 63, 139, 0.34)),
                new Stop(0.75, Color.rgb(11, 26, 74, 0.84)),
                new Stop(1.0, Color.rgb(5, 14, 44, 0.96))
            ));
            disc.setStroke(Color.rgb(126, 191, 255, 0.13));
            disc.setStrokeWidth(1.5);
            disc.setEffect(new DropShadow(12.0, Color.rgb(0, 0, 0, 0.26)));
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

    private AiChoice chooseBestMove(char[][] boardState, int[] columnHeights, int searchDepth) {
        List<Integer> aiWinningMoves = getImmediateWinningMoves(boardState, columnHeights, AI_MARK);
        if (!aiWinningMoves.isEmpty()) {
            return new AiChoice(aiWinningMoves.get(0).intValue(), MAX_SCORE);
        }

        List<Integer> humanWinningMoves = getImmediateWinningMoves(boardState, columnHeights, HUMAN_MARK);
        if (humanWinningMoves.size() == 1) {
            return new AiChoice(humanWinningMoves.get(0).intValue(), 0);
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

    private static class AiChoice {
        private final int column;
        private final int score;

        private AiChoice(int column, int score) {
            this.column = column;
            this.score = score;
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
