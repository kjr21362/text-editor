import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

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
    public static String originalTerminalSettings;

    public static void main(String[] args) throws IOException {

        enableRawMode();
        initEditor();

        while (true) {
            refreshScreen();
            int key = readKey();
            handleKey(key);
        }
    }

    private static void handleKey(int key) {
        // ctrl-q to exit
        if (key == CTRL_Q) {
            disableRawMode();
            clearScreen();
            System.exit(0);
        }
        // moves the cursor based on the arrow key input
        if (key == ARROW_UP) {
            if (cy > 0) {
                cy--;
            }
        } else if (key == ARROW_DOWN) {
            if (cy < ROWS - 1) {
                cy++;
            }
        } else if (key == ARROW_LEFT) {
            if (cx > 0) {
                cx--;
            }
        } else if (key == ARROW_RIGHT) {
            if (cx < COLUMNS - 1) {
                cx++;
            }
        } else if (key == PAGE_UP) {
            cy = 0;
        } else if (key == PAGE_DOWN) {
            cx = 0;
        } else if (key == HOME_KEY) {
            cx = 0;
        } else if (key == END_KEY) {
            cx = COLUMNS - 1;
        } else if (key == DELETE_KEY) {
            // nothing
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
        
        if(nextKey == '[') {
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
            switch (thirdKey){
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
        ROWS = size[0];
        COLUMNS = size[1];

        cx = 0;
        cy = 0;
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();
        builder.append("\033[?25l"); // hides the cursor
        //builder.append("\033[2J"); // clears entire screen
        builder.append("\033[H");  // moves cursor to row 1 column 1 (top left)
        builder.append("\033[K"); // clears line

        for (int r = 0; r < ROWS - 1; r++) {
            builder.append("~\r\n");
            builder.append("\033[K"); // clears line
        }

        builder.append("Editor - v0.0.1");

        builder.append(String.format("\033[%d;%dH", cy + 1,
            cx + 1));  // moves the cursor
        builder.append("\033[?25h"); // shows the cursor

        System.out.print(builder);
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