/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.j2objc.types;

import com.google.common.collect.Sets;
import com.google.devtools.j2objc.Options;
import com.google.devtools.j2objc.util.ErrorReportingASTVisitor;
import com.google.devtools.j2objc.util.NameTable;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;

import java.util.Collection;
import java.util.Set;

/**
 * Collects the set of imports and set of forward references needed to resolve
 * type references. Subclasses collect specific imports and super types needed
 * for header and implementation source files.
 *
 * @author Tom Ball
 */
public class ImportCollector extends ErrorReportingASTVisitor {

  protected final Set<Import> imports = Sets.newLinkedHashSet();
  protected final Set<Import> superTypes = Sets.newLinkedHashSet();

  /**
   * Collects references and super types for a specified type declaration.
   *
   * @param typeDecl the type declaration to be scanned
   */
  public void collect(ASTNode node) {
    run(node);
    for (Import imp : superTypes) {
      if (imports.contains(imp)) {
        imports.remove(imp);
      }
    }
  }

  /**
   * Returns the collected set of imports.
   */
  public Set<Import> getImports() {
    return imports;
  }

  /**
   * Returns the collected set of super types.
   */
  public Set<Import> getSuperTypes() {
    return superTypes;
  }

  protected void addImports(Type type) {
    addImports(type, imports);
  }

  protected void addImports(ITypeBinding type) {
    addImports(type, imports);
  }

  protected void addSuperType(Type type) {
    addImports(type, superTypes);
  }

  private void addImports(Type type, Collection<Import> references) {
    if (type == null || type instanceof PrimitiveType) {
      return;
    }
    ITypeBinding binding = Types.getTypeBinding(type);
    if (binding == null) {
      binding = Types.resolveIOSType(type);
    }
    if (binding == null) {
      return; // parser already reported missing class
    }
    if (Types.isIOSType(type)
        && !(binding instanceof IOSArrayTypeBinding)) { // Include array definitions.
      return;
    }
    if (binding.isPrimitive()) {
      return;
    }
    addImports(binding, references);
  }

  protected void addImports(ITypeBinding binding, Collection<Import> imports) {
    if (!binding.isTypeVariable() && !binding.isPrimitive() && !binding.isAnnotation()
        // Don't import IOS types, other than the IOS array types,
        // since they have header files.
        && (binding instanceof IOSArrayTypeBinding
            || !(binding instanceof IOSTypeBinding))) {
      binding = Types.mapType(binding).getErasure();
      String typeName = NameTable.getFullName(binding);
      boolean isInterface = binding.isInterface();
      while (!binding.isTopLevel()) {
        binding = binding.getDeclaringClass();
      }
      if (!Types.isIOSType(typeName)) {
        imports.add(new Import(typeName, binding.getErasure().getQualifiedName(), isInterface));
      }
    } else if (binding.isTypeVariable()) {
      for (ITypeBinding bound : binding.getTypeBounds()) {
        addImports(bound, imports);
      }
    }
  }

  protected void addImport(String typeName, String javaFileName, boolean isInterface) {
    imports.add(new Import(typeName, javaFileName, isInterface));
  }

  protected void addSuperType(String typeName, String javaFileName, boolean isInterface) {
    superTypes.add(new Import(typeName, javaFileName, isInterface));
  }

  /**
   * Description of an imported type. Imports are equal if their fully qualified
   * type names are equal.
   */
  public static class Import implements Comparable<Import> {
    private final String typeName;
    private final String javaFileName;
    private final boolean isInterface;

    public Import(String typeName, String javaFileName, boolean isInterface) {
      this.typeName = typeName;
      this.javaFileName = javaFileName;
      this.isInterface = isInterface;
    }

    public String getTypeName() {
      return typeName;
    }

    public String getImportFileName() {
      // Always use JRE and JUnit package directories, since the j2objc
      // distribution is (currently) built with package directories.
      if (Options.usePackageDirectories() || javaFileName.startsWith("java") ||
          javaFileName.startsWith("junit")) {
        return javaFileName.replace('.', '/');
      }
      return javaFileName.substring(javaFileName.lastIndexOf('.') + 1);
    }

    public boolean isInterface() {
      return isInterface;
    }

    @Override
    public int compareTo(Import other) {
      return typeName.compareTo(other.typeName);
    }

    @Override
    public int hashCode() {
      return typeName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Import other = (Import) obj;
      return typeName.equals(other.typeName);
    }

    @Override
    public String toString() {
      return typeName;
    }
  }
}
