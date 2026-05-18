package org.example.Lab2;

import java.util.*;

public class Parser {

    private final List<Token> tokens;
    private int pos;
    private final List<SyntaxError> errors;

    private final Map<String, String> symbolTable = new HashMap<>();
    private AstNode rootAst = null; // Корень дерева

    public Parser(List<Token> allTokens) {
        this.tokens = new ArrayList<>();
        this.errors = new ArrayList<>();
        for (Token t : allTokens) {
            if (t.getType().startsWith("ошибка")) {
                this.errors.add(new SyntaxError(t.getText(), t.getLocation(), "Лексическая ошибка: " + t.getType(), t.getGlobalStart(), t.getGlobalEnd()));
            } else if (t.getCode() != 11) {
                this.tokens.add(t);
            }
        }
        this.pos = 0;
    }

    public static ParseResult parse(List<Token> tokens) {
        Parser parser = new Parser(tokens);
        parser.rootAst = parser.parseZ();
        return new ParseResult(parser.errors, parser.rootAst);
    }

    public static class ParseResult {
        public List<SyntaxError> errors;
        public AstNode ast;
        public ParseResult(List<SyntaxError> errors, AstNode ast) {
            this.errors = errors;
            this.ast = ast;
        }
    }

    private void addError(String description) {
        if (pos < tokens.size()) {
            Token t = tokens.get(pos);
            errors.add(new SyntaxError(t.getText(), t.getLocation(), description, t.getGlobalStart(), t.getGlobalEnd()));
        } else if (!tokens.isEmpty()) {
            Token t = tokens.get(tokens.size() - 1);
            errors.add(new SyntaxError("EOF", "конец файла", description, t.getGlobalEnd(), t.getGlobalEnd()));
        }
    }

    private void recover(String... follow) {
        Set<String> followSet = new HashSet<>(Arrays.asList(follow));
        followSet.add(";"); followSet.add("}");
        int tempPos = pos;
        boolean foundSync = false;
        while (tempPos < tokens.size()) {
            if (followSet.contains(tokens.get(tempPos).getText())) { foundSync = true; break; }
            tempPos++;
        }
        if (foundSync) {
            while (!isEOF()) {
                Token c = current();
                if (followSet.contains(c.getText())) break;
                advance();
            }
        } else {
            if (!isEOF()) advance();
        }
    }

    private boolean match(String expectedText, String... follow) {
        Token c = current();
        if (c != null && c.getText().equals(expectedText)) {
            advance();
            return true;
        }
        addError("Ожидалось '" + expectedText + "'");
        recover(follow);
        return false;
    }

    private Token current() { return pos < tokens.size() ? tokens.get(pos) : null; }
    private void advance() { if (pos < tokens.size()) pos++; }
    private boolean isEOF() { return pos >= tokens.size(); }

    private AstNode parseZ() {
        if (isEOF()) return null;

        String lhsType = null;
        String lhsName = null;

        int tempPos = pos;
        boolean hasAssignment = false;
        while (tempPos < tokens.size() && !tokens.get(tempPos).getText().equals(";") && !tokens.get(tempPos).getText().equals("->")) {
            if (tokens.get(tempPos).getText().equals("=")) { hasAssignment = true; break; }
            tempPos++;
        }

        if (hasAssignment) {
            Token c = current();
            if (c != null && (c.getCode() == 14 || c.getText().equals("const"))) {
                lhsType = c.getText();
                advance();
                c = current();
                if (c != null && c.getCode() == 2) {
                    lhsName = c.getText();
                    advance();
                }
            } else if (c != null && c.getCode() == 2) {
                lhsName = c.getText();
                advance();
            }

            // ПРАВИЛО 1: Уникальность имени переменной (левая часть)
            if (lhsName != null) {
                if (symbolTable.containsKey(lhsName)) {
                    addError("Семантическая ошибка: идентификатор '" + lhsName + "' уже объявлен ранее");
                } else {
                    symbolTable.put(lhsName, lhsType != null ? lhsType : "var");
                }
            }
            match("=", "->", "(");
        }

        AstNode lambda = parseLambda();

        while (!isEOF() && !current().getText().equals(";")) {
            addError("Ожидался конец выражения, найдены лишние символы");
            advance();
        }
        match(";", "EOF");

        return new AstNode.ProgramNode(lhsType, lhsName, lambda);
    }

    private AstNode parseLambda() {
        AstNode.LambdaNode lambdaNode = new AstNode.LambdaNode();
        parseParams(lambdaNode.params, "->");
        match("->", "{", "return");
        lambdaNode.body = parseBody("");
        return lambdaNode;
    }

    private void parseParams(List<AstNode.ParamNode> paramsList, String... follow) {
        Token c = current();
        if (c == null) return;

        if (c.getText().equals("(")) {
            advance();
            if (current() != null && !current().getText().equals(")")) {
                parseParamList(paramsList, ")");
            }
            match(")", "->");
        } else if (c.getCode() == 2) {
            String paramName = c.getText();
            checkAndAddParam(paramsList, null, paramName);
            advance();
        } else {
            addError("Ожидались параметры лямбда-выражения");
            recover(follow);
        }
    }

    private void parseParamList(List<AstNode.ParamNode> paramsList, String... follow) {
        parseParam(paramsList, ",", ")");
        while (!isEOF() && current() != null && current().getText().equals(",")) {
            advance();
            parseParam(paramsList, ",", ")");
        }
    }

    private void parseParam(List<AstNode.ParamNode> paramsList, String... follow) {
        Token c = current();
        if (c == null) return;

        String paramType = null;
        String paramName = null;

        if (c.getCode() == 14) {
            paramType = c.getText();
            advance();
            if (current() != null && current().getCode() == 2) {
                paramName = current().getText();
                advance();
            } else {
                addError("Ожидался идентификатор параметра");
            }
        } else if (c.getCode() == 2) {
            paramName = c.getText();
            advance();
        } else {
            addError("Ожидался параметр");
            recover(follow);
            return;
        }

        checkAndAddParam(paramsList, paramType, paramName);
    }

    private void checkAndAddParam(List<AstNode.ParamNode> paramsList, String type, String name) {
        if (name != null) {
            if (symbolTable.containsKey(name)) {
                addError("Семантическая ошибка: параметр '" + name + "' уже объявлен");
            } else {
                symbolTable.put(name, type != null ? type : "implicit");
                paramsList.add(new AstNode.ParamNode(type, name));
            }
        }
    }

    private AstNode parseBody(String... follow) {
        Token c = current();
        if (c == null) return null;

        if (c.getText().equals("{")) {
            advance();
            AstNode expr = null;
            while (!isEOF() && !current().getText().equals("}")) {
                expr = parseStmt("}", ";", "return");
            }
            match("}", follow);
            return expr; // Возвращаем последнее выражение
        } else {
            return parseExpr(";", "EOF");
        }
    }

    private AstNode parseStmt(String... follow) {
        Token c = current();
        if (c == null) return null;
        AstNode expr = null;

        if (c.getText().equals("return")) {
            advance();
            expr = parseExpr(";");
            match(";", "}", "return");
        } else {
            expr = parseExpr(";");
            match(";", "}", "return");
        }
        return expr;
    }

    private AstNode parseExpr(String... follow) {
        AstNode left = parseTerm("+", "-", "*", "/");
        return parseExprTail(left, follow);
    }

    private AstNode parseExprTail(AstNode left, String... follow) {
        while (!isEOF()) {
            Token c = current();
            if (c != null && ("+".equals(c.getText()) || "-".equals(c.getText()) || "*".equals(c.getText()) || "/".equals(c.getText()))) {
                String op = c.getText();
                advance();
                AstNode right = parseTerm("+", "-", "*", "/", ";", ")");

                if (left == null || right == null) {
                    addError("Семантическая ошибка: неверные операнды для оператора " + op);
                }

                left = new AstNode.BinaryOpNode(op, left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AstNode parseTerm(String... follow) {
        Token c = current();
        if (c == null) {
            addError("Ожидался операнд");
            return null;
        }

        if (c.getCode() == 2) {
            String varName = c.getText();
            advance();
            if (!symbolTable.containsKey(varName)) {
                addError("Семантическая ошибка: переменная '" + varName + "' не объявлена");
            }
            return new AstNode.VariableNode(varName);

        } else if (c.getCode() == 1 || c.getCode() == 6) {
            String numVal = c.getText();
            advance();
            try {
                if (c.getCode() == 1) Integer.parseInt(numVal);
                else Double.parseDouble(numVal);
            } catch (NumberFormatException e) {
                addError("Семантическая ошибка: число " + numVal + " выходит за допустимые пределы");
            }
            return new AstNode.NumberNode(numVal);

        } else if (c.getText().equals("(")) {
            advance();
            AstNode expr = parseExpr(")");
            match(")", follow);
            return expr;
        } else {
            addError("Ожидался операнд");
            recover(follow);
            return null;
        }
    }
}