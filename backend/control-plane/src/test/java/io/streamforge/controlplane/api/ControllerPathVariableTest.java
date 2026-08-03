package io.streamforge.controlplane.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.bind.annotation.PathVariable;

class ControllerPathVariableTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("controllerMethodsWithPathVariables")
  void declaresPathVariableNamesExplicitly(Method method) {
    for (Parameter parameter : method.getParameters()) {
      PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
      if (pathVariable != null) {
        assertThat(pathVariable.value()).isNotBlank();
      }
    }
  }

  private static Stream<Method> controllerMethodsWithPathVariables() {
    return Stream.of(PipelineController.class, SchemaController.class)
        .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
        .filter(ControllerPathVariableTest::hasPathVariable);
  }

  private static boolean hasPathVariable(Method method) {
    return Stream.of(method.getParameters())
        .anyMatch(parameter -> parameter.isAnnotationPresent(PathVariable.class));
  }
}
