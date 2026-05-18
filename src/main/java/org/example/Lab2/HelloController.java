package org.example.Lab2;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HelloController {

    @FXML private TabPane editorTabPane;
    @FXML private TextArea textArea2;
    @FXML private TableView<Token> resultTable;
    @FXML private TableColumn<Token, Integer> codeCol;
    @FXML private TableColumn<Token, String> typeCol;
    @FXML private TableColumn<Token, String> lexemeCol;
    @FXML private TableColumn<Token, String> locationCol;
    @FXML private TableView<SyntaxError> syntaxErrorTable;
    @FXML private TableColumn<SyntaxError, String> errorFragmentCol;
    @FXML private TableColumn<SyntaxError, String> errorLocationCol;
    @FXML private TableColumn<SyntaxError, String> errorDescCol;
    @FXML private TableView<SearchResult> searchResultTable;
    @FXML private TableColumn<SearchResult, String> searchSubstringCol;
    @FXML private TableColumn<SearchResult, String> searchStartCol;
    @FXML private TableColumn<SearchResult, Integer> searchLengthCol;
    @FXML private TableColumn<SearchResult, String> searchTypeCol;
    @FXML private ComboBox<String> searchTypeComboBox;


    @FXML private Menu menuFile, menuEdit, menuText, menuRun, menuLang, menuHelp;
    @FXML private MenuItem menuItemNew, menuItemOpen, menuItemSave, menuItemSaveAs, menuItemExit;
    @FXML private MenuItem menuItemUndo, menuItemRedo, menuItemCut, menuItemCopy, menuItemPaste, menuItemDelete, menuItemSelectAll, menuItemIncFont, menuItemDecFont;
    @FXML private MenuItem menuItemTask, menuItemGrammar, menuItemClass, menuItemMethod, menuItemTest, menuItemLit, menuItemCode;
    @FXML private MenuItem menuItemStart, menuItemHelp, menuItemAbout;
    @FXML private Tab tabResults, tabSyntaxErrors, tabMessages, tabSearch;
    @FXML private Button btnSearch;
    @FXML private Label statusLabel;
    @FXML private Label caretPositionLabel;

    private ObservableList<Token> tokenData = FXCollections.observableArrayList();
    private ObservableList<SyntaxError> syntaxErrorData = FXCollections.observableArrayList();
    private ObservableList<SearchResult> searchResultData = FXCollections.observableArrayList();

    private double fontSize = 14;
    private int newFileCounter = 1;


    private Map<Tab, CodeArea> tabTextAreas = new HashMap<>();
    private Map<Tab, File> tabFiles = new HashMap<>();
    private Map<Tab, Boolean> tabModified = new HashMap<>();

    private boolean isRussian = true;

    @FXML
    public void initialize() {
        textArea2.setEditable(false);

        codeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        lexemeCol.setCellValueFactory(new PropertyValueFactory<>("text"));
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        resultTable.setItems(tokenData);

        resultTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null && newSel.getCode() == 17) focusOnRange(newSel.getGlobalStart(), newSel.getGlobalEnd());
        });

        errorFragmentCol.setCellValueFactory(new PropertyValueFactory<>("fragment"));
        errorLocationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        errorDescCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        syntaxErrorTable.setItems(syntaxErrorData);

        syntaxErrorTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) focusOnRange(newSel.getGlobalStart(), newSel.getGlobalEnd());
        });

        searchTypeComboBox.setItems(FXCollections.observableArrayList(
            "Пользовательские логины", "C++ комментарии", "12-часовой формат времени", "C++ комментарии (Автомат)"
        ));

        searchSubstringCol.setCellValueFactory(new PropertyValueFactory<>("substring"));
        searchStartCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        searchLengthCol.setCellValueFactory(new PropertyValueFactory<>("length"));
        searchTypeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        searchResultTable.setItems(searchResultData);

        searchResultTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) focusOnRange(newSel.getStartIndex(), newSel.getStartIndex() + newSel.getLength());
        });


        setupDragAndDrop();


        javafx.application.Platform.runLater(() -> {
            handleNew();
        });


        editorTabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getWindow().setOnCloseRequest(event -> {
                    if (!closeAllTabsSafely()) event.consume();
                });
                setupHotkeys(newScene);
            }
        });

        editorTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateTitleAndStatus());
    }



    private void createNewTab(String title, String content, File file) {
        Tab tab = new Tab(title);

        CodeArea codeArea = new CodeArea();
        codeArea.replaceText(0, 0, content);
        codeArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: " + fontSize + "px;");


        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));


        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            tabModified.put(tab, true);
            updateTitleAndStatus();
            applyHighlighting(codeArea, newText);
        });


        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCaretPosition(codeArea));

        tab.setContent(codeArea);

        tabTextAreas.put(tab, codeArea);
        tabFiles.put(tab, file);
        tabModified.put(tab, false);

        tab.setOnCloseRequest(event -> {
            if (!checkSaveTab(tab)) event.consume();
            else {
                tabTextAreas.remove(tab);
                tabFiles.remove(tab);
                tabModified.remove(tab);
            }
        });

        editorTabPane.getTabs().add(tab);
        editorTabPane.getSelectionModel().select(tab);
        updateTitleAndStatus();


        applyHighlighting(codeArea, content);
    }

    private CodeArea getCurrentTextArea() {
        Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
        return selectedTab != null ? tabTextAreas.get(selectedTab) : null;
    }



    private void applyHighlighting(CodeArea codeArea, String text) {
        if (text.isEmpty()) return;

        List<Token> tokens = Scanner.analyze(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;

        for (Token t : tokens) {
            int start = t.getGlobalStart();
            int end = t.getGlobalEnd() + 1;


            if (start > lastEnd) {
                spansBuilder.add(Collections.emptyList(), start - lastEnd);
            }

            String styleClass = getStyleClassForToken(t.getCode());
            spansBuilder.add(Collections.singleton(styleClass), end - start);
            lastEnd = end;
        }

        if (text.length() > lastEnd) {
            spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        }

        codeArea.setStyleSpans(0, spansBuilder.create());
    }

    private String getStyleClassForToken(int code) {
        return switch (code) {
            case 14 -> "keyword";
            case 2 -> "identifier";
            case 5 -> "string";
            case 1, 6 -> "number";
            case 10, 3, 4, 16 -> "operator";
            case 17 -> "error";
            default -> "default";
        };
    }

    private void updateCaretPosition(CodeArea codeArea) {
        if (codeArea == null) return;
        int caret = codeArea.getCaretPosition();
        String text = codeArea.getText();
        int line = 1, col = 1;
        for (int i = 0; i < caret; i++) {
            if (i < text.length() && text.charAt(i) == '\n') { line++; col = 1; }
            else col++;
        }
        caretPositionLabel.setText((isRussian ? "Стр: " : "Ln: ") + line + (isRussian ? ", Симв: " : ", Col: ") + col);
    }

    private void focusOnRange(int start, int end) {
        CodeArea ta = getCurrentTextArea();
        if (ta != null) {
            ta.requestFocus();
            ta.selectRange(start, end);
        }
    }



    private void setupDragAndDrop() {
        editorTabPane.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });

        editorTabPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                for (File file : db.getFiles()) openFileIntoNewTab(file);
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    private void setupHotkeys(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::handleSave);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), this::handleNew);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), this::handleOpen);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN), () -> {
            Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null) {
                if (checkSaveTab(selectedTab)) editorTabPane.getTabs().remove(selectedTab);
            }
        });
    }


    @FXML private void setLangRu() { isRussian = true; applyLanguage(); }
    @FXML private void setLangEn() { isRussian = false; applyLanguage(); }

    private void applyLanguage() {
        menuFile.setText(isRussian ? "Файл" : "File");
        menuItemNew.setText(isRussian ? "Создать" : "New");
        menuItemOpen.setText(isRussian ? "Открыть" : "Open");
        menuItemSave.setText(isRussian ? "Сохранить" : "Save");
        menuItemSaveAs.setText(isRussian ? "Сохранить как" : "Save As");
        menuItemExit.setText(isRussian ? "Выход" : "Exit");

        menuEdit.setText(isRussian ? "Правка" : "Edit");
        menuItemUndo.setText(isRussian ? "Отменить" : "Undo");
        menuItemRedo.setText(isRussian ? "Повторить" : "Redo");
        menuItemCut.setText(isRussian ? "Вырезать" : "Cut");
        menuItemCopy.setText(isRussian ? "Копировать" : "Copy");
        menuItemPaste.setText(isRussian ? "Вставить" : "Paste");
        menuItemDelete.setText(isRussian ? "Удалить" : "Delete");
        menuItemSelectAll.setText(isRussian ? "Выделить все" : "Select All");
        menuItemIncFont.setText(isRussian ? "Увеличить шрифт" : "Increase Font");
        menuItemDecFont.setText(isRussian ? "Уменьшить шрифт" : "Decrease Font");

        menuText.setText(isRussian ? "Текст" : "Text");
        menuItemTask.setText(isRussian ? "Постановка задачи" : "Problem Statement");
        menuItemGrammar.setText(isRussian ? "Грамматика" : "Grammar");
        menuItemClass.setText(isRussian ? "Классификация грамматики" : "Grammar Classification");
        menuItemMethod.setText(isRussian ? "Метод анализа" : "Analysis Method");
        menuItemTest.setText(isRussian ? "Тестовый пример" : "Test Example");
        menuItemLit.setText(isRussian ? "Список литературы" : "Literature List");
        menuItemCode.setText(isRussian ? "Исходный код программы" : "Source Code");

        menuRun.setText(isRussian ? "Пуск" : "Run");
        menuItemStart.setText(isRussian ? "Запуск анализатора" : "Run Analyzer");
        menuLang.setText(isRussian ? "Язык" : "Language");
        menuHelp.setText(isRussian ? "Справка" : "Help");

        tabResults.setText(isRussian ? "Результаты" : "Results");
        tabSyntaxErrors.setText(isRussian ? "Синтаксические ошибки" : "Syntax Errors");
        tabMessages.setText(isRussian ? "Сообщения" : "Messages");
        tabSearch.setText(isRussian ? "Поиск (Regex)" : "Search (Regex)");
        btnSearch.setText(isRussian ? "Искать" : "Search");

        updateTitleAndStatus();
    }


    @FXML private void increaseFont() { fontSize += 2; updateFont(); }
    @FXML private void decreaseFont() { if (fontSize > 8) fontSize -= 2; updateFont(); }

    private void updateFont() {
        for (CodeArea ta : tabTextAreas.values()) {
            ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: " + fontSize + "px;");
        }
        textArea2.setStyle("-fx-font-size: " + fontSize + "px;");
    }

    private void updateTitleAndStatus() {
        if (editorTabPane.getScene() == null || editorTabPane.getScene().getWindow() == null) {
            return;
        }

        Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
        Stage stage = (Stage) editorTabPane.getScene().getWindow();
        if (selectedTab != null) {
            boolean modified = tabModified.getOrDefault(selectedTab, false);
            File file = tabFiles.get(selectedTab);
            String name = (file == null) ? (isRussian ? "Без имени" : "Untitled") : file.getName();
            selectedTab.setText(name + (modified ? " *" : ""));
            stage.setTitle((isRussian ? "Текстовый редактор - " : "Text Editor - ") + name + (modified ? " *" : ""));
            statusLabel.setText(isRussian ? "Готово" : "Ready");
            updateCaretPosition(getCurrentTextArea());
        } else {
            stage.setTitle(isRussian ? "Текстовый редактор" : "Text Editor");
            statusLabel.setText(isRussian ? "Нет открытых файлов" : "No open files");
            caretPositionLabel.setText("");
        }
    }

    private boolean checkSaveTab(Tab tab) {
        if (!tabModified.getOrDefault(tab, false)) return true;
        editorTabPane.getSelectionModel().select(tab);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(isRussian ? "Несохранённые изменения" : "Unsaved Changes");
        alert.setHeaderText(isRussian ? "Файл был изменён" : "File modified");
        alert.setContentText(isRussian ? "Сохранить изменения перед продолжением?" : "Save changes before closing?");

        ButtonType saveBtn = new ButtonType(isRussian ? "Сохранить" : "Save");
        ButtonType dontSaveBtn = new ButtonType(isRussian ? "Не сохранять" : "Don't Save");
        ButtonType cancelBtn = new ButtonType(isRussian ? "Отмена" : "Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(saveBtn, dontSaveBtn, cancelBtn);
        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == saveBtn) { handleSave(); return !tabModified.get(tab); }
            else if (result.get() == dontSaveBtn) return true;
        }
        return false;
    }

    private boolean closeAllTabsSafely() {
        for (Tab tab : new ArrayList<>(editorTabPane.getTabs())) {
            if (!checkSaveTab(tab)) return false;
        }
        return true;
    }

    @FXML private void handleNew() {
        createNewTab((isRussian ? "Новый файл " : "New File ") + newFileCounter++, "", null);
        textArea2.appendText((isRussian ? "Создан новый документ\n" : "Created new document\n"));
    }

    @FXML private void handleOpen() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(isRussian ? "Открыть файл" : "Open File");
        File file = fileChooser.showOpenDialog(editorTabPane.getScene().getWindow());
        if (file != null) openFileIntoNewTab(file);
    }

    private void openFileIntoNewTab(File file) {
        try {
            String content = Files.readString(file.toPath());
            createNewTab(file.getName(), content, file);
            textArea2.appendText((isRussian ? "Файл открыт: " : "File opened: ") + file.getName() + "\n");
        } catch (IOException e) {
            showInfo(isRussian ? "Ошибка открытия файла" : "Error opening file");
        }
    }

    @FXML public void handleSave() {
        Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return;
        File file = tabFiles.get(selectedTab);

        if (file == null) { handleSaveAs(); return; }

        try {
            Files.writeString(file.toPath(), tabTextAreas.get(selectedTab).getText());
            tabModified.put(selectedTab, false);
            updateTitleAndStatus();
            textArea2.appendText((isRussian ? "Файл сохранён\n" : "File saved\n"));
            statusLabel.setText(isRussian ? "Файл сохранён" : "File saved");
        } catch (Exception e) {
            textArea2.appendText((isRussian ? "Ошибка сохранения\n" : "Save error\n"));
        }
    }

    @FXML private void handleSaveAs() {
        Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(isRussian ? "Сохранить файл как" : "Save File As");
        File file = fileChooser.showSaveDialog(editorTabPane.getScene().getWindow());

        if (file != null) {
            try {
                Files.writeString(file.toPath(), tabTextAreas.get(selectedTab).getText());
                tabFiles.put(selectedTab, file);
                tabModified.put(selectedTab, false);
                updateTitleAndStatus();
                textArea2.appendText((isRussian ? "Файл сохранён: " : "File saved: ") + file.getName() + "\n");
            } catch (IOException e) {
                showInfo(isRussian ? "Ошибка сохранения файла" : "Error saving file");
            }
        }
    }

    @FXML public void handleExit() {
        if (closeAllTabsSafely()) ((Stage) editorTabPane.getScene().getWindow()).close();
    }

    @FXML public void handleUndo() { CodeArea ta = getCurrentTextArea(); if (ta != null) ta.undo(); }
    @FXML public void handleRedo() { CodeArea ta = getCurrentTextArea(); if (ta != null) ta.redo(); }
    @FXML public void handleCut() { CodeArea ta = getCurrentTextArea(); if (ta != null) ta.cut(); }
    @FXML public void handleCopy() { CodeArea ta = getCurrentTextArea(); if (ta != null) ta.copy(); }
    @FXML public void handlePaste() { CodeArea ta = getCurrentTextArea(); if (ta != null) ta.paste(); }
    @FXML public void handleDelete() { CodeArea ta = getCurrentTextArea(); if (ta != null) ta.replaceSelection(""); }
    @FXML public void handleSelectAll() { CodeArea ta = getCurrentTextArea(); if (ta != null) ta.selectAll(); }


    @FXML public void handleRun() {
        CodeArea ta = getCurrentTextArea();
        if (ta == null || ta.getText().isEmpty()) {
            textArea2.setText(isRussian ? "Текст для анализа отсутствует." : "No text to analyze.");
            tokenData.clear(); syntaxErrorData.clear();
            return;
        }

        List<Token> tokens = Scanner.analyze(ta.getText());
        tokenData.clear(); tokenData.addAll(tokens);
        long lexErrorCount = tokens.stream().filter(t -> t.getCode() == 17).count();

        Parser.ParseResult result = Parser.parse(tokens);
        syntaxErrorData.clear(); syntaxErrorData.addAll(result.errors);

        StringBuilder report = new StringBuilder();

        if (lexErrorCount > 0 || result.errors.size() > 0) {
            report.append(String.format(isRussian ? "Анализ завершен. Лексических ошибок: %d, Синтаксических/Семантических ошибок: %d.\n\n"
                    : "Analysis finished. Lexical errors: %d, Syntax/Semantic errors: %d.\n\n", lexErrorCount, result.errors.size()));
            statusLabel.setText(isRussian ? "Найдены ошибки" : "Errors found");
        } else {
            report.append(isRussian ? "Анализ успешно завершен. Ошибок не обнаружено.\n\n" : "Analysis completed successfully. No errors.\n\n");
            statusLabel.setText(isRussian ? "Анализ успешен" : "Analysis successful");
        }

        report.append(isRussian ? "--- АБСТРАКТНОЕ СИНТАКСИЧЕСКОЕ ДЕРЕВО (AST) ---\n" : "--- ABSTRACT SYNTAX TREE (AST) ---\n");
        if (result.ast != null) {
            report.append(result.ast.print("", true));
        } else {
            report.append(isRussian ? "Дерево не построено из-за критических ошибок.\n" : "Tree not built due to critical errors.\n");
        }

        textArea2.setText(report.toString());
    }

    @FXML public void handleRegexSearch() {
        CodeArea ta = getCurrentTextArea();
        if (ta == null || ta.getText().isEmpty()) { showInfo(isRussian ? "Текст отсутствует." : "No text."); return; }

        String selectedType = searchTypeComboBox.getValue();
        if (selectedType == null) { showInfo(isRussian ? "Выберите паттерн." : "Select a pattern."); return; }

        String text = ta.getText();
        String regex = ""; String typeDesc = ""; int flags = 0;

        switch (selectedType) {
            case "Пользовательские логины": regex = "\\b[a-zA-Z_.-][a-zA-Z0-9_.-]*\\b"; typeDesc = "Логин"; break;
            case "C++ комментарии": regex = "(//[^\\n]*)|(/\\*.*?\\*/)"; flags = Pattern.DOTALL; typeDesc = "Комментарий"; break;
            case "12-часовой формат времени": regex = "\\b(0?[1-9]|1[0-2]):[0-5][0-9]\\s*(?i)[ap]m\\b"; typeDesc = "Время"; break;
        }

        searchResultData.clear();

        if (selectedType.equals("C++ комментарии (Автомат)")) {
            int state = 0, startIdx = -1, len = text.length();
            for (int i = 0; i < len; i++) {
                char c = text.charAt(i);
                switch (state) {
                    case 0: if (c == '/') { state = 1; startIdx = i; } break;
                    case 1: if (c == '/') state = 2; else if (c == '*') state = 3; else { i = startIdx; state = 0; } break;
                    case 2: if (c == '\n' || i == len - 1) { int endIdx = (c == '\n') ? i : i + 1; searchResultData.add(new SearchResult(text.substring(startIdx, endIdx), startIdx, endIdx - startIdx, "Комментарий (DFA)", "")); state = 0; } break;
                    case 3: if (c == '*') state = 4; break;
                    case 4: if (c == '/') { int endIdx = i + 1; searchResultData.add(new SearchResult(text.substring(startIdx, endIdx), startIdx, endIdx - startIdx, "Комментарий (DFA)", "")); state = 0; } else if (c != '*') state = 3; break;
                }
            }
        } else {
            Pattern pattern = (flags == 0) ? Pattern.compile(regex) : Pattern.compile(regex, flags);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) searchResultData.add(new SearchResult(matcher.group(), matcher.start(), matcher.end() - matcher.start(), typeDesc, ""));
        }

        textArea2.setText((isRussian ? "Найдено совпадений: " : "Matches found: ") + searchResultData.size());
    }


    private void showTextWindow(String title, String content) {
        Stage stage = new Stage();
        stage.setTitle(title);

        TextArea infoArea = new TextArea();
        infoArea.setEditable(false);
        infoArea.setWrapText(true);
        infoArea.setText(content);
        infoArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 14px;");

        Scene scene = new Scene(new StackPane(infoArea), 700, 500);
        stage.setScene(scene);
        stage.show();
    }

    @FXML public void handleProblemStatement() {
        showTextWindow("Постановка задачи", """
            ПОСТАНОВКА ЗАДАЧИ
            
            Разработать синтаксический анализатор (парсер) в соответствии
            с индивидуальным вариантом курсовой работы, интегрировать его 
            в приложение из лабораторной работы №1 и обеспечить наглядный 
            вывод результатов анализа.
            
            Вариант задания:
            Тема: Лямбда-выражения языка Java (с арифметическими операциями).
            
            Примеры корректных входных строк:
            1. Function op = (int x, int y) -> x + y;
            2. mult = x -> x * 2;
            3. () -> (10 + 5) / 2;
            """);
    }

    @FXML public void handleGrammar() {
        showTextWindow("Грамматика", """
            ПОЛНОЕ ОПРЕДЕЛЕНИЕ ГРАММАТИКИ G[<Z>]
            
            1. <Z> -> <LVal> = <Lambda> ; | <Lambda> ;
            2. <LVal> -> <Type> <Identifier> | <Identifier> <Identifier> | <Identifier>
            3. <Lambda> -> <Params> -> <Expr>
            4. <Params> -> ( <ParamList> ) | ( ) | <Identifier>
            5. <ParamList> -> <Param> <ParamListTail>
            6. <ParamListTail> -> , <Param> <ParamListTail> | ε
            7. <Param> -> <Type> <Identifier> | <Identifier>
            8. <Expr> -> <Term> <ExprTail>
            9. <ExprTail> -> <Op> <Term> <ExprTail> | ε
            10. <Op> -> + | - | * | /
            11. <Term> -> <Identifier> | <Number> | ( <Expr> )
            12. <Type> -> int | double | float | boolean | char | byte | short | long | var | void | String | const
            13. <Identifier> -> letter <IdentifierRem> | _ <IdentifierRem> | $ <IdentifierRem>
            14. <IdentifierRem> -> letter <IdentifierRem> | digit <IdentifierRem> | _ <IdentifierRem> | $ <IdentifierRem> | ε
            15. <Number> -> <Integer> <Fraction>
            16. <Integer> -> digit <IntegerTail>
            17. <IntegerTail> -> digit <IntegerTail> | ε
            18. <Fraction> -> . <Integer> | ε
            """);
    }

    @FXML public void handleGrammarClassification() {
        showTextWindow("Классификация грамматики", """
            КЛАССИФИКАЦИЯ ГРАММАТИКИ (ПО ХОМСКОМУ)
            
            Согласно классификации Хомского, полученная порождающая грамматика G[<Z>] 
            соответствует типу контекстно-свободных, так как правая часть каждой редукции 
            начинается либо с терминального символа, либо с нетерминального, 
            принадлежащего объединённому словарю. 
            
            Грамматика G[Z] не является автоматной, так как не все её редукции начинаются 
            с терминального символа. По этой же причине данная грамматика не является S-грамматикой.
            """);
    }

    @FXML public void handleAnalysisMethod() {
        showTextWindow("Метод анализа", """
            МЕТОД АНАЛИЗА И НЕЙТРАЛИЗАЦИИ ОШИБОК
            
            Метод анализа:
            Алгоритм синтаксического анализа реализован методом рекурсивного спуска 
            (нисходящий синтаксический анализ). Для каждого нетерминального символа 
            грамматики написана отдельная программная функция.
            
            Диагностика и нейтрализация ошибок:
            Реализован алгоритм на базе метода Айронса (в практической реализации 
            известный как метод синхронизирующих токенов).
            При обнаружении неверного символа фиксируется ошибка, и парсер пропускает 
            символы до тех пор, пока не встретит один из токенов множества Follow 
            (например, ;, ) или ->). После этого разбор возобновляется.
            """);
    }

    @FXML public void handleTestExample() {
        showTextWindow("Тестовые примеры", """
            ТЕСТОВЫЕ ПРИМЕРЫ
            
            Пример 1: Корректное выражение
            Function add = (int a, int b) -> a + b;
            
            Пример 2: Синтаксическая ошибка (пропущена скобка)
            Function add = (int a, int b -> a + b;
            Результат: Ошибка "Ожидалось ')'".
            
            Пример 3: Отсутствие параметров у лямбды
            var obj = -> 5 + 5;
            Результат: Ошибка "Ожидались параметры лямбда-выражения".
            
            Пример 4: Лексически неверный символ
            (x, y) -> x + y @ 5;
            Результат: Лексическая ошибка на '@', синтаксическая на '5'.
            """);
    }

    @FXML public void handleLiteratureList() {
        showTextWindow("Список литературы", """
            СПИСОК ЛИТЕРАТУРЫ
            
            1. Ахо А., Лам М., Сети Р., Ульман Дж. Компиляторы: принципы, технологии и инструментарий. 
               — М.: Вильямс, 2008.
            2. Шилдт Г. Java. Полное руководство. — М.: Вильямс, 2018.
            3. Карпов Ю. Г. Теория автоматов. — СПб.: Питер, 2002.
            """);
    }

    @FXML public void handleListing() {
        showTextWindow("Исходный код программы", """
            /* =========================================
               ЛИСТИНГ ИСХОДНОГО КОДА (Фрагмент)
               ========================================= */
            package org.example.Lab2;
            import java.util.List;
            
            public class Parser {
                private final List<Token> tokens;
                private int pos;
                private final List<SyntaxError> errors;
                private boolean panicMode = false;
                
                public static List<SyntaxError> parse(List<Token> tokens) {
                    Parser parser = new Parser(tokens);
                    parser.parseZ();
                    return parser.errors;
                }
                
            }
            """);
    }

    @FXML private void handleCallingHelp() {
        showInfo(isRussian ? "Справочная система...\nПрограмма поддерживает горячие клавиши:\nCtrl+N - Создать\nCtrl+O - Открыть\nCtrl+S - Сохранить\nCtrl+W - Закрыть вкладку" : "Help system...\nSupported hotkeys:\nCtrl+N - New\nCtrl+O - Open\nCtrl+S - Save\nCtrl+W - Close Tab");
    }

    @FXML public void handleAbout() {
        showInfo("Text Editor Compiler\n" + (isRussian ? "Версия 1.0\nРазработчик: Гусейнов Р.А." : "Version 1.0\nDeveloper: Guseynov R.A."));
    }

    private void showInfo(String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }
}