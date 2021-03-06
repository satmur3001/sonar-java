/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks.helpers;

import com.google.common.collect.Lists;
import com.sonar.sslr.api.typed.ActionParser;
import org.junit.Test;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.java.resolve.SemanticModel;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.IfStatementTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.StatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class ReassignmentFinderTest {

  private final ActionParser<Tree> p = JavaParser.createParser(StandardCharsets.UTF_8);

  @Test
  public void parameter() throws Exception {
    String code = newCode(
      "int foo(int a) {",
      "  return a;",
      "}");

    MethodTree method = methodTree(code);
    List<StatementTree> statements = method.block().body();
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, null);
  }

  @Test
  public void parameter_with_usage() throws Exception {
    String code = newCode(
      "int foo(boolean test) {",
      "  if (test) {}",
      "  return test;",
      "}");

    MethodTree method = methodTree(code);
    List<StatementTree> statements = method.block().body();
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, null);
  }

  @Test
  public void declaration() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int a = 0;",
      "  return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    ExpressionTree aDeclarationInitializer = initializerFromVariableDeclarationStatement(statements.get(0));
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, aDeclarationInitializer);
  }

  @Test
  public void unknown_variable() throws Exception {
    String code = newCode(
      "int foo() {",
      "  return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, null);
  }

  @Test
  public void array_declaration() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int a[] = new int[42];",
      "  a[0] = 42;",
      "  return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    ExpressionTree arrayAssignmentExpression = initializerFromVariableDeclarationStatement(statements.get(0));
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, arrayAssignmentExpression);
  }

  @Test
  public void assignement() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int a;",
      "  a = 0;",
      "  return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    ExpressionTree aAssignmentExpression = assignementExpressionFromStatement(statements.get(1));
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, aAssignmentExpression);
  }

  @Test
  public void assignement_with_other_variable() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int a;",
      "  int b;",
      "  a = 0;",
      "  b = 0;",
      "  return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    ExpressionTree aAssignmentExpression = assignementExpressionFromStatement(statements.get(2));
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, aAssignmentExpression);
  }

  @Test
  public void last_assignement() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int a;",
      "  a = 0;",
      "  a = 1;",
      "  return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    ExpressionTree secondAssignment = assignementExpressionFromStatement(statements.get(2));
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, secondAssignment);
  }

  @Test
  public void last_assignement_on_same_line() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int a;",
      "  a = 0;",
      "  a = 1; return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    ExpressionTree secondAssignment = assignementExpressionFromStatement(statements.get(2));
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, secondAssignment);
  }

  @Test
  public void outside_method() throws Exception {
    String code = newCode(
      "int b;",
      "int foo() {",
      "  return b;",
      "}");

    ClassTree classTree = classTree(code);
    List<StatementTree> statements = ((MethodTree) classTree.members().get(1)).block().body();
    ExpressionTree variableDeclaration = ((VariableTree) (classTree.members().get(0))).initializer();
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, variableDeclaration);
  }

  @Test
  public void ignore_assignation_after_starting_point() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int b = 0;",
      "  doSomething(b);",
      "  b = 1;",
      "}");

    List<StatementTree> statements = methodBody(code);
    Tree expectedVariableDeclaration = initializerFromVariableDeclarationStatement(statements.get(0));
    MethodInvocationTree startingPoint = (MethodInvocationTree) ((ExpressionStatementTree) statements.get(1)).expression();
    Symbol searchedVariable = ((IdentifierTree) startingPoint.arguments().get(0)).symbol();
    assertThatLastReassignmentsOfVariableIsEqualTo(searchedVariable, startingPoint, expectedVariableDeclaration);
  }

  @Test
  public void ignore_assignation_after_starting_point_same_line() throws Exception {
    String code = newCode(
      "int foo() {",
      "  int b = 0;",
      "  doSomething(b); b = 1;",
      "}");

    List<StatementTree> statements = methodBody(code);
    Tree expectedVariableDeclaration = initializerFromVariableDeclarationStatement(statements.get(0));
    MethodInvocationTree startingPoint = (MethodInvocationTree) ((ExpressionStatementTree) statements.get(1)).expression();
    Symbol searchedVariable = ((IdentifierTree) startingPoint.arguments().get(0)).symbol();
    assertThatLastReassignmentsOfVariableIsEqualTo(searchedVariable, startingPoint, expectedVariableDeclaration);
  }

  private static void assertThatLastReassignmentsOfVariableIsEqualTo(Symbol searchedVariable, Tree startingPoint, Tree expectedVariableDeclaration) {
    assertThat(ReassignmentFinder.getClosestReassignmentOrDeclarationExpression(startingPoint, searchedVariable)).isEqualTo(expectedVariableDeclaration);
  }

  @Test
  public void known_limitation() throws Exception {
    String code = newCode(
      "int foo(boolean test) {",
      "  int a;",
      "  if (test) {",
      "    a = 0;",
      "  } else {",
      "    a = 1;", // Should have returned both thenAssignment and elseAssignment. CFG?
      "  }",
      "  return a;",
      "}");

    List<StatementTree> statements = methodBody(code);
    StatementTree elseAssignment = ((BlockTree) ((IfStatementTree) statements.get(1)).elseStatement()).body().get(0);
    ExpressionTree expression = assignementExpressionFromStatement(elseAssignment);
    assertThatLastReassignmentsOfReturnedVariableIsEqualTo(statements, expression);
  }

  private static void assertThatLastReassignmentsOfReturnedVariableIsEqualTo(List<StatementTree> statements, ExpressionTree target) {
    assertThat(getLastReassignment(statements)).isEqualTo(target);
  }

  private static Tree getLastReassignment(List<StatementTree> statements) {
    return ReassignmentFinder.getClosestReassignmentOrDeclarationExpression(statements.get(statements.size() - 1), variableFromLastReturnStatement(statements).symbol());
  }

  private static IdentifierTree variableFromLastReturnStatement(List<StatementTree> statements) {
    return (IdentifierTree) ((ReturnStatementTree) statements.get(statements.size() - 1)).expression();
  }

  private static ExpressionTree assignementExpressionFromStatement(StatementTree statement) {
    return ((AssignmentExpressionTree) ((ExpressionStatementTree) statement).expression()).expression();
  }

  private static ExpressionTree initializerFromVariableDeclarationStatement(Tree statement) {
    return ((VariableTree) statement).initializer();
  }

  private ClassTree classTree(String classBody) {
    CompilationUnitTree compilationUnitTree = (CompilationUnitTree) p.parse(classBody);
    SemanticModel.createFor(compilationUnitTree, Lists.<File>newArrayList());
    return (ClassTree) compilationUnitTree.types().get(0);
  }

  private MethodTree methodTree(String classBody) {
    ClassTree firstType = classTree(classBody);
    return (MethodTree) firstType.members().get(0);
  }

  private List<StatementTree> methodBody(String code) {
    return methodTree(code).block().body();
  }

  private static String newCode(String... lines) {
    String lineSeparator = System.lineSeparator();
    StringBuilder sb = new StringBuilder("class A {").append(lineSeparator);
    for (String string : lines) {
      sb.append(string).append(lineSeparator);
    }
    return sb.append("}").append(lineSeparator).toString();
  }

}
