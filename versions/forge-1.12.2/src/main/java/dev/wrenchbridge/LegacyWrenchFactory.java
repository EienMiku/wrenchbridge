package dev.wrenchbridge;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Implements the small legacy wrench interfaces only when their owning mod actually supplies them.
 */
public final class LegacyWrenchFactory {
  private LegacyWrenchFactory() {}

  public static UniversalWrenchItem create() {
    List<Class<?>> apis = new ArrayList<Class<?>>();
    for (String name :
        new String[] {
          "cofh.api.item.IToolHammer",
          "buildcraft.api.tools.IToolWrench",
          "mekanism.api.IMekWrench",
          "appeng.api.implementations.items.IAEWrench"
        }) {
      try {
        Class<?> api = Class.forName(name, false, UniversalWrenchItem.class.getClassLoader());
        boolean compatible = api.isInterface();
        for (Method method : api.getMethods()) {
          if (Modifier.isAbstract(method.getModifiers())
              && method.getReturnType() != boolean.class
              && method.getReturnType() != void.class) compatible = false;
        }
        if (compatible) apis.add(api);
      } catch (ClassNotFoundException | LinkageError ignored) {
      }
    }
    if (apis.isEmpty()) return new UniversalWrenchItem();
    try {
      String name = "dev/wrenchbridge/generated/DetectedWrench";
      String[] interfaces = new String[apis.size()];
      for (int i = 0; i < interfaces.length; i++) interfaces[i] = Type.getInternalName(apis.get(i));
      ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      writer.visit(
          Opcodes.V1_8,
          Opcodes.ACC_PUBLIC,
          name,
          null,
          Type.getInternalName(UniversalWrenchItem.class),
          interfaces);
      MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
      init.visitCode();
      init.visitVarInsn(Opcodes.ALOAD, 0);
      init.visitMethodInsn(
          Opcodes.INVOKESPECIAL,
          Type.getInternalName(UniversalWrenchItem.class),
          "<init>",
          "()V",
          false);
      init.visitInsn(Opcodes.RETURN);
      init.visitMaxs(0, 0);
      init.visitEnd();
      Set<String> emitted = new HashSet<String>();
      for (Class<?> api : apis)
        for (Method method : api.getMethods()) {
          if (!Modifier.isAbstract(method.getModifiers())) continue;
          String descriptor = Type.getMethodDescriptor(method);
          if (!emitted.add(method.getName() + descriptor)) continue;
          MethodVisitor mv =
              writer.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), descriptor, null, null);
          mv.visitCode();
          if (method.getReturnType() == boolean.class) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/wrenchbridge/WrenchContext",
                "forcedItem",
                "(Ljava/lang/Object;)Z",
                false);
            mv.visitInsn(Opcodes.IRETURN);
          } else mv.visitInsn(Opcodes.RETURN);
          mv.visitMaxs(0, 0);
          mv.visitEnd();
        }
      writer.visitEnd();
      final byte[] bytes = writer.toByteArray();
      Class<?> generated =
          new ClassLoader(UniversalWrenchItem.class.getClassLoader()) {
            Class<?> define() {
              return defineClass(
                  "dev.wrenchbridge.generated.DetectedWrench", bytes, 0, bytes.length);
            }
          }.define();
      WrenchBridge.LOG.info("Detected {} legacy wrench interfaces", apis.size());
      return (UniversalWrenchItem) generated.newInstance();
    } catch (ReflectiveOperationException | LinkageError error) {
      WrenchBridge.LOG.warn(
          "Legacy interface probe failed; retaining ore-dictionary support", error);
      return new UniversalWrenchItem();
    }
  }
}
