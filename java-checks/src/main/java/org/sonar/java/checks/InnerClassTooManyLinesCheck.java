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
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.checks.helpers.ExpressionsHelper;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.List;

@Rule(key = "S2972")
public class InnerClassTooManyLinesCheck extends IssuableSubscriptionVisitor {

  private static final int DEFAULT_MAX = 25;

  @RuleProperty(key = "Max",
    description = "The maximum number of lines allowed",
    defaultValue = "" + DEFAULT_MAX)
  public int max = DEFAULT_MAX;

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Kind.CLASS, Kind.ENUM, Kind.INTERFACE, Kind.ANNOTATION_TYPE);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTree node = (ClassTree) tree;
    Symbol.TypeSymbol symbol = node.symbol();
    Symbol owner = symbol.owner();
    Type ownerType = owner.type();
    if (ownerType != null && ownerType.isClass() && owner.owner().isPackageSymbol()) {
      // raise only one issue for the first level of nesting when multiple nesting
      int first = node.firstToken().line();
      int last = node.lastToken().line();
      int length = last - first + 1;
      if (length > max) {
        reportIssue(ExpressionsHelper.reportOnClassTree(node), "Reduce this class from " + length + " to the maximum allowed " + max + " or externalize it in a public class.");
      }
    }
  }

}
