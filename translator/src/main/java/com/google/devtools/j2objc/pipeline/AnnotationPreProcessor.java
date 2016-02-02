/*
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

package com.google.devtools.j2objc.pipeline;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.devtools.j2objc.Options;
import com.google.devtools.j2objc.file.RegularInputFile;
import com.google.devtools.j2objc.util.ErrorUtil;
import com.google.devtools.j2objc.util.FileUtil;
import com.google.devtools.j2objc.util.PathClassLoader;

import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import javax.annotation.processing.Processor;

/**
 * Preprocesses all input with annotation processors, if any,
 * and adds any .java files generated by those annotation processors
 * to the given GenerationBatch.
 */
public class AnnotationPreProcessor {

  private File tmpDirectory;

  public File getTemporaryDirectory() {
    return tmpDirectory;
  }

  /**
   * Process the given input files, given in the same format as J2ObjC command line args.
   */
  public void process(Iterable<String> fileArgs) {
    assert tmpDirectory == null;  // Shouldn't run an instance more than once.

    if (!hasAnnotationProcessors()) {
      return;
    }

    try {
      tmpDirectory = FileUtil.createTempDir("annotations");
    } catch (IOException e) {
      ErrorUtil.error("failed creating temporary directory: " + e);
      return;
    }
    String tmpDirPath = tmpDirectory.getAbsolutePath();
    List<String> compileArgs = Lists.newArrayList();
    Joiner pathJoiner = Joiner.on(":");
    List<String> sourcePath = Options.getSourcePathEntries();
    sourcePath.add(tmpDirPath);
    compileArgs.add("-sourcepath");
    compileArgs.add(pathJoiner.join(sourcePath));
    compileArgs.add("-classpath");
    List<String> classPath = Options.getClassPathEntries();
    compileArgs.add(pathJoiner.join(classPath));
    compileArgs.add("-encoding");
    compileArgs.add(Options.getCharset().name());
    compileArgs.add("-source");
    compileArgs.add(Options.getSourceVersion());
    compileArgs.add("-s");
    compileArgs.add(tmpDirPath);
    compileArgs.add("-d");
    compileArgs.add(tmpDirPath);
    List<String> processorPath = Options.getProcessorPathEntries();
    if (!processorPath.isEmpty()) {
      compileArgs.add("-processorpath");
      compileArgs.add(pathJoiner.join(processorPath));
    }
    String processorClasses = Options.getProcessors();
    if (processorClasses != null) {
      compileArgs.add("-processor");
      compileArgs.add(processorClasses);
    }
    if (Options.isVerbose()) {
      compileArgs.add("-XprintProcessorInfo");
      compileArgs.add("-XprintRounds");
    }
    for (String fileArg : fileArgs) {
      compileArgs.add(fileArg);
    }
    Map<String, String> batchOptions = Maps.newHashMap();
    batchOptions.put(CompilerOptions.OPTION_Process_Annotations, CompilerOptions.ENABLED);
    batchOptions.put(CompilerOptions.OPTION_GenerateClassFiles, CompilerOptions.DISABLED);
    // Fully qualified name used since "Main" isn't unique.
    org.eclipse.jdt.internal.compiler.batch.Main batchCompiler =
        new org.eclipse.jdt.internal.compiler.batch.Main(
            new PrintWriter(System.out), new PrintWriter(System.err), false, batchOptions, null);
    if (!batchCompiler.compile(compileArgs.toArray(new String[0]))) {
      // Any compilation errors will already by displayed.
      ErrorUtil.error("failed batch processing sources");
    }
  }

  /**
   * Check whether any javax.annotation.processing.Processor services are defined on
   * the declared classpath. This is checked here to avoid batch compiling sources
   * in case any might have annotations that should be processed.
   */
  private boolean hasAnnotationProcessors() {
    PathClassLoader loader = new PathClassLoader(Options.getClassPathEntries());
    loader.addPaths(Options.getProcessorPathEntries());
    ServiceLoader<Processor> serviceLoader = ServiceLoader.load(Processor.class, loader);
    Iterator<Processor> iterator = serviceLoader.iterator();
    return iterator.hasNext();
  }

  public void collectInputs(Collection<ProcessingContext> inputs) {
    if (tmpDirectory != null) {
      collectGeneratedInputs(tmpDirectory, "", inputs);
    }
  }

  private void collectGeneratedInputs(
      File dir, String currentRelativePath, Collection<ProcessingContext> inputs) {
    assert dir.exists() && dir.isDirectory();
    for (File f : dir.listFiles()) {
      String relativeName = currentRelativePath + File.separatorChar + f.getName();
      if (f.isDirectory()) {
        collectGeneratedInputs(f, relativeName, inputs);
      } else {
        if (f.getName().endsWith(".java")) {
          inputs.add(ProcessingContext.fromFile(new RegularInputFile(f.getPath(), relativeName)));
        }
      }
    }
  }
}
