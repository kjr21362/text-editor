import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class EditorSyntax {
    String fileType;
    String[] fileMatch;
    String singleLineCommentStart;
    String multilineCommentStart;
    String multilineCommentEnd;
    String[] keywords;
    int hl_flags;

    public EditorSyntax() {
    }

    public EditorSyntax(String fileType, String[] fileMatch, String singleLineCommentStart,
                        String multilineCommentStart, String multilineCommentEnd,
                        String[] keywords,
                        int hl_flags) {
        this.fileType = fileType;
        this.fileMatch = fileMatch.clone();
        this.singleLineCommentStart = singleLineCommentStart;
        this.multilineCommentStart = multilineCommentStart;
        this.multilineCommentEnd = multilineCommentEnd;
        this.keywords = keywords.clone();
        this.hl_flags = hl_flags;
    }
}

public class Main {
    private static final int ARROW_UP = 1000, ARROW_DOWN = 1001, ARROW_LEFT = 1002, ARROW_RIGHT =
        1003, PAGE_UP = 1004, PAGE_DOWN = 1005, HOME_KEY = 1006, END_KEY = 1007, DELETE_KEY = 1008,
        BACKSPACE = 127;

    private static final int HL_HIGHLIGHT_NUMBERS = 1 << 0;
    private static final int HL_HIGHLIGHT_STRING = 1 << 1;

    private static final String[] C_HL_EXTENSIONS = {".c", ".h", ".cpp"};
    private static final String[] C_HL_KEYWORDS = {
        "switch", "if", "while", "for", "break", "continue", "return", "else",
        "struct", "union", "typedef", "static", "enum", "class", "case",
        "int", "long", "double", "float", "char", "unsigned", "signed",
        "void"
    };
    private static final String SEPARATORS = " ,.()+-/*=~%<>[];";

    private static EditorSyntax[] HLDB =
        {new EditorSyntax("c", C_HL_EXTENSIONS, "//", "/*", "*/", C_HL_KEYWORDS,
            HL_HIGHLIGHT_NUMBERS | HL_HIGHLIGHT_STRING)};
    private static EditorSyntax editorSyntax;

    private enum HIGHLIGHT {
        HL_NORMAL,
        HL_NUMBER,
        HL_MATCH,
        HL_STRING,
        HL_COMMENT,
        HL_KEYWORD,
        HL_MLCOMMENT
    }

    // Number of extra ctrl-q action needed to exit the application,
    // when the file is modified.
    private static final int QUIT_TIMES = 1;
    private static final int DIRECTION_FORWARD = 1, DIRECTION_BACKWARD = -1;

    private static int lastMatchRow = -1, direction = DIRECTION_FORWARD;
    private static int quitTimes = QUIT_TIMES;

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
    private static String statusMessage;
    private static List<String> content;
    private static List<List<HIGHLIGHT>> highlightedContent;
    private static List<Boolean> rowInComment;

    private static List<HIGHLIGHT> savedHighlight;
    private static int savedHighlightLine;

    private static String fileName;

    // if the file has been modified
    private static boolean dirty = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            fileName = args[0];
        }

        enableRawMode();
        initEditor();
        editorOpen(fileName);
        initHighlight();

        while (true) {
            refreshScreen();
            int key = readKey();
            handleKey(key);
        }
    }

    private static void initHighlight() {
        if (content == null || content.isEmpty()) {
            return;
        }

        highlightedContent = new ArrayList<>();
        for (String line : content) {
            List<HIGHLIGHT> highlightedLine =
                new ArrayList<>(Collections.nCopies(line.length(), HIGHLIGHT.HL_NORMAL));
            highlightedContent.add(highlightedLine);
            rowInComment.add(false);
        }

        editorUpdateHighlight();
    }

    private static void editorOpen(String file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        editorSelectSyntax();

        Path path = Path.of(file);
        if (Files.exists(path)) {
            try (Stream<String> stream = Files.lines(path)) {
                content = stream.collect(Collectors.toList());
                dirty = false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void editorSave() {
        if (fileName == null) {
            fileName = editorPrompt("Save as: ", null);
            if (fileName == null) {
                statusMessage = "Save canceled";
                return;
            }
        }
        editorSelectSyntax();

        Path path = Path.of(fileName);
        try {
            Files.deleteIfExists(path);
            Files.createFile(path);
            for (String line : content) {
                Files.writeString(path, line + System.lineSeparator(), StandardOpenOption.APPEND);
            }
            statusMessage = "File saved!";
            dirty = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void editorFind() {
        int savedCx = cx, savedCy = cy;
        String query = editorPrompt("Search (ESC to cancel): ", getEditFindConsumer());
        if (query == null) {
            // search is canceled, restore cursor position
            cx = savedCx;
            cy = savedCy;
        }
    }

    private static BiConsumer<String, Integer> getEditFindConsumer() {
        BiConsumer<String, Integer> editFind = (query, key) -> {

            if (savedHighlight != null) {
                // restore previous highlighted line
                highlightedContent.set(savedHighlightLine, savedHighlight);
                savedHighlight = null;
            }

            if (key == '\033' || key == '\r') {
                lastMatchRow = -1;
                direction = DIRECTION_FORWARD;
                return;
            } else if (key == ARROW_UP || key == ARROW_LEFT) {
                direction = DIRECTION_BACKWARD;
            } else if (key == ARROW_DOWN || key == ARROW_RIGHT) {
                direction = DIRECTION_FORWARD;
            } else {
                lastMatchRow = -1;
                direction = DIRECTION_FORWARD;
            }

            int col;
            int currentRow = lastMatchRow;
            for (int i = 0; i < content.size(); i++) {
                currentRow += direction;
                if (currentRow < 0) {
                    currentRow = content.size() - 1;
                } else if (currentRow == content.size()) {
                    currentRow = 0;
                }
                String line = content.get(currentRow);
                col = line.indexOf(query);
                if (col > -1) {
                    lastMatchRow = currentRow;
                    cx = col;
                    cy = currentRow;

                    List<HIGHLIGHT> highlightedLine = highlightedContent.get(currentRow);
                    // save highlighted line
                    savedHighlightLine = currentRow;
                    savedHighlight = new ArrayList<>(highlightedLine);

                    for (int j = col; j < col + query.length(); j++) {
                        highlightedLine.set(j, HIGHLIGHT.HL_MATCH);
                    }
                    highlightedContent.set(currentRow, highlightedLine);

                    return;
                }
            }
        };

        return editFind;
    }

    private static String editorPrompt(String prompt, BiConsumer<String, Integer> callback) {
        StringBuilder input = new StringBuilder();

        while (true) {
            statusMessage = prompt + input;
            refreshScreen();

            try {
                int key = readKey();
                // TODO: BUG: when in search mode, need to press ESC twice to exit
                if (key == '\033') {
                    statusMessage = "";
                    if (callback != null) {
                        callback.accept(input.toString(), key);
                    }
                    return null;
                } else if (key == '\r') { // enter key
                    if (input.length() > 0) {
                        if (callback != null) {
                            callback.accept(input.toString(), key);
                        }
                        return input.toString();
                    }
                } else if (key == BACKSPACE) {
                    if (input.length() > 0) {
                        input.deleteCharAt(input.length() - 1);
                    }
                } else if (key >= 32 && key < 128) {
                    input.append((char) key);
                }
                if (callback != null) {
                    callback.accept(input.toString(), key);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void editorSelectSyntax() {
        if (fileName == null) {
            return;
        }

        editorSyntax = null;
        Optional<String> fileExt = getFileExtension(fileName);

        for (EditorSyntax syntax : HLDB) {
            for (String fileMatch : syntax.fileMatch) {
                boolean isExt = fileMatch.charAt(0) == '.';
                if ((isExt && !fileExt.isEmpty() && fileExt.get().equals(fileMatch)) ||
                    (!isExt && fileName.contains(fileMatch))) {
                    editorSyntax = syntax;
                    return;
                }
            }
        }

    }

    private static Optional<String> getFileExtension(String fileName) {
        return Optional.ofNullable(fileName).filter(f -> f.contains("."))
            .map(f -> f.substring(f.lastIndexOf(".")));
    }

    private static void editorUpdateHighlight() {
        for (int r = 0; r < content.size(); r++) {
            String line = content.get(r);
            List<HIGHLIGHT> highlightedLine =
                new ArrayList<>(Collections.nCopies(line.length(), HIGHLIGHT.HL_NORMAL));
            if (editorSyntax != null) {
                // syntax highlight is enabled
                boolean hasSeparatorBefore = true;
                boolean inComment = r > 0 && rowInComment.get(r-1);
                int in_string = 0;
                HIGHLIGHT prevHighlight = HIGHLIGHT.HL_NORMAL;

                String singleLineCommentStart = editorSyntax.singleLineCommentStart;
                String multilineCommentStart = editorSyntax.multilineCommentStart;
                String multilineCommentEnd = editorSyntax.multilineCommentEnd;

                int i = 0;
                while (i < line.length()) {
                    if (i > 0) {
                        prevHighlight = highlightedLine.get(i - 1);
                    }

                    // single line comment highlight
                    if (!singleLineCommentStart.isEmpty() && in_string == 0 && !inComment) {
                        if (i + singleLineCommentStart.length() <= line.length() &&
                            line.substring(i, i + singleLineCommentStart.length())
                                .equals(singleLineCommentStart)) {
                            for (int j = i; j < line.length(); j++) {
                                highlightedLine.set(j, HIGHLIGHT.HL_COMMENT);
                            }
                            break;
                        }
                    }

                    // multiline comment highlight
                    if (!multilineCommentStart.isEmpty() &&
                        !multilineCommentEnd.isEmpty() && in_string == 0) {
                        if (inComment) {
                            highlightedLine.set(i, HIGHLIGHT.HL_MLCOMMENT);
                            if (i + multilineCommentEnd.length() <= line.length() &&
                                line.substring(i, i + multilineCommentEnd.length())
                                    .equals(multilineCommentEnd)) {
                                for(int j=i; j<i+multilineCommentEnd.length(); j++) {
                                    highlightedLine.set(j, HIGHLIGHT.HL_MLCOMMENT);
                                }
                                i += multilineCommentEnd.length();
                                inComment = false;
                                hasSeparatorBefore = true;
                                continue;
                            } else {
                                i++;
                                continue;
                            }
                        } else if (i + multilineCommentStart.length() <= line.length() &&
                            line.substring(i, i + multilineCommentStart.length())
                                .equals(multilineCommentStart)) {
                            for(int j=i; j<i+multilineCommentStart.length(); j++) {
                                highlightedLine.set(j, HIGHLIGHT.HL_MLCOMMENT);
                            }
                            inComment = true;
                            i += multilineCommentStart.length();
                            continue;
                        }
                    }

                    if ((editorSyntax.hl_flags & HL_HIGHLIGHT_STRING) != 0) {
                        // string highlight is enabled
                        if (in_string > 0) {
                            highlightedLine.set(i, HIGHLIGHT.HL_STRING);
                            if (line.charAt(i) == '\\' && i + 1 < line.length()) {
                                highlightedLine.set(i + 1, HIGHLIGHT.HL_STRING);
                                i += 2;
                                continue;
                            }

                            if (line.charAt(i) == in_string) {
                                in_string = 0;
                            }
                            hasSeparatorBefore = false;
                            i++;
                            continue;
                        } else {
                            if (line.charAt(i) == '"' || line.charAt(i) == '\'') {
                                in_string = line.charAt(i);
                                highlightedLine.set(i, HIGHLIGHT.HL_STRING);
                                i++;
                                continue;
                            }
                        }
                    }

                    if ((editorSyntax.hl_flags & HL_HIGHLIGHT_NUMBERS) != 0) {
                        // number highlight is enabled
                        if (Character.isDigit(line.charAt(i)) &&
                            (hasSeparatorBefore || prevHighlight == HIGHLIGHT.HL_NUMBER) ||
                            (line.charAt(i) == '.' && prevHighlight == HIGHLIGHT.HL_NUMBER)) {
                            highlightedLine.set(i, HIGHLIGHT.HL_NUMBER);
                            hasSeparatorBefore = false;
                            i++;
                            continue;
                        }
                    }

                    // keyword highlight
                    if (hasSeparatorBefore) {
                        boolean seenKeyword = false;
                        for (int j = 0; j < editorSyntax.keywords.length; j++) {
                            String keyword = editorSyntax.keywords[j];
                            if (i + keyword.length() <= line.length() &&
                                line.substring(i, i + keyword.length())
                                    .equals(keyword) &&
                                (i + keyword.length() == line.length() ||
                                    isSeparator(line.charAt(i + keyword.length())))) {
                                for (int k = i; k < i + keyword.length(); k++) {
                                    highlightedLine.set(k, HIGHLIGHT.HL_KEYWORD);
                                }
                                i += keyword.length();
                                seenKeyword = true;
                                break;
                            }
                        }
                        if (seenKeyword) {
                            hasSeparatorBefore = false;
                            continue;
                        }
                    }

                    hasSeparatorBefore = isSeparator(line.charAt(i));
                    i++;
                }

                rowInComment.set(r, inComment);
            }
            highlightedContent.set(r, highlightedLine);
        }
    }

    private static boolean isSeparator(int key) {
        return SEPARATORS.indexOf(key) != -1;
    }

    private static int editorSyntaxToColor(HIGHLIGHT highlight) {
        switch (highlight) {
            case HL_NUMBER:
                return 31; // fg red
            case HL_MATCH:
                return 34;
            case HL_STRING:
                return 35;
            case HL_COMMENT:
            case HL_MLCOMMENT:
                return 36;
            case HL_KEYWORD:
                return 33;
            default:
                return 37; // fg white
        }
    }

    private static void insertChar(int c) {
        if (cy == content.size()) {
            content.add("");
            highlightedContent.add(new ArrayList<>());
        }
        int at = cx;
        String row = content.get(cy);
        if (at < 0 || at > row.length()) {
            at = row.length();
        }
        StringBuilder builder = new StringBuilder(row);
        builder.insert(at, (char) c);

        content.set(cy, builder.toString());
        cx++;
        dirty = true;
    }

    private static void insertRow() {
        if (cx == 0) {
            content.add(cy, "");
            highlightedContent.add(cy, new ArrayList<>());
            rowInComment.add(cy, false);
        } else if (cx == content.get(cy).length()) {
            content.add(cy + 1, "");
            highlightedContent.add(cy + 1, new ArrayList<>());
            rowInComment.add(cy + 1, false);
            cx = 0;
        } else {
            String line = content.get(cy);
            content.add(cy + 1, line.substring(cx));
            highlightedContent.add(cy + 1, new ArrayList<>());
            rowInComment.add(cy + 1, false);
            content.set(cy, line.substring(0, cx));
            cx = 0;
        }

        cy++;
        dirty = true;
    }

    // deletes the char to the left of cursor
    private static void deleteChar() {
        if (cy == content.size()) {
            return;
        }
        if (cx == 0 && cy == 0) {
            return;
        }

        if (cx > 0) {
            int at = cx - 1;
            String line = content.get(cy);
            if (at < 0 || at >= line.length()) {
                return;
            }
            line = line.substring(0, at) + line.substring(at + 1);
            content.set(cy, line);
            cx--;
            dirty = true;
        } else {
            String line = content.get(cy);
            deleteRow(cy);
            cy--;
            cx = content.get(cy).length();
            content.set(cy, content.get(cy) + line);
            dirty = true;
        }
    }

    private static void deleteRow(int at) {
        if (at < 0 || at >= content.size()) {
            return;
        }

        content.remove(at);
        dirty = true;
    }

    private static void handleKey(int key) {
        // ctrl-q to exit
        if (key == ctrl_key('q')) {
            if (dirty && quitTimes > 0) {
                statusMessage =
                    String.format("File has unsaved changes. Press Ctrl-Q %d times to quit.",
                        quitTimes);
                quitTimes--;
                return;
            }
            disableRawMode();
            clearScreen();
            System.exit(0);
        } else if (key == ctrl_key('s')) {
            editorSave();
            editorUpdateHighlight();
            return;
        } else if (key == ctrl_key('f')) {
            editorFind();
        } else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT).contains(key)) {
            moveCursor(key);
        } else if (key == PAGE_UP || key == PAGE_DOWN) {
            // position the cursor to the top/or bottom of window
            if (key == PAGE_UP) {
                cy = yOffset;
            } else if (key == PAGE_DOWN) {
                cy = yOffset + ROWS - 1;
            }
            // then move the cursor up/or down
            for (int i = 0; i < ROWS; i++) {
                moveCursor(key == PAGE_UP ? ARROW_UP : ARROW_DOWN);
            }
        } else if (key == HOME_KEY) {
            cx = 0;
        } else if (key == END_KEY) {
            cx = content.get(cy).length();
        } else if (key == DELETE_KEY) {
            moveCursor(ARROW_RIGHT);
            deleteChar();
            editorUpdateHighlight();
        } else if (key == '\r') { // enter key
            insertRow();
            editorUpdateHighlight();
        } else if (key == BACKSPACE) {
            deleteChar();
            editorUpdateHighlight();
        } else if (key == '\033') { // escape key
            // nothing
        } else {
            insertChar(key);
            editorUpdateHighlight();
        }

        quitTimes = QUIT_TIMES;

        // reposition cursor to end of line if it was out of range
        if (cy < content.size() && cx > content.get(cy).length()) {
            cx = content.get(cy).length();
        }

        buildStatusMessage();
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
            if (cy < content.size() &&
                cx < content.get(cy).length()) { // cannot scroll pass end of line
                cx++;
            } else if (cy < content.size()) {
                // arrow right at the end of line goes to beginning of next line
                cy++;
                cx = 0;
            }
        }
    }

    private static int ctrl_key(int key) {
        return key & 0x1f;
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
        editorSyntax = null;

        content = new ArrayList<>();
        highlightedContent = new ArrayList<>();
        rowInComment = new ArrayList<>();

        buildStatusMessage();
    }

    private static void buildStatusMessage() {
        statusMessage = String.format("Editor - v0.0.1. cx: %d, cy: %d", cx, cy);
        if (fileName != null) {
            statusMessage += " " + fileName;
        }
        if (editorSyntax == null) {
            statusMessage += " no ft";
        } else {
            statusMessage += " " + editorSyntax.fileType;
        }
        if (dirty) {
            statusMessage += " modified";
        }
    }

    private static void refreshScreen() {
        editorScroll();
        //editorUpdateSyntax();
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
                    int currentColor = -1;
                    List<HIGHLIGHT> highlightedLine = highlightedContent.get(fileRow);
                    for (int i = xOffset; i < xOffset + drawLen; i++) {
                        if(Character.isISOControl(line.charAt(i))) {
                            // handle non-printable chars
                            int sym = (line.charAt(i) <= 26 ? '@' + line.charAt(i) : '?');
                            builder.append("\033[7m"); // inverts color
                            builder.append((char) sym);
                            builder.append("\033[m"); // turns off invert color
                            if (currentColor != -1) {
                                builder.append(String.format("\033[%dm", currentColor));
                            }
                        }else if (highlightedLine.get(i) == HIGHLIGHT.HL_NORMAL) {
                            if (currentColor != -1) {
                                builder.append("\033[39m");
                                currentColor = -1;
                            }
                        } else {
                            int color = editorSyntaxToColor(highlightedLine.get(i));
                            if (color != currentColor) {
                                currentColor = color;
                                builder.append(String.format("\033[%dm", color));
                            }
                        }
                        builder.append(line.charAt(i));
                    }
                    builder.append("\033[39m"); // resets color
                    //builder.append(line, xOffset, xOffset + drawLen);
                }
            }
            builder.append("\r\n");
            builder.append("\033[K"); // clears line
        }

        builder.append(statusMessage);

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