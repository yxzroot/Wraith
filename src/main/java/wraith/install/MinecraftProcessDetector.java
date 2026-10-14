package wraith.install;

import java.util.Locale;

public final class MinecraftProcessDetector {
    public boolean running() {
        return ProcessHandle.allProcesses().anyMatch(process -> {
            ProcessHandle.Info info = process.info(); String command = info.command().orElse("").toLowerCase(Locale.ROOT);
            String executable = command.substring(Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\')) + 1);
            if (!executable.matches("javaw?(?:\\.exe)?")) return false;
            String args = String.join(" ", info.arguments().orElse(new String[0])).toLowerCase(Locale.ROOT);
            return args.contains(".minecraft") || args.contains("lunarclient") || args.contains("prismlauncher");
        });
    }
}
