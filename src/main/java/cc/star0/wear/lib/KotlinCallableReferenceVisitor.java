package cc.star0.wear.lib;

import static cc.star0.wear.lib.WearHapticsClassVisitor.COMPAT_CONSTANTS;
import static cc.star0.wear.lib.WearHapticsClassVisitor.GOOGLE_CONSTANTS;
import static cc.star0.wear.lib.WearHapticsClassVisitor.SDK_GETTERS;

import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Kotlin callable references store an owner Class in addition to invoking the target method.
 * Redirecting just the invoke would still load the missing Google class in their constructor.
 * Only these small generated classes are buffered; ordinary class literals keep their meaning.
 */
final class KotlinCallableReferenceVisitor extends ClassNode {
    private static final Set<String> SUPERCLASSES = Set.of(
            "kotlin/jvm/internal/FunctionReference",
            "kotlin/jvm/internal/FunctionReferenceImpl",
            "kotlin/jvm/internal/AdaptedFunctionReference");

    private final ClassVisitor next;

    KotlinCallableReferenceVisitor(ClassVisitor next) {
        super(Opcodes.ASM9);
        this.next = next;
    }

    static boolean isCallableReference(String superclass) {
        return superclass != null && SUPERCLASSES.contains(superclass);
    }

    @Override
    public void visitEnd() {
        boolean adapted = false;
        boolean stillUsesGoogle = false;
        for (MethodNode method : methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && SDK_GETTERS.contains(call.name)) {
                    adapted |= COMPAT_CONSTANTS.equals(call.owner);
                    stillUsesGoogle |= GOOGLE_CONSTANTS.equals(call.owner);
                }
            }
        }
        // A reference to an unselected effect must retain its original owner as well as its call.
        if (adapted && !stillUsesGoogle) {
            for (MethodNode method : methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode constant
                            && constant.cst instanceof Type type
                            && type.getSort() == Type.OBJECT
                            && GOOGLE_CONSTANTS.equals(type.getInternalName())) {
                        constant.cst = Type.getObjectType(COMPAT_CONSTANTS);
                    }
                }
            }
        }
        accept(next);
    }
}
