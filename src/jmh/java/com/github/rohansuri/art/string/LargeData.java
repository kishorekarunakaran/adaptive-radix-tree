package com.github.rohansuri.art.string;

import com.github.rohansuri.art.AdaptiveRadixTree;
import com.github.rohansuri.art.BinaryComparables;

import com.github.rohansuri.art.FixedStringBinaryComparable;
import com.github.rohansuri.art.StringBinaryComparable1;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;
import org.apache.commons.collections4.trie.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class LargeData {

	@State(Scope.Benchmark)
	public static class Data {
		Object holder;
		String[] keys;
		Supplier<Map<String, Object>> supplier;

		public enum MapType {
			HASH_MAP,
			ART1,
			ART2,
			FIXEDART,
			TREE_MAP,
			PATRICIA_TRIE
		}

		@Param
		MapType mapType;

		public enum File {
			WORDS("/words.txt", 235886),
			UUIDs("/uuid.txt", 100000),
			TERMINATED_WORDS("/terminated_words.txt", 235886);

			private final String fileName;
			private final int size;

			File(String fileName, int size) {
				this.fileName = fileName;
				this.size = size;
			}
		}

		@Param
		File file;

		@Setup
		public void setup() throws IOException, URISyntaxException {
			switch (mapType) {
			case HASH_MAP:
				supplier = () -> new HashMap<>();
				break;
			case ART1:
				supplier = () -> new AdaptiveRadixTree<>(BinaryComparables.forUTF8());
				break;
			case ART2:
				supplier = () -> new AdaptiveRadixTree<>(new StringBinaryComparable1());
				break;
			case FIXEDART:
				supplier = () -> new AdaptiveRadixTree<>(new FixedStringBinaryComparable());
				break;
			case TREE_MAP:
				supplier = () -> new TreeMap<>();
				break;
			case PATRICIA_TRIE:
				supplier = () -> new PatriciaTrie<Object>();
				break;
			default:
				throw new AssertionError();
			}

			holder = new Object();
			List<String> s = IOUtils
					.readLines(this.getClass().getResourceAsStream(file.fileName), StandardCharsets.UTF_8);
			keys = s.toArray(String[]::new);
			if (keys.length != file.size) {
				throw new AssertionError("expected " + file.size + " words from the file, got " + keys.length);
			}
		}
	}

	public static class LookupData extends Data {
		@Override
		public void setup() throws IOException, URISyntaxException {
			super.setup();
			Map<String, Object> m = supplier.get();
			for (int i = 0; i < keys.length; i++) {
				m.put(keys[i], holder);
			}
			supplier = () -> m;
		}

	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	public int lookup(Blackhole bh, LookupData d) {
		Map<String, Object> m = d.supplier.get();
		for (int i = 0; i < d.keys.length; i++) {
			bh.consume(m.get(d.keys[i]));
		}
		return m.size();
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	public int insert(Blackhole bh, Data d) {
		Map<String, Object> m = d.supplier.get();
		for (int i = 0; i < d.keys.length; i++) {
			bh.consume(m.put(d.keys[i], d.holder));
		}
		return m.size();
	}
}