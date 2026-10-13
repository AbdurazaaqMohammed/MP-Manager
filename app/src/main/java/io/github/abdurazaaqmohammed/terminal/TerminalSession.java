package io.github.abdurazaaqmohammed.terminal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One running interactive shell.
 *
 * <p>Spawns a plain {@code sh} for normal mode or {@code su -c} for root mode,
 * then streams whatever it prints back to the caller. Commands are pushed in as
 * raw lines on stdin, so this is a real interactive shell rather than a
 * one-shot executor: cd, jobs and prompts all behave the way they do in a
 * terminal.
 */
public final class TerminalSession {

    /** What the shell prints back. Delivered on a background thread. */
    public interface Output {
        void onOutput(String text);

        void onExit(int code);
    }

    private final Process process;
    private final OutputStream stdin;
    private final Thread reader;
    private final List<Output> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    private TerminalSession(Process process) {
        this.process = process;
        this.stdin = process.getOutputStream();
        this.reader = new Thread(this::pump, "terminal-pump");
        this.reader.setDaemon(true);
        this.reader.start();
        new Thread(() -> {
            try {
                int code = process.waitFor();
                running = false;
                for (Output output : listeners) {
                    output.onExit(code);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "terminal-wait").start();
    }

    /**
     * Start a shell. When {@code root} is true the shell runs as {@code su -c sh}
     * so the user gets a root prompt; otherwise a plain {@code sh}.
     */
    public static TerminalSession start(boolean root, String binary) {
        try {
            Process process = root
                    ? Runtime.getRuntime().exec(new String[]{binary, "-c", "sh"})
                    : Runtime.getRuntime().exec(new String[]{"sh"});
            return new TerminalSession(process);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void addListener(Output output) {
        listeners.add(output);
    }

    public void removeListener(Output output) {
        listeners.remove(output);
    }

    /** Send one command line. The trailing newline is added here. */
    public void send(String line) {
        try {
            stdin.write((line + "\n").getBytes(Charset.defaultCharset()));
            stdin.flush();
        } catch (Exception e) {
            running = false;
        }
    }

    /** Send a single control byte, e.g. Ctrl-C (0x03) to interrupt. */
    public void sendControl(int code) {
        try {
            stdin.write(code);
            stdin.flush();
        } catch (Exception e) {
            running = false;
        }
    }

    /** Tear the shell down. Safe to call more than once. */
    public void close() {
        running = false;
        try {
            stdin.close();
        } catch (Exception ignored) {
        }
        process.destroy();
    }

    private void pump() {
        try {
            InputStream stream = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while (running && (read = stream.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, read, Charset.defaultCharset());
                for (Output output : listeners) {
                    output.onOutput(chunk);
                }
            }
        } catch (Exception ignored) {
            // Stream closed when the shell exits or is destroyed.
        }
    }

    /** Read remaining buffered output without blocking forever, for shutdown. */
    static String drain(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder all = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                all.append(line).append('\n');
            }
            return all.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
