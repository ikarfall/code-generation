package processors;

import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;

import com.google.auto.service.AutoService;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("annotations.BuilderProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class BuilderProcessor  extends AbstractProcessor {


  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    Predicate<Element> trueSetter = annotatedMethod -> ((ExecutableType) annotatedMethod.asType()).getParameterTypes().size() == 1;
    Function<Element, String> methodName = setter -> setter.getSimpleName().toString();
    Function<Element, String> firstArgType = setter -> ((ExecutableType) setter.asType()).getParameterTypes().get(0).toString();

    annotations.stream().map(a -> (TypeElement)a).findFirst().ifPresent(a -> {

      Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(a);
      Map<Boolean, List<Element>> annotatedMethods = elementsAnnotatedWith.stream().collect(partitioningBy(trueSetter));

      annotatedMethods.get(false).forEach(invalidAnnotation -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
          "@BuilderProperty must be applied to a setXxx method with a single argument", invalidAnnotation));

      String classToGenerateBuilderFor = ((TypeElement) annotatedMethods.get(true).get(0)
          .getEnclosingElement()).getQualifiedName().toString();

      Map<String, String> methodToArgTypeMap = annotatedMethods.get(true).stream()
          .collect(toMap(methodName, firstArgType));
      try {
          writeBuilderFile(classToGenerateBuilderFor, methodToArgTypeMap);
      } catch (IOException e){
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Can not write generated java file");
      }
    });
    return true;
  }

  private void writeBuilderFile(
      String className, Map<String, String> setterMap)
      throws IOException {

    String packageName = null;
    int lastDot = className.lastIndexOf('.');
    if (lastDot > 0) {
      packageName = className.substring(0, lastDot);
    }

    String simpleClassName = className.substring(lastDot + 1);
    String builderClassName = className + "Builder";
    String builderSimpleClassName = builderClassName
        .substring(lastDot + 1);

    JavaFileObject builderFile = processingEnv.getFiler()
        .createSourceFile(builderClassName);

    try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {

      if (packageName != null) {
        out.print("package ");
        out.print(packageName);
        out.println(";");
        out.println();
      }

      out.print("public class ");
      out.print(builderSimpleClassName);
      out.println(" {");
      out.println();

      out.print("    private ");
      out.print(simpleClassName);
      out.print(" object = new ");
      out.print(simpleClassName);
      out.println("();");
      out.println();

      out.print("    public ");
      out.print(simpleClassName);
      out.println(" build() {");
      out.println("        return object;");
      out.println("    }");
      out.println();

      setterMap.forEach((methodName, argumentType) -> {

        out.print("    public ");
        out.print(builderSimpleClassName);
        out.print(" ");
        out.print(methodName);

        out.print("(");

        out.print(argumentType);
        out.println(" value) {");
        out.print("        object.");
        out.print(methodName);
        out.println("(value);");
        out.println("        return this;");
        out.println("    }");
        out.println();
      });

      out.println("}");
    }
  }

}
