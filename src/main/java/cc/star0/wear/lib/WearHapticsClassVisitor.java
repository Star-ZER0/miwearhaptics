package cc.star0.wear.lib;

import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Changes only the owner of matching calls; descriptors, boxing, frames and control flow stay valid. */
final class WearHapticsClassVisitor extends ClassVisitor {
    static final String GOOGLE_CONSTANTS = "com/google/wear/input/WearHapticFeedbackConstants";
    static final String COMPAT_CONSTANTS = "cc/star0/wear/lib/miwearhaptics/WearHapticFeedbackConstantsCompat";
    static final Set<String> SDK_GETTERS =
            Set.of("getScrollItemFocus", "getScrollTick", "getScrollLimit");

    private final Set<String> methods;
    private final boolean failOnUnsupportedCalls;
    private String className;

    WearHapticsClassVisitor(ClassVisitor next, Set<String> methods, boolean failOnUnsupportedCalls) {
        super(Opcodes.ASM9, next);
        this.methods = Set.copyOf(methods);
        this.failOnUnsupportedCalls = failOnUnsupportedCalls;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        className = name;
        if (KotlinCallableReferenceVisitor.isCallableReference(superName)) {
            cv = new KotlinCallableReferenceVisitor(cv);
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String method,
                    String desc, boolean isInterface) {
                String target = redirect(owner, method, desc, opcode == Opcodes.INVOKESTATIC && !isInterface);
                super.visitMethodInsn(opcode, target, method, desc, isInterface);
            }

            @Override
            public void visitInvokeDynamicInsn(String method, String desc,
                    Handle bootstrapMethod, Object... arguments) {
                Object[] rewritten = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    rewritten[i] = rewriteConstant(arguments[i]);
                }
                super.visitInvokeDynamicInsn(method, desc, rewriteHandle(bootstrapMethod), rewritten);
            }

            @Override
            public void visitLdcInsn(Object value) {
                super.visitLdcInsn(rewriteConstant(value));
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                if (GOOGLE_CONSTANTS.equals(owner)) {
                    unsupported(field, desc);
                }
                super.visitFieldInsn(opcode, owner, field, desc);
            }
        };
    }

    private String redirect(String owner, String method, String descriptor, boolean staticMethod) {
        if (!GOOGLE_CONSTANTS.equals(owner)) {
            return owner;
        }
        if (!staticMethod || !"()I".equals(descriptor) || !SDK_GETTERS.contains(method)) {
            unsupported(method, descriptor);
            return owner;
        }
        return methods.contains(method) ? COMPAT_CONSTANTS : owner;
    }

    private Handle rewriteHandle(Handle handle) {
        String owner = redirect(handle.getOwner(), handle.getName(), handle.getDesc(),
                handle.getTag() == Opcodes.H_INVOKESTATIC && !handle.isInterface());
        return owner.equals(handle.getOwner()) ? handle : new Handle(
                handle.getTag(), owner, handle.getName(), handle.getDesc(), handle.isInterface());
    }

    private Object rewriteConstant(Object value) {
        if (value instanceof Handle handle) {
            return rewriteHandle(handle);
        }
        if (value instanceof ConstantDynamic constant) {
            Object[] arguments = new Object[constant.getBootstrapMethodArgumentCount()];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = rewriteConstant(constant.getBootstrapMethodArgument(i));
            }
            return new ConstantDynamic(constant.getName(), constant.getDescriptor(),
                    rewriteHandle(constant.getBootstrapMethod()), arguments);
        }
        return value;
    }

    private void unsupported(String name, String descriptor) {
        if (failOnUnsupportedCalls) {
            throw new IllegalStateException("Unsupported WearHapticFeedbackConstants member "
                    + name + descriptor + " in " + className + ". Update the compatibility adapter, "
                    + "exclude this caller, or set wearHaptics.failOnUnsupportedCalls=false "
                    + "to explicitly keep the original Google reference.");
        }
    }
}
