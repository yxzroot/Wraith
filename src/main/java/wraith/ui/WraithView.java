package wraith.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import wraith.core.AppContext;
import wraith.core.NameValidator;
import wraith.core.ProgramEntry;
import wraith.core.ProgramStatus;
import wraith.github.LibraryResult;
import wraith.detect.LocalInstallDetector;
import wraith.detect.MinecraftInstall;
import wraith.detect.LoaderType;
import wraith.install.CancelToken;
import wraith.install.InstallProgress;
import wraith.install.InstallResult;
import wraith.install.InstalledProgram;
import wraith.install.MinecraftLauncherStarter;
import wraith.install.MinecraftProcessDetector;
import wraith.install.ProgramRunner;

/** Phase-one UI. Services are deliberately hidden behind the AppContext. */
public final class WraithView {
    private final Stage stage;
    private final AppContext context;
    private final StackPane content = new StackPane();
    private final ScreenRouter router = new ScreenRouter(content);
    private final Label debugFooter = new Label();
    private final List<ProgramEntry> programs = new ArrayList<>();
    private final Map<String, Button> navigation = new LinkedHashMap<>();
    private TextField search;
    private String filter = "All";
    private boolean libraryOffline;
    private ProgramEntry selected;
    private Label userInitial;
    private Label userName;
    private double dragX, dragY;

    public WraithView(Stage stage, AppContext context, boolean customChrome, boolean tiling) {
        this.stage = stage; this.context = context;
        stage.getScene().setRoot(buildShell(customChrome, tiling));
        if ("1".equals(System.getenv("WRAITH_DEBUG"))) {
            debugFooter.setText("Debug · " + context.paths.os() + " · " + context.paths.configDirectory() + " · class "
                    + context.paths.windowClass() + " · JavaFX " + System.getProperty("javafx.version", "unknown"));
        }
    }

    private Parent buildShell(boolean customChrome, boolean tiling) {
        BorderPane shell = new BorderPane(); shell.getStyleClass().addAll("app-shell", tiling ? "tiling-shell" : "glass-shell");
        shell.setTop(titleBar(customChrome));
        shell.setLeft(sidebar());
        shell.setCenter(content);
        BorderPane.setMargin(content, new Insets(0));
        debugFooter.getStyleClass().add("debug-footer");
        BorderPane footer = new BorderPane(debugFooter); footer.getStyleClass().add("debug-footer-box");
        shell.setBottom(footer);
        shell.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SLASH && search != null) { search.requestFocus(); event.consume(); }
            if (event.getCode() == KeyCode.ESCAPE && selected != null) { selected = null; showLibrary(false); event.consume(); }
        });
        return shell;
    }

    private Node titleBar(boolean customChrome) {
        HBox bar = new HBox(7); bar.setAlignment(Pos.CENTER_LEFT); bar.getStyleClass().add("title-bar");
        Label wordmark = new Label("WRAITH"); wordmark.getStyleClass().add("wordmark");
        Label badge = new Label("V1"); badge.getStyleClass().add("version-badge");
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        Button min = chrome("—"); min.setOnAction(event -> stage.setIconified(true));
        Button close = chrome("×"); close.getStyleClass().add("close-control"); close.setOnAction(event -> stage.close());
        min.setVisible(customChrome); min.setManaged(customChrome); close.setVisible(customChrome); close.setManaged(customChrome);
        bar.getChildren().addAll(wordmark, badge, space, min, close);
        if (customChrome) {
            bar.setOnMousePressed(event -> { dragX = event.getScreenX() - stage.getX(); dragY = event.getScreenY() - stage.getY(); });
            bar.setOnMouseDragged(event -> { stage.setX(event.getScreenX() - dragX); stage.setY(event.getScreenY() - dragY); });
        }
        return bar;
    }

    private Node sidebar() {
        VBox side = new VBox(4); side.getStyleClass().add("sidebar"); side.setPrefWidth(200); side.setMinWidth(200); side.setMaxWidth(200);
        side.setPadding(new Insets(14, 12, 12, 12));
        Button library = nav("▦", "Library", ""); library.setOnAction(event -> { filter = "All"; selected = null; showLibrary(false); });
        Button installed = nav("↓", "Installed", "1"); installed.setOnAction(event -> { filter = "Installed"; selected = null; showLibrary(false); });
        Button updates = nav("↑", "Updates", ""); updates.setVisible(false); updates.setManaged(false);
        Button help = nav("?", "Help", ""); help.setOnAction(event -> showTutorial());
        Button settings = nav("⚙", "Settings", ""); settings.setOnAction(event -> showSettings());
        Region grow = new Region(); VBox.setVgrow(grow, Priority.ALWAYS);
        side.getChildren().addAll(library, installed, updates, help, settings, grow, userChip());
        return side;
    }

    private Button nav(String icon, String text, String count) {
        HBox inner = new HBox(10); inner.setAlignment(Pos.CENTER_LEFT);
        Label glyph = new Label(icon); glyph.getStyleClass().add("nav-icon");
        Label label = new Label(text); label.getStyleClass().add("nav-label");
        Region gap = new Region(); HBox.setHgrow(gap, Priority.ALWAYS);
        inner.getChildren().addAll(glyph, label, gap);
        if (!count.isBlank()) { Label badge = new Label(count); badge.getStyleClass().add("count-badge"); inner.getChildren().add(badge); }
        Button result = new Button(); result.setGraphic(inner); result.setMaxWidth(Double.MAX_VALUE); result.getStyleClass().add("nav-item");
        navigation.put(text, result);
        return result;
    }

    private Node userChip() {
        HBox chip = new HBox(8); chip.setAlignment(Pos.CENTER_LEFT); chip.getStyleClass().add("user-chip");
        String name = displayName();
        userInitial = new Label(name.substring(0, 1).toUpperCase()); userInitial.getStyleClass().add("user-initial");
        VBox text = new VBox(2); userName = new Label(name); userName.getStyleClass().add("user-name");
        Button change = ghost("Change name"); change.getStyleClass().add("change-name"); change.setOnAction(event -> showName());
        text.getChildren().addAll(userName, change); chip.getChildren().addAll(userInitial, text); return chip;
    }

    public void showInitial() {
        if (context.config.playerName == null || context.config.playerName.isBlank()) showName();
        else if (!context.config.tutorialCompleted) showTutorial();
        else showLibrary(true);
    }

    private void showName() {
        VBox card = new VBox(14); card.setAlignment(Pos.CENTER); card.getStyleClass().addAll("surface-card", "name-card");
        Label logo = new Label("WRAITH"); logo.getStyleClass().add("name-logo");
        Label sub = new Label("Your local program library"); sub.getStyleClass().add("body-secondary");
        TextField field = new TextField(context.config.playerName == null ? "" : context.config.playerName); field.setPromptText("Enter your name"); field.getStyleClass().add("name-input");
        Label error = new Label(); error.getStyleClass().add("inline-error");
        Button go = primary("Continue");
        Runnable submit = () -> { NameValidator.Result result = NameValidator.validate(field.getText()); error.setText(result.message()); if (result.valid()) { context.config.playerName = field.getText(); context.saveQuietly(); refreshUserChip(); showTutorial(); } };
        go.setOnAction(event -> submit.run()); field.setOnAction(event -> submit.run());
        card.getChildren().addAll(logo, sub, field, error, go); router.show(center(card)); Platform.runLater(field::requestFocus);
    }

    /** Short, plain-language guide for first launch and the Help navigation item. */
    private void showTutorial() { showTutorialStep(1); }

    private void showTutorialStep(int step) {
        selected = null;
        setActiveNavigation("Help");
        VBox card = new VBox(14); card.setAlignment(Pos.CENTER_LEFT); card.getStyleClass().addAll("surface-card", "tutorial-card");
        Label progress = new Label("GETTING STARTED  ·  " + step + " OF 3"); progress.getStyleClass().add("tutorial-progress");
        Label title = new Label(switch (step) { case 1 -> "Welcome to Wraith"; case 2 -> "Find something you want"; default -> "Install safely, then launch"; }); title.getStyleClass().add("tutorial-title");
        Label intro = new Label(switch (step) {
            case 1 -> "Hi " + displayName() + ". Wraith keeps GitHub projects in one simple library.";
            case 2 -> "Your Library shows projects published by the GitHub people you choose.";
            default -> "Wraith only installs something after you press Install. It never starts a download by itself.";
        }); intro.setWrapText(true); intro.getStyleClass().add("tutorial-intro");
        VBox explanation = new VBox(9); explanation.getStyleClass().add("tutorial-explanation");
        String[] points = switch (step) {
            case 1 -> new String[] {
                    "Nothing from your computer is sent anywhere.",
                    "Wraith reads public project information from GitHub.",
                    "You can change where projects come from later in Settings."
            };
            case 2 -> new String[] {
                    "Click a card to read what a project does.",
                    "Use Search if you already know its name.",
                    "Projects marked “Source only” do not have a download yet."
            };
            default -> new String[] {
                    "Click Install only for a project you trust.",
                    "When it is ready, click Launch yourself—Wraith will ask before the first launch.",
                    "For Phantom, Wraith will guide you to a compatible Minecraft Forge profile."
            };
        };
        for (int index = 0; index < points.length; index++) {
            HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
            Label number = new Label(String.valueOf(index + 1)); number.getStyleClass().add("tutorial-number");
            Label text = new Label(points[index]); text.setWrapText(true); text.getStyleClass().add("tutorial-text");
            row.getChildren().addAll(number, text); explanation.getChildren().add(row);
        }
        HBox buttons = new HBox(10); buttons.setAlignment(Pos.CENTER_RIGHT); Button skip = ghost("Skip for now"); skip.setOnAction(event -> finishTutorial());
        if (step > 1) { Button back = ghost("Back"); back.setOnAction(event -> showTutorialStep(step - 1)); buttons.getChildren().add(back); }
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS); buttons.getChildren().add(space); Button next = primary(step == 3 ? "Open my Library" : "Next"); next.setOnAction(event -> { if (step == 3) finishTutorial(); else showTutorialStep(step + 1); }); buttons.getChildren().addAll(skip, next);
        card.getChildren().addAll(progress, title, intro, explanation, buttons);
        router.show(center(card));
    }

    private void finishTutorial() { context.config.tutorialCompleted = true; context.saveQuietly(); filter = "All"; showLibrary(true); }

    public void showLibrary(boolean refresh) {
        selected = null;
        setActiveNavigation(switch (filter) { case "Installed" -> "Installed"; case "Updates" -> "Updates"; default -> "Library"; });
        VBox page = new VBox(14); page.getStyleClass().add("content-page");
        HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Library"); title.getStyleClass().add("page-title");
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        Label noticeBanner = new Label(); noticeBanner.getStyleClass().add("offline-banner"); noticeBanner.setVisible(false); noticeBanner.setManaged(false);
        search = new TextField(); search.setPromptText("⌕  Search programs"); search.getStyleClass().add("search-field"); search.textProperty().addListener((obs, before, after) -> renderCards(gridHolder));
        Button reload = ghost("↻"); reload.getStyleClass().add("refresh-button"); reload.setOnAction(event -> loadLibrary(true, gridHolder, reload, noticeBanner));
        header.getChildren().addAll(title, space, search, reload);
        HBox chips = new HBox(8); for (String name : List.of("All", "Installable", "Installed", "Updates")) {
            Button chip = new Button(name); chip.getStyleClass().addAll("filter-chip", name.equals(filter) ? "filter-active" : "");
            chip.setOnAction(event -> { filter = name; showLibrary(false); }); chips.getChildren().add(chip);
        }
        gridHolder = new StackPane(); gridHolder.getStyleClass().add("grid-host"); VBox.setVgrow(gridHolder, Priority.ALWAYS);
        page.getChildren().addAll(header, noticeBanner, chips, gridHolder); router.show(page); loadLibrary(refresh, gridHolder, reload, noticeBanner);
    }

    private StackPane gridHolder;

    private void loadLibrary(boolean force, StackPane holder, Button refresh, Label notice) {
        if (!programs.isEmpty() && !force) { renderCards(holder); return; }
        holder.getChildren().setAll(skeleton()); refresh.getStyleClass().add("is-loading");
        Task<LibraryResult> task = new Task<>() { @Override protected LibraryResult call() throws Exception { return context.library.load(force); } };
        task.setOnSucceeded(event -> { programs.clear(); programs.addAll(task.getValue().programs()); libraryOffline = task.getValue().offline(); String text = task.getValue().notice(); notice.setText(text); notice.setVisible(!text.isBlank()); notice.setManaged(!text.isBlank()); refresh.getStyleClass().remove("is-loading"); renderCards(holder); });
        task.setOnFailed(event -> { refresh.getStyleClass().remove("is-loading"); holder.getChildren().setAll(message("Could not load the library. Cached programs will be shown when available.")); context.configStore.log().error("Library load failed", new Exception(task.getException())); });
        Thread thread = new Thread(task, "wraith-library"); thread.setDaemon(true); thread.start();
    }

    private void renderCards(StackPane holder) {
        // 2 × 300px tiles plus the 16px gutter fit inside the fixed content
        // viewport even after the scrollbar consumes its small width.
        TilePane grid = new TilePane(16, 16); grid.setPrefColumns(2); grid.setPrefTileWidth(300); grid.setPrefTileHeight(150); grid.getStyleClass().add("program-grid");
        String text = search == null ? "" : search.getText().toLowerCase(Locale.ROOT);
        Predicate<ProgramEntry> matcher = entry -> (text.isBlank() || entry.displayName().toLowerCase(Locale.ROOT).contains(text) || entry.description().toLowerCase(Locale.ROOT).contains(text))
                && switch (filter) { case "Installable" -> entry.hasRelease(); case "Installed" -> entry.status() == ProgramStatus.INSTALLED; case "Updates" -> entry.status() == ProgramStatus.UPDATE; default -> true; };
        int delay = 0;
        for (ProgramEntry entry : programs.stream().filter(matcher).toList()) {
            Node card = programCard(entry); card.setOpacity(0); card.setTranslateY(8); grid.getChildren().add(card);
            PauseTransition stagger = new PauseTransition(Duration.millis(delay)); stagger.setOnFinished(event -> { card.setOpacity(1); card.setTranslateY(0); }); stagger.play(); delay += 40;
        }
        if (grid.getChildren().isEmpty()) holder.getChildren().setAll(message("No programs match this filter."));
        else { ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("library-scroll"); holder.getChildren().setAll(scroll); }
    }

    private Node programCard(ProgramEntry entry) {
        VBox card = new VBox(8); card.getStyleClass().add("program-card"); card.setPrefSize(300, 150);
        HBox top = new HBox(10); top.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(entry.initial()); icon.getStyleClass().add("program-icon");
        VBox title = new VBox(3); Label name = new Label(entry.displayName()); name.getStyleClass().add("program-name");
        HBox meta = new HBox(5); Label dot = new Label("●"); dot.getStyleClass().add("language-dot"); Label line = new Label(entry.language() + "  ·  " + entry.version()); line.getStyleClass().add("card-meta"); meta.getChildren().addAll(dot, line); title.getChildren().addAll(name, meta);
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS); Label status = status(entry.status()); top.getChildren().addAll(icon, title, space, status);
        Label description = new Label(entry.description()); description.setWrapText(true); description.setMaxHeight(37); description.getStyleClass().add("card-description");
        HBox bottom = new HBox(); bottom.setAlignment(Pos.CENTER_RIGHT); Button action = entry.hasRelease() ? primary(entry.status() == ProgramStatus.UPDATE ? "Update" : entry.status() == ProgramStatus.INSTALLED ? "Open" : "Install") : ghost("View on GitHub"); action.getStyleClass().add("card-action"); action.setDisable(libraryOffline && (entry.status() == ProgramStatus.INSTALL || entry.status() == ProgramStatus.UPDATE));
        action.setOnAction(event -> { event.consume(); if (!entry.hasRelease()) openGithub(entry); else showProgram(entry); }); bottom.getChildren().add(action);
        card.getChildren().addAll(top, description, bottom); card.setOnMouseClicked(event -> showProgram(entry)); return card;
    }

    private void showProgram(ProgramEntry entry) {
        selected = entry;
        VBox page = new VBox(14); page.getStyleClass().add("content-page");
        Button back = ghost("← Library"); back.getStyleClass().add("back-link"); back.setOnAction(event -> showLibrary(false));
        HBox header = new HBox(14); header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(entry.initial()); icon.getStyleClass().add("program-icon-large");
        VBox details = new VBox(4); Label title = new Label(entry.displayName()); title.getStyleClass().add("program-title"); Label metadata = new Label(entry.owner() + "/" + entry.repo() + "  ·  " + entry.language() + "  ·  " + entry.version()); metadata.getStyleClass().add("body-secondary"); details.getChildren().addAll(title, metadata);
        Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS); Button action = primary(entry.status() == ProgramStatus.INSTALLED ? "Launch" : entry.status() == ProgramStatus.UPDATE ? "Update" : "Install"); Button uninstall = ghost("Uninstall"); uninstall.setVisible(entry.status() == ProgramStatus.INSTALLED); uninstall.setManaged(entry.status() == ProgramStatus.INSTALLED); Button github = ghost("↗"); github.setOnAction(event -> openGithub(entry));
        action.setOnAction(event -> actOnProgram(entry)); action.setDisable(libraryOffline && (entry.status() == ProgramStatus.INSTALL || entry.status() == ProgramStatus.UPDATE));
        uninstall.setOnAction(event -> uninstall(entry));
        header.getChildren().addAll(icon, details, push, status(entry.status()), action, uninstall, github);
        HBox tabs = new HBox(22); tabs.getStyleClass().add("tabs"); StackPane tabContent = new StackPane(); VBox.setVgrow(tabContent, Priority.ALWAYS);
        for (String tab : List.of("Overview", "How to launch", "Releases", "Files")) { Button button = new Button(tab); button.getStyleClass().add(tab.equals("Overview") ? "tab-active" : "tab"); button.setOnAction(event -> { tabs.getChildren().forEach(node -> node.getStyleClass().remove("tab-active")); button.getStyleClass().add("tab-active"); tabContent.getChildren().setAll(programTab(entry, tab)); }); tabs.getChildren().add(button); }
        tabContent.getChildren().add(programTab(entry, "Overview")); page.getChildren().addAll(back, header, tabs, tabContent); router.show(page);
    }

    private Node programTab(ProgramEntry entry, String tab) {
        if (tab.equals("How to launch")) return launchInstructions(entry);
        if (tab.equals("Releases")) return message(entry.release() != null && entry.release().available() ? entry.release().tag() + "  ·  " + entry.release().publishedAt() + "\n" + shorten(entry.release().body()) + "\n" + String.join("\n", entry.release().assets().stream().map(asset -> asset.name() + "  ·  " + asset.size() + " bytes").toList()) : "No releases have been published.");
        if (tab.equals("Files")) return filesTab(entry);
        VBox overview = new VBox(12); overview.getStyleClass().add("overview"); Label description = new Label(entry.description()); description.setWrapText(true); description.getStyleClass().add("overview-description"); Label readme = new Label(entry.readme().isBlank() ? "No README was available from GitHub." : entry.readme()); readme.setWrapText(true); readme.getStyleClass().add("readme-text"); overview.getChildren().addAll(description, readme); if (!entry.manifestError().isBlank()) { Label invalid = new Label(entry.manifestError()); invalid.getStyleClass().add("inline-error"); overview.getChildren().add(invalid); } return overview;
    }

    private Node launchInstructions(ProgramEntry entry) {
        VBox pane = new VBox(14); pane.getStyleClass().add("surface-card"); String[] steps = instructions(entry).split("\\n");
        int number = 1; for (String step : steps) { HBox row = new HBox(10); row.setAlignment(Pos.TOP_LEFT); Label circle = new Label(String.valueOf(number++)); circle.getStyleClass().add("step-number"); Label label = new Label(step); label.setWrapText(true); label.getStyleClass().add("step-text"); row.getChildren().addAll(circle, label); pane.getChildren().add(row); }
        Path installedPath = context.installs.find(entry.owner(), entry.repo()).map(record -> Path.of(record.location)).orElse(context.paths.programsDirectory().resolve(entry.repo()));
        HBox location = new HBox(12); location.setAlignment(Pos.CENTER_LEFT); location.getStyleClass().add("install-location"); VBox labels = new VBox(3); Label installed = new Label("Installed in"); installed.getStyleClass().add("location-label"); Label path = new Label(installedPath.toString()); path.getStyleClass().add("location-path"); labels.getChildren().addAll(installed, path); Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS); Button open = ghost("Open folder"); Path finalInstalledPath = installedPath; open.setOnAction(event -> { try { context.actions.openFolder(finalInstalledPath); } catch (Exception error) { context.configStore.log().error("Could not open programs folder", error); } }); location.getChildren().addAll(labels, push, open); pane.getChildren().add(location); return pane;
    }

    private void actOnProgram(ProgramEntry entry) {
        if (entry.status() == ProgramStatus.INSTALLED) { launch(entry); return; }
        if (!entry.hasRelease() || entry.manifest() == null) { openGithub(entry); return; }
        VBox page = new VBox(14); page.getStyleClass().add("content-page"); Label title = new Label((entry.status() == ProgramStatus.UPDATE ? "Update " : "Install ") + entry.displayName()); title.getStyleClass().add("page-title"); Label line = new Label("Preparing GitHub release…"); line.getStyleClass().add("body-secondary"); ProgressBar progress = new ProgressBar(0); progress.setMaxWidth(Double.MAX_VALUE); Button cancel = ghost("Cancel"); page.getChildren().addAll(title, line, progress, cancel); router.show(page);
        CancelToken token = new CancelToken(); cancel.setOnAction(event -> { token.cancel(); showProgram(entry); });
        Task<InstallResult> task = new Task<>() { @Override protected InstallResult call() { return context.installer.install(entry, p -> { updateProgress(p.fraction() < 0 ? -1 : p.fraction(), 1); updateMessage(p.status()); }, token); } };
        progress.progressProperty().bind(task.progressProperty()); line.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> { progress.progressProperty().unbind(); line.textProperty().unbind(); InstallResult result = task.getValue(); if (!result.success()) { line.setText(result.message()); return; } if (result.needsMinecraft()) showMinecraftWizard(entry, result.location(), result.hash()); else { line.setText("Installed. Refreshing library…"); programs.clear(); showLibrary(true); } });
        task.setOnFailed(event -> { progress.progressProperty().unbind(); line.textProperty().unbind(); line.setText("Installation failed. Try again."); context.configStore.log().error("Install failed", new Exception(task.getException())); });
        Thread worker = new Thread(task, "wraith-install"); worker.setDaemon(true); worker.start();
    }

    private void showMinecraftWizard(ProgramEntry entry, Path source, String hash) {
        VBox page = new VBox(14); page.getStyleClass().add("content-page"); Label steps = new Label("1  Detect     2  Install     3  Launch"); steps.getStyleClass().add("wizard-steps"); Label title = new Label("Choose a Minecraft profile"); title.getStyleClass().add("page-title"); Label status = new Label("Scanning local Minecraft installs…"); status.getStyleClass().add("body-secondary"); ListView<MinecraftInstall> installs = new ListView<>(); installs.setPrefHeight(210); installs.getStyleClass().add("minecraft-list"); HBox buttons = new HBox(10); buttons.setAlignment(Pos.CENTER_RIGHT); Button browse = ghost("Browse…"); Button rescan = ghost("Rescan"); Button install = primary("Install"); install.setDisable(true); buttons.getChildren().addAll(browse, rescan, install); page.getChildren().addAll(steps, title, status, installs, buttons); router.show(page);
        final MinecraftInstall[] chosen = new MinecraftInstall[1]; installs.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> { chosen[0] = value; install.setDisable(value == null); status.setText(value == null ? "Choose a profile." : value.isForge189() ? "Recommended: Forge 1.8.9." : value.loader() == LoaderType.FORGE ? "Not tested. Confirm before installing." : "Forge for 1.8.9 isn’t installed on this profile."); });
        Runnable scan = () -> scanMinecraft(installs, status); scan.run(); rescan.setOnAction(event -> scan.run());
        browse.setOnAction(event -> { DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Choose a Minecraft folder"); java.io.File folder = chooser.showDialog(stage); if (folder == null) return; MinecraftInstall custom = new LocalInstallDetector(context.paths, context.configStore.log()).custom(folder.toPath()); if (!Files.isDirectory(custom.modsFolder())) { try { Files.createDirectories(custom.modsFolder()); } catch (Exception error) { context.configStore.log().error("Could not create mods folder", error); } } installs.getItems().add(custom); installs.getSelectionModel().select(custom); });
        install.setOnAction(event -> {
            if (chosen[0] == null) return;
            if (chosen[0].loader() != LoaderType.FORGE) { status.setText("Forge for 1.8.9 isn’t installed on this profile. Use Rescan after installing Forge."); return; }
            if (!chosen[0].isForge189()) { Alert warning = new Alert(Alert.AlertType.CONFIRMATION, "This Forge profile is not tested. Continue anyway?"); warning.setHeaderText("Not tested"); if (warning.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) return; }
            if (new MinecraftProcessDetector().running()) { status.setText("Close Minecraft first so the file isn’t locked, then select Retry."); install.setText("Retry"); return; }
            install.setDisable(true); Task<InstallResult> copy = new Task<>() { @Override protected InstallResult call() { return context.minecraftInstaller.install(source, chosen[0]); } };
            copy.setOnSucceeded(done -> { try { Files.deleteIfExists(source); } catch (Exception ignored) { } InstallResult result = copy.getValue(); if (!result.success()) { status.setText(result.message()); install.setDisable(false); return; } rememberMinecraft(entry, chosen[0], result.hash()); showMinecraftSuccess(entry, chosen[0]); });
            copy.setOnFailed(done -> { status.setText("Could not install the Minecraft mod."); install.setDisable(false); }); Thread worker = new Thread(copy, "wraith-minecraft-install"); worker.setDaemon(true); worker.start();
        });
    }

    private void scanMinecraft(ListView<MinecraftInstall> list, Label status) {
        list.getItems().clear(); Task<List<MinecraftInstall>> task = new Task<>() { @Override protected List<MinecraftInstall> call() { return new LocalInstallDetector(context.paths, context.configStore.log()).scan(); } };
        task.setOnSucceeded(event -> { list.getItems().setAll(task.getValue()); status.setText(task.getValue().isEmpty() ? "No local installs found. Use Browse…" : "Choose a Minecraft profile."); if (task.getValue().size() == 1) list.getSelectionModel().select(0); });
        task.setOnFailed(event -> status.setText("Could not scan local installs. Use Browse…")); Thread worker = new Thread(task, "wraith-minecraft-scan"); worker.setDaemon(true); worker.start();
    }

    private void rememberMinecraft(ProgramEntry entry, MinecraftInstall install, String hash) {
        try { InstalledProgram record = new InstalledProgram(); record.owner = entry.owner(); record.repo = entry.repo(); record.tag = entry.version(); record.type = "mod-jar"; record.location = install.modsFolder().toString(); record.minecraftModsPath = install.modsFolder().toString(); record.hash = hash; record.installedAt = Instant.now().toString(); context.installs.put(record); }
        catch (Exception error) { context.configStore.log().error("Could not record Minecraft install", error); }
    }

    private void showMinecraftSuccess(ProgramEntry entry, MinecraftInstall install) {
        VBox page = new VBox(14); page.getStyleClass().add("content-page"); Label check = new Label("✓"); check.getStyleClass().add("success-check"); Label title = new Label("Phantom is installed."); title.getStyleClass().add("page-title"); Label line = new Label("Start your Minecraft now and select the " + install.displayName() + " profile."); line.getStyleClass().add("body-secondary"); Label path = new Label(install.modsFolder().toString()); path.getStyleClass().add("location-path"); HBox buttons = new HBox(10); buttons.setAlignment(Pos.CENTER); Button open = primary("Open mods folder"); open.setOnAction(event -> { try { context.actions.openFolder(install.modsFolder()); } catch (Exception error) { context.configStore.log().error("Could not open mods folder", error); } }); Button uninstall = ghost("Uninstall"); uninstall.setOnAction(event -> { context.minecraftInstaller.uninstall(install); try { context.installs.remove(entry.owner(), entry.repo()); } catch (Exception ignored) { } showLibrary(true); }); buttons.getChildren().add(open); MinecraftLauncherStarter starter = new MinecraftLauncherStarter(context.paths); if (starter.command(install).isPresent()) { Button launcher = ghost("Open Minecraft Launcher"); launcher.setOnAction(event -> { try { starter.start(install); } catch (Exception error) { context.configStore.log().error("Could not start Minecraft Launcher", error); } }); buttons.getChildren().add(launcher); } buttons.getChildren().add(uninstall); page.getChildren().addAll(check, title, line, path, buttons); router.show(page);
    }

    private void launch(ProgramEntry entry) {
        java.util.Optional<InstalledProgram> record = context.installs.find(entry.owner(), entry.repo()); if (record.isEmpty()) { showLibrary(true); return; }
        if ("mod-jar".equals(record.get().type)) { showProgram(entry); return; }
        if (!record.get().trustedToLaunch && !confirmLaunch(entry, record.get())) return;
        try { new ProgramRunner(context.paths).run(Path.of(record.get().location), entry.manifest()); }
        catch (Exception error) { context.configStore.log().error("Could not launch " + entry.repo(), error); debugFooter.setText("Could not launch: " + error.getMessage()); }
    }

    private boolean confirmLaunch(ProgramEntry entry, InstalledProgram record) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION); dialog.setHeaderText("Run downloaded program?"); CheckBox trust = new CheckBox("Don’t ask again for this program"); dialog.getDialogPane().setContent(new VBox(10, new Label("This will run a program downloaded from github.com/" + entry.owner() + "/" + entry.repo() + ". Only continue if you trust it."), trust)); boolean continueLaunch = dialog.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK; if (continueLaunch && trust.isSelected()) try { record.trustedToLaunch = true; context.installs.put(record); } catch (Exception ignored) { } return continueLaunch;
    }

    private void uninstall(ProgramEntry entry) {
        java.util.Optional<InstalledProgram> record = context.installs.find(entry.owner(), entry.repo()); if (record.isEmpty()) return;
        if ("mod-jar".equals(record.get().type)) { try { Files.deleteIfExists(Path.of(record.get().location).resolve("Phantom.jar")); context.installs.remove(entry.owner(), entry.repo()); } catch (Exception error) { debugFooter.setText("Could not uninstall: " + error.getMessage()); } }
        else context.installer.uninstall(entry); showLibrary(true);
    }

    private Node filesTab(ProgramEntry entry) {
        java.util.Optional<InstalledProgram> record = context.installs.find(entry.owner(), entry.repo()); if (record.isEmpty()) return message("Not installed yet."); InstalledProgram value = record.get(); return message("Installed in\n" + value.location + "\n\nVersion " + value.tag + "\nInstalled " + value.installedAt + "\nSHA-256 " + value.hash);
    }

    private String instructions(ProgramEntry entry) {
        if (entry.manifest() != null && entry.manifest().launch() != null && !entry.manifest().launch().instructions().isBlank()) return entry.manifest().launch().instructions();
        String lower = entry.readme().toLowerCase(Locale.ROOT); for (String heading : List.of("launch", "usage", "run", "running", "getting started", "how to use")) { int index = lower.indexOf(heading); if (index >= 0) return entry.readme().substring(index, Math.min(entry.readme().length(), index + 600)); }
        return "No launch instructions found. Check the README on GitHub.";
    }

    private static String shorten(String text) { if (text == null || text.isBlank()) return "No release notes."; String one = text.replaceAll("\\s+", " "); return one.length() > 180 ? one.substring(0, 177) + "…" : one; }

    private String displayName() { return context.config.playerName == null || context.config.playerName.isBlank() ? "User" : context.config.playerName; }
    private void refreshUserChip() { String name = displayName(); if (userName != null) userName.setText(name); if (userInitial != null) userInitial.setText(name.substring(0, 1).toUpperCase()); }
    private void setActiveNavigation(String name) { navigation.forEach((label, button) -> { button.getStyleClass().remove("nav-active"); if (label.equals(name)) button.getStyleClass().add("nav-active"); }); }

    private void showSettings() {
        setActiveNavigation("Settings");
        List<String> savedOwners = context.config.owners == null || context.config.owners.isEmpty() ? List.of("yxzroot") : context.config.owners;
        VBox pane = new VBox(12); pane.getStyleClass().add("content-page");
        Label title = new Label("Settings"); title.getStyleClass().add("page-title");
        Label subtitle = new Label("Most people only need to choose where their projects come from."); subtitle.getStyleClass().add("settings-subtitle");

        VBox source = new VBox(7); source.getStyleClass().add("settings-section");
        Label sourceTitle = new Label("Where should Wraith look?"); sourceTitle.getStyleClass().add("settings-section-title");
        Label ownerLabel = new Label("Main GitHub username"); ownerLabel.getStyleClass().add("setting-label");
        TextField owner = new TextField(savedOwners.get(0)); owner.setPromptText("yxzroot"); owner.getStyleClass().add("settings-field");
        Label ownerNote = new Label("Wraith shows public projects published by this GitHub user."); ownerNote.getStyleClass().add("body-secondary");
        Label extrasLabel = new Label("More usernames (optional)"); extrasLabel.getStyleClass().add("setting-label");
        TextField extras = new TextField(savedOwners.size() > 1 ? String.join(", ", savedOwners.subList(1, savedOwners.size())) : ""); extras.setPromptText("friend-one, friend-two"); extras.getStyleClass().add("settings-field");
        CheckBox withoutRelease = new CheckBox("Show projects that are not ready to download yet"); withoutRelease.setSelected(context.config.showWithoutReleases);
        source.getChildren().addAll(sourceTitle, ownerLabel, owner, ownerNote, extrasLabel, extras, withoutRelease);

        VBox storage = new VBox(7); storage.getStyleClass().add("settings-section");
        Label storageTitle = new Label("Where are downloads saved?"); storageTitle.getStyleClass().add("settings-section-title");
        Label folder = new Label(context.paths.programsDirectory().toString()); folder.getStyleClass().add("location-path");
        Button open = ghost("Open downloads folder"); open.setOnAction(event -> { try { Files.createDirectories(context.paths.programsDirectory()); context.actions.openFolder(context.paths.programsDirectory()); } catch (Exception error) { context.configStore.log().error("Could not open programs folder", error); } });
        storage.getChildren().addAll(storageTitle, folder, open);

        VBox advanced = new VBox(8); advanced.getStyleClass().add("settings-section");
        Label advancedTitle = new Label("Advanced options"); advancedTitle.getStyleClass().add("settings-section-title");
        CheckBox forks = new CheckBox("Also show copied projects (forks)"); forks.setSelected(context.config.includeForks);
        CheckBox archived = new CheckBox("Also show old archived projects"); archived.setSelected(context.config.includeArchived);
        Label tokenLabel = new Label("GitHub token (only if GitHub asks you to wait)"); tokenLabel.getStyleClass().add("setting-label");
        PasswordField token = new PasswordField(); token.setText(context.config.githubToken == null ? "" : context.config.githubToken); token.setPromptText("Leave empty for normal use"); token.getStyleClass().add("settings-field");
        Label tokenNote = new Label("Only needed when you hit GitHub’s request limit. It stays on this computer and is never logged."); tokenNote.setWrapText(true); tokenNote.getStyleClass().add("body-secondary");
        CheckBox systemFrame = new CheckBox("Use my computer’s normal window border"); systemFrame.setSelected(context.config.useSystemWindowFrame);
        Label frameNote = new Label("Turn this on if Wraith’s border looks odd. Restart Wraith after changing it."); frameNote.setWrapText(true); frameNote.getStyleClass().add("body-secondary");
        advanced.getChildren().addAll(advancedTitle, forks, archived, tokenLabel, token, tokenNote, systemFrame, frameNote);
        boolean advancedOpen = context.config.includeForks || context.config.includeArchived || (context.config.githubToken != null && !context.config.githubToken.isBlank()) || context.config.useSystemWindowFrame;
        advanced.setVisible(advancedOpen); advanced.setManaged(advancedOpen);
        Button advancedToggle = ghost(advancedOpen ? "Hide advanced options" : "Show advanced options"); advancedToggle.setOnAction(event -> { boolean show = !advanced.isVisible(); advanced.setVisible(show); advanced.setManaged(show); advancedToggle.setText(show ? "Hide advanced options" : "Show advanced options"); });

        HBox actions = new HBox(10); actions.setAlignment(Pos.CENTER_LEFT); Button save = primary("Save changes"); Button clear = ghost("Clear saved GitHub data"); Button rate = ghost("Check GitHub connection"); Label result = new Label(); result.setWrapText(true); result.getStyleClass().add("body-secondary");
        clear.setOnAction(event -> { context.cache.clear(); result.setText("Saved GitHub information was cleared. The Library will refresh next time you open it."); });
        rate.setOnAction(event -> checkRateLimit(result, rate));
        save.setOnAction(event -> {
            List<String> owners = new ArrayList<>();
            if (!owner.getText().isBlank()) owners.add(owner.getText().trim());
            owners.addAll(java.util.Arrays.stream(extras.getText().split(",")).map(String::trim).filter(value -> !value.isBlank()).toList());
            context.config.owners = owners.isEmpty() ? new ArrayList<>(List.of("yxzroot")) : owners;
            context.config.includeForks = forks.isSelected(); context.config.includeArchived = archived.isSelected(); context.config.showWithoutReleases = withoutRelease.isSelected(); context.config.githubToken = token.getText(); context.config.useSystemWindowFrame = systemFrame.isSelected(); context.saveQuietly();
            result.setText("Saved. Open Library and press refresh to load your chosen projects." + (systemFrame.isSelected() ? " Restart Wraith to use the normal window border." : ""));
        });
        actions.getChildren().addAll(save, clear, rate, result);
        Button tutorial = ghost("Show the getting started guide again"); tutorial.setOnAction(event -> showTutorial());
        Label about = new Label("About · Wraith " + context.info.version() + " · " + context.paths.os() + "\nSettings are stored only on this computer."); about.getStyleClass().add("body-secondary");
        pane.getChildren().addAll(title, subtitle, source, storage, advancedToggle, advanced, actions, tutorial, about);
        ScrollPane scroll = new ScrollPane(pane); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("library-scroll"); router.show(scroll);
    }

    private void checkRateLimit(Label result, Button button) {
        button.setDisable(true); result.setText("Checking GitHub rate limit…"); Task<String> task = new Task<>() { @Override protected String call() throws Exception { return context.rateLimit.check(); } }; task.setOnSucceeded(event -> { result.setText(task.getValue()); button.setDisable(false); }); task.setOnFailed(event -> { result.setText("Could not check the GitHub rate limit."); button.setDisable(false); }); Thread worker = new Thread(task, "wraith-rate-limit"); worker.setDaemon(true); worker.start();
    }

    private Node skeleton() { HBox box = new HBox(16); box.getStyleClass().add("skeleton-row"); for (int i = 0; i < 2; i++) { Region item = new Region(); item.setPrefSize(310, 150); item.getStyleClass().add("skeleton-card"); box.getChildren().add(item); } return box; }
    private Node message(String text) { Label label = new Label(text); label.setWrapText(true); label.getStyleClass().add("empty-message"); return center(label); }
    private Label status(ProgramStatus value) { Label badge = new Label("●  " + value.label()); badge.getStyleClass().addAll("status-badge", "status-" + value.name().toLowerCase()); return badge; }
    private Button primary(String text) { Button button = new Button(text); button.getStyleClass().add("primary-button"); return button; }
    private Button ghost(String text) { Button button = new Button(text); button.getStyleClass().add("ghost-button"); return button; }
    private Button chrome(String text) { Button button = new Button(text); button.getStyleClass().add("chrome-button"); return button; }
    private Node center(Node child) { StackPane pane = new StackPane(child); pane.setAlignment(Pos.CENTER); return pane; }
    private void openGithub(ProgramEntry entry) { try { context.actions.openUrl(entry.githubUrl()); } catch (Exception error) { context.configStore.log().error("Could not open GitHub", error); } }
}
