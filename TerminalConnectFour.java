import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class TerminalConnectFour {
    private static final int ROWS = 6;
    private static final int COLUMNS = 7;
    private static final int WIN_LENGTH = 4;
    private static final int MAX_SCORE = 1_000_000;
    private static final int[] COLUMN_ORDER = {3, 2, 4, 1, 5, 0, 6};

    private static final char EMPTY = ' ';
    private static final char HUMAN_MARK = 'X';
    private static final char AI_MARK = 'O';

    private final Scanner scanner = new Scanner(System.in);
    private final Random random = new Random();

    public static void main(String[] args) {
        new TerminalConnectFour().play();
    }

    private void play() {
        char[][] board = new char[ROWS][COLUMNS];
        int[] heights = new int[COLUMNS];

        initializeBoard(board);
        printTitle();
        System.out.println("You are X. The AI is O.");
        System.out.println("Pick a column from 1 to 7 and the checker will fall to the lowest open spot.");
        System.out.println();

        boolean humanTurn = random.nextBoolean();
        System.out.println(humanTurn ? "Random start: You go first." : "Random start: The AI goes first.");
        System.out.println();

        while (true) {
            renderBoard(board);

            int column;
            int row;
            char currentMark;

            if (humanTurn) {
                column = promptForHumanMove(heights);
                row = dropPiece(board, heights, column, HUMAN_MARK);
                currentMark = HUMAN_MARK;
            } else {
                int searchDepth = chooseSearchDepth(heights);
                AiChoice aiChoice = chooseBestMove(board, heights, searchDepth);
                column = aiChoice.column;
                row = dropPiece(board, heights, column, AI_MARK);
                currentMark = AI_MARK;

                System.out.println(
                    "AI dropped into column " + (column + 1)
                    + " after searching " + searchDepth + " plies."
                );
                System.out.println("AI outlook score: " + describeScore(aiChoice.score));
                System.out.println();
            }

            if (isWinningMove(board, row, column, currentMark)) {
                renderBoard(board);
                if (currentMark == HUMAN_MARK) {
                    System.out.println("You win!");
                } else {
                    System.out.println("The AI wins!");
                }
                break;
            }

            if (isBoardFull(heights)) {
                renderBoard(board);
                System.out.println("It's a draw!");
                break;
            }

            humanTurn = !humanTurn;
        }
    }

    private void initializeBoard(char[][] board) {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                board[row][column] = EMPTY;
            }
        }
    }

    private int promptForHumanMove(int[] heights) {
        while (true) {
            System.out.print("Choose a column (1-7): ");
            String input = scanner.nextLine().trim();

            int columnNumber;
            try {
                columnNumber = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number from 1 to 7.");
                continue;
            }

            if (columnNumber < 1 || columnNumber > COLUMNS) {
                System.out.println("Please enter a whole number from 1 to 7.");
                continue;
            }

            int column = columnNumber - 1;
            if (isColumnFull(heights, column)) {
                System.out.println("That column is full. Pick another one.");
                continue;
            }

            System.out.println();
            return column;
        }
    }

    private AiChoice chooseBestMove(char[][] board, int[] heights, int searchDepth) {
        List<Integer> aiWinningMoves = getImmediateWinningMoves(board, heights, AI_MARK);
        if (!aiWinningMoves.isEmpty()) {
            return new AiChoice(aiWinningMoves.get(0).intValue(), MAX_SCORE);
        }

        List<Integer> humanWinningMoves = getImmediateWinningMoves(board, heights, HUMAN_MARK);
        if (humanWinningMoves.size() == 1) {
            return new AiChoice(humanWinningMoves.get(0).intValue(), 0);
        }

        int bestColumn = -1;
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (int column : getPlayableColumns(heights)) {
            int row = dropPiece(board, heights, column, AI_MARK);
            int score;

            if (isWinningMove(board, row, column, AI_MARK)) {
                score = MAX_SCORE;
            } else if (isBoardFull(heights)) {
                score = 0;
            } else {
                score = minimax(board, heights, searchDepth - 1, false, alpha, beta);
            }

            undoMove(board, heights, column);

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

    private int minimax(char[][] board, int[] heights, int depth, boolean aiTurn, int alpha, int beta) {
        if (depth == 0 || isBoardFull(heights)) {
            return evaluateBoard(board, heights);
        }

        if (aiTurn) {
            int bestScore = Integer.MIN_VALUE;

            for (int column : getPlayableColumns(heights)) {
                int row = dropPiece(board, heights, column, AI_MARK);
                int score;

                if (isWinningMove(board, row, column, AI_MARK)) {
                    score = MAX_SCORE + depth;
                } else if (isBoardFull(heights)) {
                    score = 0;
                } else {
                    score = minimax(board, heights, depth - 1, false, alpha, beta);
                }

                undoMove(board, heights, column);

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

        for (int column : getPlayableColumns(heights)) {
            int row = dropPiece(board, heights, column, HUMAN_MARK);
            int score;

            if (isWinningMove(board, row, column, HUMAN_MARK)) {
                score = -MAX_SCORE - depth;
            } else if (isBoardFull(heights)) {
                score = 0;
            } else {
                score = minimax(board, heights, depth - 1, true, alpha, beta);
            }

            undoMove(board, heights, column);

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

    private int evaluateBoard(char[][] board, int[] heights) {
        int score = 0;

        for (int row = 0; row < ROWS; row++) {
            if (board[row][COLUMNS / 2] == AI_MARK) {
                score += 6;
            } else if (board[row][COLUMNS / 2] == HUMAN_MARK) {
                score -= 6;
            }
        }

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column <= COLUMNS - WIN_LENGTH; column++) {
                score += evaluateWindow(
                    board[row][column],
                    board[row][column + 1],
                    board[row][column + 2],
                    board[row][column + 3]
                );
            }
        }

        for (int column = 0; column < COLUMNS; column++) {
            for (int row = 0; row <= ROWS - WIN_LENGTH; row++) {
                score += evaluateWindow(
                    board[row][column],
                    board[row + 1][column],
                    board[row + 2][column],
                    board[row + 3][column]
                );
            }
        }

        for (int row = 0; row <= ROWS - WIN_LENGTH; row++) {
            for (int column = 0; column <= COLUMNS - WIN_LENGTH; column++) {
                score += evaluateWindow(
                    board[row][column],
                    board[row + 1][column + 1],
                    board[row + 2][column + 2],
                    board[row + 3][column + 3]
                );
            }
        }

        for (int row = 0; row <= ROWS - WIN_LENGTH; row++) {
            for (int column = WIN_LENGTH - 1; column < COLUMNS; column++) {
                score += evaluateWindow(
                    board[row][column],
                    board[row + 1][column - 1],
                    board[row + 2][column - 2],
                    board[row + 3][column - 3]
                );
            }
        }

        score += getImmediateWinningMoves(board, heights, AI_MARK).size() * 900;
        score -= getImmediateWinningMoves(board, heights, HUMAN_MARK).size() * 1_000;

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

    private List<Integer> getImmediateWinningMoves(char[][] board, int[] heights, char mark) {
        List<Integer> winningMoves = new ArrayList<Integer>();

        for (int column : getPlayableColumns(heights)) {
            int row = dropPiece(board, heights, column, mark);
            if (isWinningMove(board, row, column, mark)) {
                winningMoves.add(Integer.valueOf(column));
            }
            undoMove(board, heights, column);
        }

        return winningMoves;
    }

    private List<Integer> getPlayableColumns(int[] heights) {
        List<Integer> columns = new ArrayList<Integer>();
        for (int column : COLUMN_ORDER) {
            if (!isColumnFull(heights, column)) {
                columns.add(Integer.valueOf(column));
            }
        }
        return columns;
    }

    private int chooseSearchDepth(int[] heights) {
        int remainingMoves = ROWS * COLUMNS;
        for (int height : heights) {
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

    private int dropPiece(char[][] board, int[] heights, int column, char mark) {
        int row = ROWS - 1 - heights[column];
        board[row][column] = mark;
        heights[column]++;
        return row;
    }

    private void undoMove(char[][] board, int[] heights, int column) {
        heights[column]--;
        int row = ROWS - 1 - heights[column];
        board[row][column] = EMPTY;
    }

    private boolean isColumnFull(int[] heights, int column) {
        return heights[column] >= ROWS;
    }

    private boolean isBoardFull(int[] heights) {
        for (int column = 0; column < COLUMNS; column++) {
            if (!isColumnFull(heights, column)) {
                return false;
            }
        }
        return true;
    }

    private boolean isWinningMove(char[][] board, int row, int column, char mark) {
        return countConnected(board, row, column, 0, 1, mark) >= WIN_LENGTH
            || countConnected(board, row, column, 1, 0, mark) >= WIN_LENGTH
            || countConnected(board, row, column, 1, 1, mark) >= WIN_LENGTH
            || countConnected(board, row, column, 1, -1, mark) >= WIN_LENGTH;
    }

    private int countConnected(char[][] board, int row, int column, int rowStep, int columnStep, char mark) {
        int total = 1;
        total += countOneDirection(board, row, column, rowStep, columnStep, mark);
        total += countOneDirection(board, row, column, -rowStep, -columnStep, mark);
        return total;
    }

    private int countOneDirection(char[][] board, int row, int column, int rowStep, int columnStep, char mark) {
        int count = 0;
        int currentRow = row + rowStep;
        int currentColumn = column + columnStep;

        while (currentRow >= 0 && currentRow < ROWS && currentColumn >= 0 && currentColumn < COLUMNS) {
            if (board[currentRow][currentColumn] != mark) {
                break;
            }
            count++;
            currentRow += rowStep;
            currentColumn += columnStep;
        }

        return count;
    }

    private void renderBoard(char[][] board) {
        System.out.println("    1   2   3   4   5   6   7");
        System.out.println("  +---+---+---+---+---+---+---+");

        for (int row = 0; row < ROWS; row++) {
            StringBuilder line = new StringBuilder();
            line.append(row + 1).append(" |");

            for (int column = 0; column < COLUMNS; column++) {
                line.append(' ').append(displayMark(board[row][column])).append(" |");
            }

            System.out.println(line.toString());
            System.out.println("  +---+---+---+---+---+---+---+");
        }

        System.out.println();
    }

    private char displayMark(char mark) {
        return mark == EMPTY ? '.' : mark;
    }

    private String describeScore(int score) {
        if (score >= MAX_SCORE) {
            return "forced win";
        }
        if (score >= 8_000) {
            return "winning attack";
        }
        if (score >= 1_500) {
            return "strong edge";
        }
        if (score <= -MAX_SCORE) {
            return "forced loss ahead";
        }
        if (score <= -8_000) {
            return "dangerous position";
        }
        if (score <= -1_500) {
            return "slight disadvantage";
        }
        return "balanced";
    }

    private void printTitle() {
        System.out.println("  ____                            _     _____                 ");
        System.out.println(" / ___|___  _ __  _ __   ___  ___| |_  |  ___|__  _   _ _ __  ");
        System.out.println("| |   / _ \\| '_ \\| '_ \\ / _ \\/ __| __| | |_ / _ \\| | | | '__| ");
        System.out.println("| |__| (_) | | | | | | |  __/ (__| |_  |  _| (_) | |_| | |    ");
        System.out.println(" \\____\\___/|_| |_|_| |_|\\___|\\___|\\__| |_|  \\___/ \\__,_|_|    ");
        System.out.println();
    }

    private static class AiChoice {
        private final int column;
        private final int score;

        private AiChoice(int column, int score) {
            this.column = column;
            this.score = score;
        }
    }
}
