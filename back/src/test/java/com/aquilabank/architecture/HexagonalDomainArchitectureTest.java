package com.aquilabank.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

class HexagonalDomainArchitectureTest {

  private static final List<String> FORBIDDEN_INTERNAL_PREFIXES =
      List.of(
          "org/springframework/",
          "jakarta/persistence/",
          "jakarta/servlet/",
          "jakarta/validation/",
          "javax/persistence/",
          "org/hibernate/",
          "org/apache/kafka/",
          "io/lettuce/",
          "io/micrometer/",
          "com/fasterxml/jackson/",
          "com/zaxxer/hikari/");

  @Test
  void domainHasNoFrameworkOrExternalSdkDependenciesAndNoPackageInfoFiles() throws IOException {
    Path mainJava = Path.of("src/main/java");
    Path domainSourceRoot = mainJava.resolve("com/aquilabank/domain");
    assertThat(Files.isDirectory(domainSourceRoot)).isTrue();

    List<String> packageInfoFiles = new ArrayList<>();
    try (var stream = Files.walk(mainJava)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> "package-info.java".equals(path.getFileName().toString()))
          .forEach(path -> packageInfoFiles.add(mainJava.relativize(path).toString()));
    }
    assertThat(packageInfoFiles).isEmpty();

    List<String> sourceViolations = new ArrayList<>();
    try (var stream = Files.walk(domainSourceRoot)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  for (String line : Files.readAllLines(path)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("import ")) {
                      for (String forbiddenPrefix : FORBIDDEN_INTERNAL_PREFIXES) {
                        String pkgPrefix = forbiddenPrefix.replace('/', '.');
                        if (trimmed.contains(pkgPrefix)) {
                          sourceViolations.add(path + ": " + trimmed);
                        }
                      }
                    }
                  }
                } catch (IOException ex) {
                  throw new RuntimeException(ex);
                }
              });
    }
    assertThat(sourceViolations).isEmpty();

    Path domainClassesRoot = Path.of("build/classes/java/main/com/aquilabank/domain");
    assertThat(Files.isDirectory(domainClassesRoot)).isTrue();
    List<String> bytecodeViolations = new ArrayList<>();
    try (var stream = Files.walk(domainClassesRoot)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".class"))
          .forEach(
              path -> {
                try (InputStream in = Files.newInputStream(path)) {
                  ClassReader reader = new ClassReader(in);
                  String ownerClass = reader.getClassName();
                  reader.accept(
                      new DomainDependencyCheckingVisitor(ownerClass, bytecodeViolations),
                      ClassReader.SKIP_DEBUG);
                } catch (IOException ex) {
                  throw new RuntimeException(ex);
                }
              });
    }
    assertThat(bytecodeViolations).isEmpty();
  }

  private static void checkInternalName(
      String ownerClass, String internalName, List<String> violations) {
    if (internalName == null) {
      return;
    }
    for (String forbiddenPrefix : FORBIDDEN_INTERNAL_PREFIXES) {
      if (internalName.startsWith(forbiddenPrefix)) {
        violations.add(ownerClass + " -> " + internalName);
      }
    }
  }

  private static void checkDescriptor(
      String ownerClass, String descriptor, List<String> violations) {
    if (descriptor == null) {
      return;
    }
    for (String forbiddenPrefix : FORBIDDEN_INTERNAL_PREFIXES) {
      if (descriptor.contains("L" + forbiddenPrefix)) {
        violations.add(ownerClass + " -> " + descriptor);
      }
    }
  }

  private static final class DomainDependencyCheckingVisitor extends ClassVisitor {

    private final String ownerClass;
    private final List<String> violations;

    private DomainDependencyCheckingVisitor(String ownerClass, List<String> violations) {
      super(Opcodes.ASM9);
      this.ownerClass = ownerClass;
      this.violations = violations;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      checkInternalName(ownerClass, superName, violations);
      if (interfaces != null) {
        for (String iface : interfaces) {
          checkInternalName(ownerClass, iface, violations);
        }
      }
      checkDescriptor(ownerClass, signature, violations);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      checkDescriptor(ownerClass, descriptor, violations);
      return null;
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      checkDescriptor(ownerClass, descriptor, violations);
      checkDescriptor(ownerClass, signature, violations);
      return null;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      checkDescriptor(ownerClass, descriptor, violations);
      checkDescriptor(ownerClass, signature, violations);
      if (exceptions != null) {
        for (String exception : exceptions) {
          checkInternalName(ownerClass, exception, violations);
        }
      }
      return new MethodVisitor(Opcodes.ASM9) {
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          checkDescriptor(ownerClass, desc, violations);
          return null;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
          checkInternalName(ownerClass, type, violations);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String fieldName, String desc) {
          checkInternalName(ownerClass, owner, violations);
          checkDescriptor(ownerClass, desc, violations);
        }

        @Override
        public void visitMethodInsn(
            int opcode, String owner, String methodName, String desc, boolean isInterface) {
          checkInternalName(ownerClass, owner, violations);
          checkDescriptor(ownerClass, desc, violations);
        }

        @Override
        public void visitLdcInsn(Object value) {
          if (value instanceof Type type) {
            checkDescriptor(ownerClass, type.getDescriptor(), violations);
          }
        }
      };
    }
  }
}
