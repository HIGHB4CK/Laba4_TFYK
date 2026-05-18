package org.example.Lab2;

import java.util.ArrayList;
import java.util.List;

public abstract class AstNode {
    public abstract String print(String prefix, boolean isTail);


    public static class ProgramNode extends AstNode {
        public String lhsType;
        public String lhsName;
        public AstNode lambda;

        public ProgramNode(String lhsType, String lhsName, AstNode lambda) {
            this.lhsType = lhsType;
            this.lhsName = lhsName;
            this.lambda = lambda;
        }

        @Override
        public String print(String prefix, boolean isTail) {
            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append(isTail ? "└── " : "├── ").append("ProgramNode\n");
            String newPrefix = prefix + (isTail ? "    " : "│   ");
            if (lhsName != null) {
                sb.append(newPrefix).append("├── LHS Type: ").append(lhsType != null ? lhsType : "var").append("\n");
                sb.append(newPrefix).append("├── LHS Name: ").append(lhsName).append("\n");
            }
            if (lambda != null) {
                sb.append(lambda.print(newPrefix, true));
            }
            return sb.toString();
        }
    }


    public static class LambdaNode extends AstNode {
        public List<ParamNode> params = new ArrayList<>();
        public AstNode body;

        @Override
        public String print(String prefix, boolean isTail) {
            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append(isTail ? "└── " : "├── ").append("LambdaNode\n");
            String newPrefix = prefix + (isTail ? "    " : "│   ");
            for (ParamNode param : params) {
                sb.append(param.print(newPrefix, false));
            }
            if (body != null) {
                sb.append(body.print(newPrefix, true));
            }
            return sb.toString();
        }
    }


    public static class ParamNode extends AstNode {
        public String type;
        public String name;

        public ParamNode(String type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String print(String prefix, boolean isTail) {
            return prefix + (isTail ? "└── " : "├── ") + "ParamNode (Type: " + (type != null ? type : "implicit") + ", Name: " + name + ")\n";
        }
    }


    public static class BinaryOpNode extends AstNode {
        public String op;
        public AstNode left;
        public AstNode right;

        public BinaryOpNode(String op, AstNode left, AstNode right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        public String print(String prefix, boolean isTail) {
            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append(isTail ? "└── " : "├── ").append("BinaryOpNode (").append(op).append(")\n");
            String newPrefix = prefix + (isTail ? "    " : "│   ");
            if (left != null) sb.append(left.print(newPrefix, false));
            if (right != null) sb.append(right.print(newPrefix, true));
            return sb.toString();
        }
    }


    public static class VariableNode extends AstNode {
        public String name;

        public VariableNode(String name) { this.name = name; }

        @Override
        public String print(String prefix, boolean isTail) {
            return prefix + (isTail ? "└── " : "├── ") + "VariableNode: " + name + "\n";
        }
    }


    public static class NumberNode extends AstNode {
        public String value;

        public NumberNode(String value) { this.value = value; }

        @Override
        public String print(String prefix, boolean isTail) {
            return prefix + (isTail ? "└── " : "├── ") + "NumberNode: " + value + "\n";
        }
    }
}