package dev.wrenchbridge.benchmark;

import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import dev.wrenchbridge.WrenchTagKeys;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TagLookupBenchmark {
    @Param({"ordinary", "mixed", "positive", "wrong_registry"}) public String workload;
    private TagKey<?>[] input;
    private TagKey<?>[] keys;
    private ResourceLocation[] names;
    private record Group(String path, String[] namespaces) {}
    private Group[] groups;

    @Setup public void setup() {
        keys = WrenchTagKeys.ITEMS.toArray(TagKey<?>[]::new);
        names = Arrays.stream(keys).map(TagKey::location).toArray(ResourceLocation[]::new);
        var grouped = new TreeMap<String, Set<String>>();
        for (var name : names) grouped.computeIfAbsent(name.getPath(), ignored -> new TreeSet<>()).add(name.getNamespace());
        groups = grouped.entrySet().stream().map(e -> new Group(e.getKey(), e.getValue().toArray(String[]::new))).toArray(Group[]::new);
        input = new TagKey<?>[256];
        String[] namespaces = {"c", "c", "minecraft", "mekanism", "forge", "other"};
        String[] paths = {"ingots", "dusts/iron", "logs", "ores/copper", "alloys/basic", "tools/pickaxe", "plates/steel", "tools/wrench", "wrenches"};
        for (int i = 0; i < input.length; i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespaces[i % namespaces.length], paths[(i * 7) % paths.length]);
            if (id.getPath().equals("wrenches") || id.getPath().equals("tools/wrench")) id = ResourceLocation.fromNamespaceAndPath("other", id.getPath());
            input[i] = TagKey.create(Registries.ITEM, id);
            if (workload.equals("positive") || workload.equals("mixed") && i % 64 == 0) input[i] = keys[i % keys.length];
            if (workload.equals("wrong_registry")) input[i] = TagKey.create(Registries.BLOCK, id);
        }
        // Check value equality for keys bypassing the weak interner before timing.
        for (var key : keys) {
            @SuppressWarnings({"deprecation", "rawtypes", "unchecked"}) TagKey<?> equal = new TagKey(key.registry(), ResourceLocation.parse(key.location().toString()));
            if (equal == key || !equal.equals(key) || !locations(equal) || !pathGroups(equal)) throw new AssertionError("non-interned value semantics");
        }
        for (var tag : input) if (hashed(tag) != locations(tag) || hashed(tag) != pathGroups(tag) || hashed(tag) != identityFallback(tag)) throw new AssertionError("candidate semantics");
    }

    private boolean hashed(TagKey<?> tag) { return tag.isFor(Registries.ITEM) && WrenchTagKeys.ITEMS.contains(tag); }
    private boolean locations(TagKey<?> tag) {
        if (!tag.isFor(Registries.ITEM)) return false;
        var location = tag.location();
        for (var name : names) if (name.equals(location)) return true;
        return false;
    }
    private boolean pathGroups(TagKey<?> tag) {
        if (!tag.isFor(Registries.ITEM)) return false;
        var location = tag.location();
        for (var group : groups) if (group.path.equals(location.getPath())) {
            for (var namespace : group.namespaces) if (namespace.equals(location.getNamespace())) return true;
            return false;
        }
        return false;
    }
    private boolean identityFallback(TagKey<?> tag) {
        if (!tag.isFor(Registries.ITEM)) return false;
        for (var key : keys) if (key == tag) return true;
        return pathGroups(tag);
    }
    @Benchmark @OperationsPerInvocation(256) public int baseline() { int hits = 0; for (var tag : input) if (hashed(tag)) hits++; return hits; }
    @Benchmark @OperationsPerInvocation(256) public int equalLocations() { int hits = 0; for (var tag : input) if (locations(tag)) hits++; return hits; }
    @Benchmark @OperationsPerInvocation(256) public int groupedNames() { int hits = 0; for (var tag : input) if (pathGroups(tag)) hits++; return hits; }
    @Benchmark @OperationsPerInvocation(256) public int identityAndFallback() { int hits = 0; for (var tag : input) if (identityFallback(tag)) hits++; return hits; }
    @Benchmark @OperationsPerInvocation(256) public int production() { int hits = 0; for (var tag : input) if (WrenchTagKeys.contains(tag)) hits++; return hits; }
}
