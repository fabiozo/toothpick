package toothpick.compiler.memberinjector;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import javax.lang.model.element.Modifier;
import toothpick.Injector;
import toothpick.MemberInjector;
import toothpick.compiler.CodeGenerator;
import toothpick.compiler.targets.FieldInjectionTarget;

/**
 * Generates a {@link MemberInjector} for a given collection of {@link FieldInjectionTarget}.
 * Typically a {@link MemberInjector} is created for a class a soon as it contains
 * an {@link javax.inject.Inject} annotated field.
 * TODO also deal with injected methods.
 */
public class MemberInjectorGenerator implements CodeGenerator {

  private static final String MEMBER_INJECTOR_SUFFIX = "$$MemberInjector";

  private List<FieldInjectionTarget> fieldInjectionTargetList;

  public MemberInjectorGenerator(List<FieldInjectionTarget> fieldInjectionTargetList) {
    this.fieldInjectionTargetList = fieldInjectionTargetList;
    if (fieldInjectionTargetList.isEmpty()) {
      throw new IllegalArgumentException("At least one memberInjectorInjectionTarget is needed.");
    }
  }

  public String brewJava() {
    // Interface to implement
    FieldInjectionTarget fieldInjectionTarget = fieldInjectionTargetList.get(0);
    ClassName className = ClassName.get(fieldInjectionTarget.targetClass);
    ParameterizedTypeName memberInjectorInterfaceParameterizedTypeName = ParameterizedTypeName.get(ClassName.get(MemberInjector.class), className);

    // Build class
    TypeSpec.Builder injectorMemberTypeSpec = TypeSpec.classBuilder(className.simpleName() + MEMBER_INJECTOR_SUFFIX)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addSuperinterface(memberInjectorInterfaceParameterizedTypeName);
    emitSuperMemberInjectorFieldIfNeeded(injectorMemberTypeSpec, fieldInjectionTarget);
    emitInjectMethod(injectorMemberTypeSpec, fieldInjectionTargetList);

    JavaFile javaFile = JavaFile.builder(className.packageName(), injectorMemberTypeSpec.build()).build();
    return javaFile.toString();
  }

  private void emitSuperMemberInjectorFieldIfNeeded(TypeSpec.Builder injectorMemberTypeSpec, FieldInjectionTarget fieldInjectionTarget) {
    if (fieldInjectionTarget.superClassThatNeedsInjection != null) {
      ClassName superTypeThatNeedsInjection = ClassName.get(fieldInjectionTarget.superClassThatNeedsInjection);
      ParameterizedTypeName memberInjectorSuperParameterizedTypeName =
          ParameterizedTypeName.get(ClassName.get(MemberInjector.class), superTypeThatNeedsInjection);
      FieldSpec.Builder superMemberInjectorField =
          FieldSpec.builder(memberInjectorSuperParameterizedTypeName, "superMemberInjector", Modifier.PRIVATE)
              //TODO use proper typing here
              .initializer("toothpick.registries.memberinjector.MemberInjectorRegistryLocator.getMemberInjector($L.class)",
                  superTypeThatNeedsInjection.simpleName());
      injectorMemberTypeSpec.addField(superMemberInjectorField.build());
    }
  }

  private void emitInjectMethod(TypeSpec.Builder injectorMemberTypeSpec, List<FieldInjectionTarget> fieldInjectionTargetList) {
    FieldInjectionTarget fieldInjectionTarget = fieldInjectionTargetList.get(0);
    MethodSpec.Builder injectBuilder = MethodSpec.methodBuilder("inject")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ClassName.get(fieldInjectionTarget.targetClass), "target")
        .addParameter(ClassName.get(Injector.class), "injector");

    for (FieldInjectionTarget injectorInjectionTarget : fieldInjectionTargetList) {
      final String injectorGetMethodName;
      final ClassName className;
      switch (injectorInjectionTarget.kind) {
        case INSTANCE:
          injectorGetMethodName = " = injector.getInstance(";
          className = ClassName.get(injectorInjectionTarget.memberClass);
          break;
        case PROVIDER:
          injectorGetMethodName = " = injector.getProvider(";
          className = ClassName.get(injectorInjectionTarget.kindParamClass);
          break;
        case LAZY:
          injectorGetMethodName = " = injector.getLazy(";
          className = ClassName.get(injectorInjectionTarget.kindParamClass);
          break;
        case FUTURE:
          injectorGetMethodName = " = injector.getFuture(";
          className = ClassName.get(injectorInjectionTarget.kindParamClass);
          break;
        default:
          throw new IllegalStateException("The kind can't be null.");
      }
      StringBuilder assignFieldStatement;
      assignFieldStatement = new StringBuilder("target.");
      assignFieldStatement.append(injectorInjectionTarget.memberName).append(injectorGetMethodName).append(className).append(".class)");
      injectBuilder.addStatement(assignFieldStatement.toString());
    }

    if (fieldInjectionTarget.superClassThatNeedsInjection != null) {
      injectBuilder.addStatement("superMemberInjector.inject(target, injector)");
    }

    injectorMemberTypeSpec.addMethod(injectBuilder.build());
  }

  @Override public String getFqcn() {
    FieldInjectionTarget firstMemberInjector = fieldInjectionTargetList.get(0);
    return firstMemberInjector.targetClass.getQualifiedName().toString() + MEMBER_INJECTOR_SUFFIX;
  }
}
