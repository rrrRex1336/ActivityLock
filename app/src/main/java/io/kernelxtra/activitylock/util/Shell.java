package io.kernelxtra.activitylock.util;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class Shell {

    private Shell() {
    }

    public static final class Result {
        public final boolean ok;
        public final int exitCode;
        @NonNull
        public final String output;

        Result(boolean ok, int exitCode, @NonNull String output) {
            this.ok = ok;
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    @NonNull
    public static Result su(@NonNull String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(12, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(false, -1, "timeout");
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 4000) {
                        output.append(line).append('\n');
                    }
                }
            }
            int code = process.exitValue();
            return new Result(code == 0, code, output.toString().trim());
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(false, -1, e.getMessage() == null ? "error" : e.getMessage());
        }
    }

    public static boolean isRootAvailable() {
        Result result = su("id");
        return result.ok && result.output.contains("uid=0");
    }
}
