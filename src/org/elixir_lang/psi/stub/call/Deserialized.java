package org.elixir_lang.psi.stub.call;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class Deserialized {
    private static final byte[] BEGIN;
    private static final byte[] END;
    /* Increase from `0` to enable guards to check for mismatches between reads and writes, such as in
       https://github.com/KronicDeth/intellij-elixir/issues/767 */
    private static final int GUARD_LENGTH = 0;
    private static final Logger LOGGER = Logger.getInstance(Deserialized.class);

    static {
        int i;

        BEGIN = new byte[GUARD_LENGTH];

        for (i = 0; i < BEGIN.length; i++) {
            BEGIN[i] = (byte) i;
        }

        END = new byte[BEGIN.length];

        for (i = 0; i < END.length; i++) {
            END[i] = (byte) (END.length - i);
        }
    }

    @NotNull
    public final Set<StringRef> canonicalNameSet;
    public final boolean hasDoBlockOrKeyword;
    @NotNull
    public final StringRef name;
    public final int resolvedFinalArity;
    @Nullable
    public final StringRef resolvedFunctionName;
    @Nullable
    public final StringRef resolvedModuleName;

    public Deserialized(@Nullable StringRef resolvedModuleName,
                        @Nullable StringRef resolvedFunctionName,
                        int resolvedFinalArity,
                        boolean hasDoBlockOrKeyword,
                        @NotNull StringRef name,
                        @NotNull Set<StringRef> canonicalNameSet) {
        this.resolvedModuleName = resolvedModuleName;
        this.resolvedFunctionName = resolvedFunctionName;
        this.resolvedFinalArity = resolvedFinalArity;
        this.hasDoBlockOrKeyword = hasDoBlockOrKeyword;
        this.name = name;
        this.canonicalNameSet = canonicalNameSet;
    }

    public <T extends Stubbic> Deserialized(@NotNull T stubbic) {
        this(
                StringRef.fromNullableString(stubbic.resolvedModuleName()),
                StringRef.fromNullableString(stubbic.resolvedFunctionName()),
                stubbic.resolvedFinalArity(),
                stubbic.hasDoBlockOrKeyword(),
                StringRef.fromNullableString(stubbic.getName()),
                stringRefSet(stubbic.canonicalNameSet())
        );
    }

    public static void assertGuard(@NotNull StubInputStream stubInputStream, byte[] expectedGuard) {
        try {
            byte[] actualGuard = new byte[expectedGuard.length];
            //noinspection ResultOfMethodCallIgnored
            stubInputStream.read(actualGuard);

            assert Arrays.equals(actualGuard, expectedGuard) :
                    "Expected `" + hexString(expectedGuard) + "` tag in StubInputSteam, but got `" +
                            hexString(actualGuard) + "`";
        } catch (IOException e) {
            LOGGER.error(e);
        }
    }

    public static Deserialized deserialize(@NotNull StubInputStream stubInputStream) throws IOException {
        assertGuard(stubInputStream, BEGIN);

        StringRef resolvedModuleName = deserializeResolvedModuleName(stubInputStream);
        StringRef resolvedFunctionName = deserializeResolvedFunctionName(stubInputStream);
        int resolvedFinalArity = deserializeResolvedFinalArity(stubInputStream);
        boolean hasDoBlockOrKeyword = deserializeHasDoBlockOrKeyword(stubInputStream);
        StringRef name = deserializeName(stubInputStream);
        Set<StringRef> canonicalNameSet = deserializeCanonicalNameSet(stubInputStream);

        assertGuard(stubInputStream, END);

        return new Deserialized(
                resolvedModuleName,
                resolvedFunctionName,
                resolvedFinalArity,
                hasDoBlockOrKeyword,
                name,
                canonicalNameSet
        );
    }

    private static Set<StringRef> deserializeCanonicalNameSet(@NotNull StubInputStream stubInputStream)
            throws IOException {
        return readGuarded(stubInputStream, Deserialized::readNameSet);
    }

    private static boolean deserializeHasDoBlockOrKeyword(@NotNull StubInputStream stubInputStream) throws IOException {
        return readGuarded(stubInputStream, StubInputStream::readBoolean);
    }

    private static StringRef deserializeName(@NotNull StubInputStream stubInputStream) throws IOException {
        return readGuardedName(stubInputStream);
    }

    private static int deserializeResolvedFinalArity(@NotNull StubInputStream stubInputStream) throws IOException {
        return readGuarded(stubInputStream, StubInputStream::readVarInt);
    }

    private static StringRef deserializeResolvedFunctionName(@NotNull StubInputStream stubInputStream)
            throws IOException {
        return readGuardedName(stubInputStream);
    }

    private static StringRef deserializeResolvedModuleName(@NotNull StubInputStream stubInputStream)
            throws IOException {
        return readGuardedName(stubInputStream);
    }

    @NotNull
    private static String hexString(byte[] bytes) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();
    }

    public static <T> T readGuarded(@NotNull StubInputStream stubInputStream,
                                    @NotNull Reader<T> reader) throws IOException {
        assertGuard(stubInputStream, BEGIN);
        T read = reader.read(stubInputStream);
        assertGuard(stubInputStream, END);

        return read;
    }

    private static StringRef readGuardedName(@NotNull StubInputStream stubInputStream) throws IOException {
        return readGuarded(stubInputStream, StubInputStream::readName);
    }

    private static Set<StringRef> readNameSet(@NotNull StubInputStream dataStream) throws IOException {
        assertGuard(dataStream, BEGIN);

        assertGuard(dataStream, BEGIN);
        int nameSetSize = dataStream.readVarInt();
        assertGuard(dataStream, END);

        if (nameSetSize > 3) {
            byte[] readAhead = new byte[BEGIN.length];
            int bytesRead = dataStream.read(readAhead);
            LOGGER.error("readNameSet read " + bytesRead + " of " + readAhead.length + " unexpected bytes `" + hexString(readAhead) + "`");
        }
        Set<StringRef> nameSet = new THashSet<>(nameSetSize);

        for (int i = 0; i < nameSetSize; i++) {
            StringRef name = dataStream.readName();
            nameSet.add(name);
        }

        assertGuard(dataStream, END);

        return nameSet;
    }

    public static <T extends Stubbic> void serialize(@NotNull StubOutputStream stubOutputStream, @NotNull T stubbic)
            throws IOException {
        new Deserialized(stubbic).serialize(stubOutputStream);
    }

    @NotNull
    private static Set<StringRef> stringRefSet(@NotNull Set<String> stringSet) {
        Set<StringRef> stringRefSet = new THashSet<>();

        for (String string : stringSet) {
            stringRefSet.add(StringRef.fromNullableString(string));
        }

        return stringRefSet;
    }

    private static void writeGuard(@NotNull StubOutputStream stubOutputStream, byte[] guard) throws IOException {
        stubOutputStream.write(guard);
    }

    public static void writeGuarded(@NotNull StubOutputStream stubOutputStream, @NotNull Writer writer)
            throws IOException {
        writeGuard(stubOutputStream, BEGIN);
        writer.write(stubOutputStream);
        writeGuard(stubOutputStream, END);
    }

    private static void writeGuardedName(@NotNull StubOutputStream stubOutputStream, @Nullable StringRef name)
            throws IOException {
        writeGuarded(
                stubOutputStream,
                guardedStubOutputStream -> guardedStubOutputStream.writeName(StringRef.toString(name))
        );
    }

    private void serialize(@NotNull StubOutputStream stubOutputStream) throws IOException {
        writeGuarded(
                stubOutputStream,
                guardedStubOutputStream -> {
                    serializeResolvedModuleName(guardedStubOutputStream);
                    serializeResolvedFunctionName(guardedStubOutputStream);
                    serializeResolvedFinalArity(guardedStubOutputStream);
                    serializeHasDoBlockOrKeyword(guardedStubOutputStream);
                    serializeName(guardedStubOutputStream);
                    serializeCanonicalNameSet(guardedStubOutputStream);
                }
        );
    }

    private void serializeCanonicalNameSet(@NotNull StubOutputStream stubOutputStream) throws IOException {
        writeGuarded(
                stubOutputStream,
                guardedOutputStream -> writeNameSet(guardedOutputStream, canonicalNameSet)
        );
    }

    private void serializeHasDoBlockOrKeyword(@NotNull StubOutputStream stubOutputStream) throws IOException {
        writeGuarded(
                stubOutputStream,
                guardedStubOutputStream -> guardedStubOutputStream.writeBoolean(hasDoBlockOrKeyword)
        );
    }

    private void serializeName(@NotNull StubOutputStream stubOutputStream) throws IOException {
        writeGuardedName(stubOutputStream, name);
    }

    private void serializeResolvedFinalArity(@NotNull StubOutputStream stubOutputStream) throws IOException {
        writeGuarded(
                stubOutputStream,
                guardedStubOutputStream -> guardedStubOutputStream.writeVarInt(resolvedFinalArity)
        );
    }

    private void serializeResolvedFunctionName(@NotNull StubOutputStream stubOutputStream) throws IOException {
        writeGuardedName(stubOutputStream, resolvedFunctionName);
    }

    private void serializeResolvedModuleName(@NotNull StubOutputStream stubOutputStream) throws IOException {
        writeGuardedName(stubOutputStream, resolvedModuleName);
    }

    private void writeNameSet(@NotNull StubOutputStream stubOutputStream, @NotNull Set<StringRef> nameSet) throws IOException {
        writeGuarded(
                stubOutputStream,
                guardedStubOutputStream -> {
                    writeGuarded(
                            guardedStubOutputStream,
                            sizeStubOutputStream -> sizeStubOutputStream.writeVarInt(nameSet.size())
                    );

                    for (StringRef name : nameSet) {
                        writeGuardedName(guardedStubOutputStream, name);
                    }
                }
        );
    }
}
