import java.io.ByteArrayOutputStream;
import java.io.IOException;
public class Main {
    public static String originalTerminalSettings;
    public static void main(String[] args) throws IOException {

        enableRawMode();

        while(true){
            int ch = System.in.read();
            if(Character.isISOControl(ch)){
                System.out.print(ch + "\r\n");
            }else{
                System.out.print((char)ch + "\r\n");
            }

            if(ch == 'q'){
                disableRawMode();
                System.exit(0);
            }
        }
    }

    public static void enableRawMode() {
        originalTerminalSettings = exec("/usr/bin/env", "stty", "-g");
        exec("/usr/bin/env", "stty", "-brkint", "-icrnl", "-inpck", "-istrip", "-ixon");
        exec("/usr/bin/env", "stty", "-opost");
        exec("/usr/bin/env", "stty", "cs8");
        exec("/usr/bin/env", "stty", "-echo", "-icanon", "-iexten", "-isig");
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