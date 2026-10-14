package wraith.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** Tolerant launcher.json parser: unknown fields are ignored, malformed JSON is contained. */
public final class ManifestParser {
    private ManifestParser() { }
    public static Result parse(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonObject install = object(root, "install"); JsonObject launch = object(root, "launch");
            if (install == null || string(install, "type").isBlank()) return new Result(null, "Invalid launcher.json");
            Object asset = value(install.get("asset"));
            boolean executable = bool(install, "executable");
            JsonObject run = launch == null ? null : object(launch, "run");
            List<String> args = new ArrayList<>();
            if (run != null && run.has("args") && run.get("args").isJsonArray()) for (JsonElement argument : run.getAsJsonArray("args")) if (argument.isJsonPrimitive()) args.add(argument.getAsString());
            LauncherManifest result = new LauncherManifest(string(root, "displayName"), string(root, "description"), string(root, "icon"),
                    new LauncherManifest.Install(string(install, "type"), asset, executable),
                    new LauncherManifest.Launch(launch == null ? "" : string(launch, "instructions"), run == null ? null : new LauncherManifest.Run(value(run.get("windows")), value(run.get("linux")), List.copyOf(args))));
            return new Result(result, "");
        } catch (Exception error) { return new Result(null, "Invalid launcher.json"); }
    }
    private static JsonObject object(JsonObject root, String name) { return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : null; }
    private static String string(JsonObject object, String name) { return object != null && object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : ""; }
    private static boolean bool(JsonObject object, String name) { return object != null && object.has(name) && object.get(name).isJsonPrimitive() && object.get(name).getAsBoolean(); }
    private static Object value(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject()) { java.util.Map<String, String> values = new java.util.LinkedHashMap<>(); element.getAsJsonObject().entrySet().forEach(entry -> { if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString()); }); return values; }
        return null;
    }
    public record Result(LauncherManifest manifest, String error) { public boolean valid() { return manifest != null; } }
}
