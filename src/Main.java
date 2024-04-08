import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final int CTRL_Q = 17;
    private static final int ARROW_UP = 1000, ARROW_DOWN = 1001, ARROW_LEFT = 1002, ARROW_RIGHT =
        1003, PAGE_UP = 1004, PAGE_DOWN = 1005, HOME_KEY = 1006, END_KEY = 1007, DELETE_KEY = 1008;

    // screen height
    private static int ROWS;
    // screen width
    private static int COLUMNS;

    // cursor position
    private static int cx, cy;

    // file row offset, relative to the top of file
    private static int yOffset;
    private static int xOffset;
    private static String originalTerminalSettings;

    private static List<String> content = new ArrayList<>();

    public static void main(String[] args) throws IOException {

        enableRawMode();
        initEditor();
        if (args.length > 0) {
            editorOpen(args[0]);
        }

        while (true) {
            refreshScreen();
            int key = readKey();
            handleKey(key);
        }
    }

    private static void editorOpen(String file) {
        Path path = Path.of(file);
        if (Files.exists(path)) {
            try (Stream<String> stream = Files.lines(path)) {
                content = stream.toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void handleKey(int key) {
        // ctrl-q to exit
        if (key == CTRL_Q) {
            disableRawMode();
            clearScreen();
            System.exit(0);
        }
        if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT).contains(key)) {
            moveCursor(key);
        } else if (key == PAGE_UP || key == PAGE_DOWN) {
            if (key == PAGE_UP) {
                cy = yOffset;
            } else if (key == PAGE_DOWN) {
                cy = yOffset + ROWS - 1;
            }
            for (int i = 0; i < ROWS; i++) {
                moveCursor(key == PAGE_UP ? ARROW_UP : ARROW_DOWN);
            }
        } else if (key == HOME_KEY) {
            cx = 0;
        } else if (key == END_KEY) {
            cx = content.get(cy).length();
        } else if (key == DELETE_KEY) {
            // nothing
        }

        // reposition cursor to end of line if it was out of range
        if (cy < content.size() && cx > content.get(cy).length()) {
            cx = content.get(cy).length();
        }
    }

    private static void moveCursor(int key) {
        // moves the cursor based on the arrow key input
        if (key == ARROW_UP) {
            if (cy > 0) {
                cy--;
            }
        } else if (key == ARROW_DOWN) {
            if (cy < content.size()) {
                cy++;
            }
        } else if (key == ARROW_LEFT) {
            if (cx > 0) {
                cx--;
            } else if (cy > 0) {
                // arrow left at the beginning of line goes to end of previous line
                cy--;
                cx = content.get(cy).length();
            }
        } else if (key == ARROW_RIGHT) {
            if (cx < content.get(cy).length()) { // cannot scroll pass end of line
                cx++;
            } else if (cy < content.size()) {
                // arrow right at the end of line goes to beginning of next line
                cy++;
                cx = 0;
            }
        }
    }

    private static int readKey() throws IOException {
        int key = System.in.read();
        // arrow key: \033[A or \033[B or \033[C or \033[D
        // page key: \033[5~ and \033[6~
        // delete key: \033[3~
        // home key: \033[1~, \033[7~, \033[H, \033OH
        // end key: \033[4~, \033[8~, \033[F, \033OF
        if (key != '\033') {
            return key;
        }

        int nextKey = System.in.read();

        if (nextKey == '[') {
            int thirdKey = System.in.read();
            switch (thirdKey) {
                case 'A':
                    return ARROW_UP;
                case 'B':
                    return ARROW_DOWN;
                case 'C':
                    return ARROW_RIGHT;
                case 'D':
                    return ARROW_LEFT;
                case 'H':
                    return HOME_KEY;
                case 'F':
                    return END_KEY;
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9':
                    int forthKey = System.in.read();
                    if (forthKey != '~') {
                        return forthKey;
                    }
                    switch (thirdKey) {
                        case '1':
                        case '7':
                            return HOME_KEY;
                        case '3':
                            return DELETE_KEY;
                        case '4':
                        case '8':
                            return END_KEY;
                        case '5':
                            return PAGE_UP;
                        case '6':
                            return PAGE_DOWN;
                        default:
                            return thirdKey;
                    }
                default:
                    return thirdKey;
            }
        } else if (nextKey == 'O') {
            int thirdKey = System.in.read();
            switch (thirdKey) {
                case 'H':
                    return HOME_KEY;
                case 'F':
                    return END_KEY;
                default:
                    return thirdKey;
            }
        }

        return nextKey;
    }

    private static void clearScreen() {
        System.out.print("\033[2J"); // clears entire screen
        System.out.print("\033[H");  // moves cursor to row 1 column 1 (top left)
    }

    private static void initEditor() {
        int[] size = getWindowSize();
        ROWS = size[0] - 1;
        COLUMNS = size[1];

        cx = 0;
        cy = 0;
        xOffset = 0;
        yOffset = 0;
    }

    private static void refreshScreen() {
        editorScroll();
        StringBuilder builder = new StringBuilder();
        builder.append("\033[?25l"); // hides the cursor
        //builder.append("\033[2J"); // clears entire screen
        builder.append("\033[H");  // moves cursor to row 1 column 1 (top left)
        builder.append("\033[K"); // clears line

        for (int r = 0; r < ROWS; r++) {
            int fileRow = r + yOffset;
            if (fileRow >= content.size()) {
                // prints ~ for empty line
                builder.append("~");
            } else {
                // prints content
                String line = content.get(fileRow);
                int drawLen = line.length() - xOffset;
                if (drawLen < 0) {
                    drawLen = 0;
                }
                if (drawLen > COLUMNS) {
                    drawLen = COLUMNS;
                }
                if (drawLen > 0) {
                    builder.append(line, xOffset, xOffset + drawLen);
                }
            }
            builder.append("\r\n");
            builder.append("\033[K"); // clears line
        }

        builder.append("Editor - v0.0.1");

        builder.append(String.format("\033[%d;%dH", cy - yOffset + 1,
            cx - xOffset + 1));  // moves the cursor
        builder.append("\033[?25h"); // shows the cursor

        System.out.print(builder);
    }

    private static void editorScroll() {
        // if the cursor is above the visible window, scroll up
        if (cy < yOffset) {
            yOffset = cy;
        }
        // if the cursor is below the bottom of visible window, scroll down
        if (cy >= yOffset + ROWS) {
            yOffset = cy - ROWS + 1;
        }

        // horizontal scrolling
        if (cx < xOffset) {
            xOffset = cx;
        }
        if (cx >= xOffset + COLUMNS) {
            xOffset = cx - COLUMNS + 1;
        }
    }

    public static void enableRawMode() {
        originalTerminalSettings = exec("/usr/bin/env", "stty", "-g");
        exec("/usr/bin/env", "stty", "-brkint", "-icrnl", "-inpck", "-istrip", "-ixon");
        exec("/usr/bin/env", "stty", "-opost");
        exec("/usr/bin/env", "stty", "cs8");
        exec("/usr/bin/env", "stty", "-echo", "-icanon", "-iexten", "-isig");
    }

    public static int[] getWindowSize() {
        var output = exec("/usr/bin/env", "stty", "size");
        return Arrays.stream(output.split(" ")).mapToInt(Integer::parseInt).toArray();
    }

    static String exec(String... cmd) {
        var pb = new ProcessBuilder(cmd);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

        try {
            Process process = pb.start();
            var stdoutBuffer = new ByteArrayOutputStream();
            var stdout = process.getInputStream();
            var readByte = stdout.read();
            while (readByte != -1) {
                stdoutBuffer.write(readByte);
                readByte = stdout.read();
            }
            return stdoutBuffer.toString().trim();
        } catch (IOException e) {
            System.out.println("Error executing " + cmd);
            throw new RuntimeException(e);
        }
    }

    public static void disableRawMode() {
        if (originalTerminalSettings == null) {
            return;
        }
        exec("/usr/bin/env", "stty", originalTerminalSettings);
    }
}